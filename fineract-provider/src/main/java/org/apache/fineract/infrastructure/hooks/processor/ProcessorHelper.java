/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.hooks.processor;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.Dns;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Service
public final class ProcessorHelper {

    // Nota bene: Similar code to insecure HTTPS is also in Fineract Client's
    // org.apache.fineract.client.util.FineractClient.Builder.insecure()

    private static final Logger LOG = LoggerFactory.getLogger(ProcessorHelper.class);

    @SuppressWarnings("unused")
    private static final X509TrustManager insecureX509TrustManager = new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}// NOSONAR

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}// NOSONAR

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {};
        }
    };

    /**
     * Configure HTTP client to be "insecure", as in skipping host SSL certificate verification. While this can be
     * useful during development e.g. when using self-signed certificates, it should never be enabled in production (due
     * to "man in the middle").
     */
    private final boolean insecureHttpClient = Boolean.getBoolean("fineract.insecureHttpClient");

    /**
     * Allows outbound hook requests to target internal/private/loopback/link-local addresses. This is disabled by
     * default to prevent Server-Side Request Forgery (SSRF); it should only be enabled in trusted development or test
     * environments where hooks legitimately point at local services.
     */
    private final boolean allowInternalHookTargets = Boolean.getBoolean("fineract.hooks.allowInternalTargets");

    private final SSLContext insecureSSLContext;

    public ProcessorHelper() throws KeyManagementException, NoSuchAlgorithmException {
        if (insecureHttpClient) {
            insecureSSLContext = createInsecureSSLContext();
        } else {
            insecureSSLContext = null;
        }
    }

    private OkHttpClient createClient() {
        var okBuilder = new OkHttpClient.Builder();
        if (!allowInternalHookTargets) {
            okBuilder.dns(new SsrfProtectedDns());
        }
        if (insecureHttpClient) {
            configureInsecureClient(okBuilder);
        }
        return okBuilder.build();
    }

    /**
     * DNS resolver that rejects any resolution to an internal/reserved address (loopback, link-local, site-local,
     * unique-local, wildcard or multicast). It is applied to every connection the OkHttp client makes, so it also
     * re-validates on each request and thereby defends against DNS-rebinding attacks. Blocking the resolved addresses
     * (rather than only inspecting the URL string) neutralises the SSRF sink for literal IPs, DNS names that map to
     * internal hosts, and the cloud metadata endpoint (169.254.169.254).
     */
    private static final class SsrfProtectedDns implements Dns {

        @Override
        public List<InetAddress> lookup(final String hostname) throws UnknownHostException {
            final List<InetAddress> addresses = Dns.SYSTEM.lookup(hostname);
            final List<InetAddress> allowed = new ArrayList<>(addresses.size());
            for (final InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    LOG.warn("Blocked outbound hook request to internal/reserved address {} (host {})", address.getHostAddress(), hostname);
                    throw new UnknownHostException("Refusing to connect to internal/reserved address for host " + hostname);
                }
                allowed.add(address);
            }
            return allowed;
        }
    }

    /**
     * Enforce an http(s) scheme allowlist on the configured hook URL before it is used to build a live HTTP client.
     * Rejecting other schemes (file, ftp, gopher, jar, ...) closes SSRF/local-file vectors that would otherwise be
     * reachable through the underlying client stack.
     */
    private static void validateUrlScheme(final String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Hook URL must not be empty");
        }
        final String scheme;
        try {
            scheme = new URI(url).getScheme();
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Hook URL is not a valid URI", e);
        }
        if (scheme == null) {
            throw new IllegalArgumentException("Hook URL must be absolute with an http or https scheme");
        }
        final String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            throw new IllegalArgumentException("Unsupported hook URL scheme: " + scheme);
        }
    }

    private static boolean isBlockedAddress(final InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        final byte[] bytes = address.getAddress();
        // IPv6 unique local addresses (fc00::/7)
        if (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc) {
            return true;
        }
        // IPv4 carrier-grade NAT (100.64.0.0/10)
        if (bytes.length == 4 && (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 0x40) {
            return true;
        }
        return false;
    }

    private void configureInsecureClient(final OkHttpClient.Builder okBuilder) {
        okBuilder.sslSocketFactory(insecureSSLContext.getSocketFactory(), insecureX509TrustManager);
        HostnameVerifier insecureHostnameVerifier = (hostname, session) -> true;// NOSONAR
        okBuilder.hostnameVerifier(insecureHostnameVerifier);
    }

    private SSLContext createInsecureSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext insecureSSLContext = SSLContext.getInstance("TLS"); // TODO "TLS" or "SSL" as in
        // FineractClient.Builder?
        insecureSSLContext.init(null, new TrustManager[] { insecureX509TrustManager }, new SecureRandom());
        return insecureSSLContext;
    }

    @SuppressWarnings("rawtypes")
    public Callback createCallback(final String url) {
        return new Callback() {

            @Override
            public void onResponse(@SuppressWarnings("unused") Call call, retrofit2.Response response) {
                LOG.debug("URL: {} - Status: {}", url, response.code());
            }

            @Override
            public void onFailure(@SuppressWarnings("unused") Call call, Throwable t) {
                LOG.error("URL: {} - Retrofit failure occurred", url, t);
            }
        };
    }

    public WebHookService createWebHookService(final String url) {
        validateUrlScheme(url);
        final OkHttpClient client = createClient();
        final Retrofit.Builder retrofitBuilder = new Retrofit.Builder();
        retrofitBuilder.baseUrl(url);
        retrofitBuilder.client(client);
        retrofitBuilder.addConverterFactory(GsonConverterFactory.create());
        final Retrofit retrofit = retrofitBuilder.build();
        return retrofit.create(WebHookService.class);
    }

    @SuppressWarnings("rawtypes")
    public Callback createCallback(final String url, String payload) {

        return new Callback() {

            @Override
            public void onResponse(@SuppressWarnings("unused") Call call, retrofit2.Response response) {
                LOG.debug("URL: {} - Status: {}", url, response.code());
            }

            @Override
            public void onFailure(@SuppressWarnings("unused") Call call, Throwable t) {
                LOG.error("URL: {} - Retrofit failure occured", url, t);
            }
        };
    }
}
