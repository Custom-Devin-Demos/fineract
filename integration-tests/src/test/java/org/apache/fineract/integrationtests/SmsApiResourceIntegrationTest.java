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

import static org.apache.fineract.client.feign.util.FeignCalls.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.services.SmsApi;
import org.apache.fineract.client.models.CommandProcessingResult;
import org.apache.fineract.client.models.PageSmsData;
import org.apache.fineract.client.models.SmsBusinessRulesData;
import org.apache.fineract.client.models.SmsCampaignData;
import org.apache.fineract.client.models.SmsCreationRequest;
import org.apache.fineract.client.models.SmsData;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

/**
 * Integration tests for the retrieveAllSmsByStatus endpoint in SmsApiResource. Ensures correct retrieval of SMS
 * messages by campaign and status.
 */
@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = { 9191 })
public class SmsApiResourceIntegrationTest {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String DATE_TIME_FORMAT = "dd MMMM yyyy HH:mm:ss";

    private final FineractFeignClient fineractClient = FineractFeignClientHelper.getFineractFeignClient();
    private final SmsApi smsApi = fineractClient.sms();
    private final SmsCampaignApi campaignApi = fineractClient.create(SmsCampaignApi.class);

    private final ClientAndServer client;

    public SmsApiResourceIntegrationTest(ClientAndServer client) {
        this.client = client;
        this.client.when(HttpRequest.request().withMethod("GET").withPath("/smsbridges"))
                .respond(HttpResponse.response().withContentType(MediaType.APPLICATION_JSON).withBody(
                        "[{\"id\":1,\"tenantId\":1,\"phoneNo\":\"+1234567890\",\"providerName\":\"Dummy SMS Provider - Testing\",\"providerDescription\":\"Dummy, just for testing\"}]"));
    }

    /**
     * Test retrieving SMS messages by status for a valid campaign.
     */
    @Test
    public void testRetrieveAllSmsByStatus_validStatus() {
        String reportName = "Prospective Clients";
        int triggerType = 1;
        Long campaignId = createCampaign(reportName, triggerType);
        verifyCampaignCreatedOnServer(campaignId);
        activateCampaign(campaignId);

        Long clientId = ClientHelper.addClientAsPerson("1", 1L, null).getClientId();

        CommandProcessingResult smsCreationResult = ok(() -> smsApi
                .createSms(new SmsCreationRequest().clientId(clientId).message("Integration test message").campaignId(campaignId)));
        assertNotNull(smsCreationResult.getResourceId());

        Long status = null;
        for (SmsData sms : ok(smsApi::retrieveAllSms)) {
            Long smsClientId = sms.getClientId();
            String smsCampaignName = sms.getCampaignName();
            if (smsClientId != null && smsCampaignName != null && smsClientId.equals(clientId)
                    && smsCampaignName.equals("Campaign_Name_" + Integer.toHexString(campaignId.intValue()).toUpperCase())) {
                if (sms.getStatus() != null) {
                    status = sms.getStatus().getId();
                    break;
                }
            }
        }
        if (status == null) {
            status = 100L;
        }
        int limit = 10;
        final Long queryStatus = status;
        PageSmsData messages = ok(
                () -> smsApi.retrieveAllSmsByStatus(campaignId, queryStatus, null, null, null, null, null, limit, null, null));
        assertNotNull(messages.getPageItems());
        assertTrue(messages.getPageItems().stream().anyMatch(sms -> clientId.equals(sms.getClientId())));
    }

    /**
     * Test retrieving SMS messages by status for an invalid status value.
     */
    @Test
    public void testRetrieveAllSmsByStatus_invalidStatus() {
        String reportName = "Prospective Clients";
        int triggerType = 1;
        Long campaignId = createCampaign(reportName, triggerType);
        verifyCampaignCreatedOnServer(campaignId);
        activateCampaign(campaignId);

        long invalidStatus = 9999L;
        int limit = 10;
        PageSmsData messages = ok(
                () -> smsApi.retrieveAllSmsByStatus(campaignId, invalidStatus, null, null, null, null, null, limit, null, null));
        assertNotNull(messages.getPageItems());
    }

    private Long createCampaign(String reportName, int triggerType) {
        Long reportId = selectedReportId(reportName);
        Map<String, Object> paramValue = new HashMap<>();
        paramValue.put("officeId", "1");
        paramValue.put("loanOfficerId", "1");
        paramValue.put("reportName", reportName);
        Map<String, Object> request = new HashMap<>();
        request.put("providerId", 1);
        request.put("triggerType", triggerType);
        request.put("campaignName", Utils.randomStringGenerator("Campaign_Name_", 5));
        request.put("campaignType", 1);
        request.put("message", "Hi, this is from integtration tests runner");
        request.put("locale", "en");
        request.put("dateFormat", DATE_FORMAT);
        request.put("dateTimeFormat", DATE_TIME_FORMAT);
        request.put("runReportId", reportId);
        request.put("paramValue", paramValue);
        return ok(() -> campaignApi.createCampaign(request)).getResourceId();
    }

    private void verifyCampaignCreatedOnServer(Long campaignId) {
        SmsCampaignData campaign = ok(() -> campaignApi.retrieveCampaign(campaignId));
        assertEquals(campaignId, campaign.getId(), "ERROR IN CREATING THE CAMPAIGN");
    }

    private void activateCampaign(Long campaignId) {
        String actionDate = Utils.getLocalDateOfTenant().format(DateTimeFormatter.ofPattern(DATE_FORMAT));
        Map<String, Object> request = new HashMap<>();
        request.put("activationDate", actionDate);
        request.put("locale", "en");
        request.put("dateFormat", DATE_FORMAT);
        ok(() -> campaignApi.activateCampaign(campaignId, request));
    }

    private Long selectedReportId(String reportName) {
        SmsCampaignData template = ok(campaignApi::retrieveTemplate);
        if (template.getBusinessRulesOptions() != null) {
            for (SmsBusinessRulesData report : template.getBusinessRulesOptions()) {
                if (reportName.equals(report.getReportName())) {
                    return report.getReportId();
                }
            }
        }
        throw new IllegalStateException("Report not found: " + reportName);
    }

    /**
     * SMS campaign endpoints whose generated request models are too thin to preserve the exact payloads this test
     * relies on, so their raw (but typed-response) representations are used directly through fineract-client-feign.
     */
    interface SmsCampaignApi {

        @RequestLine("POST /v1/smscampaigns")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        CommandProcessingResult createCampaign(Map<String, Object> request);

        @RequestLine("GET /v1/smscampaigns/{campaignId}")
        @Headers("Accept: application/json")
        SmsCampaignData retrieveCampaign(@Param("campaignId") Long campaignId);

        @RequestLine("GET /v1/smscampaigns/template")
        @Headers("Accept: application/json")
        SmsCampaignData retrieveTemplate();

        @RequestLine("POST /v1/smscampaigns/{campaignId}?command=activate")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        CommandProcessingResult activateCampaign(@Param("campaignId") Long campaignId, Map<String, Object> request);
    }
}
