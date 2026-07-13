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
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalServicesConfigurationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalServicesConfigurationTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // The typed SDK exposes the configuration as a single object and returns void on update, dropping the name/value
    // entries and the "changes" body the assertions rely on, so a minimal Feign interface handles the raw payloads.
    private final RawExternalServiceApi externalServiceApi = FineractFeignClientHelper.getFineractFeignClient()
            .create(RawExternalServiceApi.class);

    interface RawExternalServiceApi {

        @RequestLine("GET v1/externalservice/{servicename}")
        Response getConfiguration(@Param("servicename") String servicename);

        @RequestLine("PUT v1/externalservice/{servicename}")
        Response updateConfiguration(@Param("servicename") String servicename, JsonNode body);
    }

    @Test
    public void testExternalServicesConfiguration() {

        // Checking for S3
        String configName = "s3_access_key";
        JsonNode externalServicesConfig = getExternalServicesConfigurationByServiceName("S3");
        Assertions.assertNotNull(externalServicesConfig);
        for (JsonNode config : externalServicesConfig) {
            String name = config.get("name").asText();
            String value = null;
            if (name.equals(configName)) {
                value = config.hasNonNull("value") ? config.get("value").asText() : null;
                if (value == null) {
                    value = "testnull";
                }
                String newValue = "test";
                LOG.info("{} : {}", name, value);
                JsonNode arrayListValue = updateValueForExternaServicesConfiguration("S3", name, newValue);
                Assertions.assertNotNull(arrayListValue.get("value"));
                Assertions.assertEquals(arrayListValue.get("value").asText(), newValue);
                JsonNode arrayListValue1 = updateValueForExternaServicesConfiguration("S3", name, value);
                Assertions.assertNotNull(arrayListValue1.get("value"));
                Assertions.assertEquals(arrayListValue1.get("value").asText(), value);
            }

        }

        // Checking for SMTP:
        configName = "username";
        externalServicesConfig = getExternalServicesConfigurationByServiceName("SMTP");
        Assertions.assertNotNull(externalServicesConfig);

        for (JsonNode config : externalServicesConfig) {
            String name = config.get("name").asText();
            String value = null;
            if (name.equals(configName)) {
                value = config.hasNonNull("value") ? config.get("value").asText() : null;
                if (value == null) {
                    value = "testnull";
                }
                String newValue = "test";
                LOG.info("{} : {}", name, value);
                JsonNode arrayListValue = updateValueForExternaServicesConfiguration("SMTP", name, newValue);
                Assertions.assertNotNull(arrayListValue.get("value"));
                Assertions.assertEquals(arrayListValue.get("value").asText(), newValue);
                JsonNode arrayListValue1 = updateValueForExternaServicesConfiguration("SMTP", name, value);
                Assertions.assertNotNull(arrayListValue1.get("value"));
                Assertions.assertEquals(arrayListValue1.get("value").asText(), value);
            }

        }

        // Checking for Notifications:
        configName = "server_key";
        externalServicesConfig = getExternalServicesConfigurationByServiceName("NOTIFICATION");
        Assertions.assertNotNull(externalServicesConfig);

        for (JsonNode config : externalServicesConfig) {
            String name = config.get("name").asText();
            String value = null;
            if (name.equals(configName)) {
                value = config.hasNonNull("value") ? config.get("value").asText() : null;
                if (value == null) {
                    value = "testnull";
                }
                LOG.info("{} : {}", name, value);
                assertTrue(hasMoreThanThreeStars(value));
            }

        }
    }

    private JsonNode getExternalServicesConfigurationByServiceName(final String serviceName) {
        return readBody(externalServiceApi.getConfiguration(serviceName));
    }

    private JsonNode updateValueForExternaServicesConfiguration(final String serviceName, final String name, final String value) {
        final ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put(name, value);
        final JsonNode response = readBody(externalServiceApi.updateConfiguration(serviceName, requestBody));
        return response.get("changes");
    }

    private static JsonNode readBody(final Response response) {
        try (Response r = response) {
            return MAPPER.readTree(Util.toString(r.body().asReader(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasMoreThanThreeStars(String input) {
        return input != null && input.matches("(.*\\*.*){4,}");
    }
}
