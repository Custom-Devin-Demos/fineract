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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.apache.fineract.client.feign.services.CodeValuesApi;
import org.apache.fineract.client.feign.services.CodesApi;
import org.apache.fineract.client.models.GetCodeValuesDataResponse;
import org.apache.fineract.client.models.GetCodesResponse;
import org.apache.fineract.client.models.GetJobsResponse;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostCodeValuesDataRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Manthan Surkar
 *
 */
public class AuditIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom rand = new SecureRandom();

    private final CodesApi codesApi = FineractFeignClientHelper.getFineractFeignClient().codes();
    private final CodeValuesApi codeValuesApi = FineractFeignClientHelper.getFineractFeignClient().codeValues();

    private final AuditApi auditApi = FineractFeignClientHelper.getFineractFeignClient().create(AuditApi.class);

    interface AuditApi {

        @RequestLine("GET v1/audits?entityName={entityName}&resourceId={resourceId}&actionName={actionName}&orderBy=id&sortBy=DSC")
        Response getAuditDetails(@Param("entityName") String entityName, @Param("resourceId") Integer resourceId,
                @Param("actionName") String actionName);

        @RequestLine("GET v1/audits?paged=true&limit={limit}")
        Response getAuditsWithLimit(@Param("limit") int limit);

        @RequestLine("GET v1/audits/searchtemplate")
        Response getAuditSearchTemplate();

        @RequestLine("GET v1/audits?paged=true&orderBy={orderBy}")
        Response getAuditsOrderBy(@Param("orderBy") String orderBy);
    }

    @Test
    public void testAuditSearchTemplate() {
        // given
        // when
        final JsonNode auditSearchTemplate = readBody(auditApi.getAuditSearchTemplate());

        // then
        assertNotNull(auditSearchTemplate);
        assertEquals(4, auditSearchTemplate.size()); // appUsers, actionNames, entityNames, processingResults
        assertTrue(auditSearchTemplate.get("actionNames").size() > 0);

        // verify all command processing status enum values are present and use enum_value (not enum_message_property)
        final JsonNode statuses = auditSearchTemplate.get("statuses");
        assertNotNull(statuses);
        assertEquals(6, statuses.size());

        final List<String> statusValues = new ArrayList<>();
        statuses.forEach(r -> statusValues.add(r.get("processingResult").asText()));

        assertTrue(statusValues.contains("Invalid"));
        assertTrue(statusValues.contains("Processed"));
        assertTrue(statusValues.contains("Awaiting Approval"));
        assertTrue(statusValues.contains("Rejected"));
        assertTrue(statusValues.contains("Under Processing"));
        assertTrue(statusValues.contains("Error"));
    }

    /**
     * Here we Create/Update different Entities and verify an audit is generated for each action. This can be further
     * extened with more entities and actions in similiar way.
     */
    @Test
    public void auditShouldbeCreated() {
        // Audits recieved after all actions are performed.
        JsonNode auditsRecieved;

        // Audits recieved before any action is performed, needed in special
        // cases eg: reactivate client, close client
        JsonNode auditsRecievedInitial;

        // When Client is created: Count should be "1"
        final PostClientsRequest clientRequest = ClientHelper.defaultClientCreationRequest();
        final PostClientsResponse clientResponse = ClientHelper.createClient(clientRequest);
        final Integer clientId = clientResponse.getClientId().intValue();
        final String clientExternalId = clientRequest.getExternalId();
        ClientHelper.verifyClientCreatedOnServer(clientResponse.getClientId());

        auditsRecieved = getAuditDetails(clientId, "CREATE", "CLIENT");
        verifyOneAuditOnly(auditsRecieved, clientId, "CREATE", "CLIENT");

        // Performs multiple close and reactivate on client

        final Long closureReasonId = retrieveOrCreateClientClosureReasonId();
        for (int i = 0; i < 4; i++) {
            // Close
            auditsRecievedInitial = getAuditDetails(clientId, "CLOSE", "CLIENT");
            ClientHelper.closeClient(clientExternalId, closureReasonId.intValue());
            auditsRecieved = getAuditDetails(clientId, "CLOSE", "CLIENT");
            verifyMultipleAuditsOnserver(auditsRecievedInitial, auditsRecieved, clientId, "CLOSE", "CLIENT");

            // Activate
            auditsRecievedInitial = getAuditDetails(clientId, "REACTIVATE", "CLIENT");
            ClientHelper.reactivateClient(clientExternalId);
            auditsRecieved = getAuditDetails(clientId, "REACTIVATE", "CLIENT");
            verifyMultipleAuditsOnserver(auditsRecievedInitial, auditsRecieved, clientId, "REACTIVATE", "CLIENT");
        }

        // When Office is created
        OfficeHelper officeHelper = new OfficeHelper();
        int officeId = officeHelper.createOffice(java.time.LocalDate.of(2020, 6, 22)).getResourceId().intValue();
        auditsRecieved = getAuditDetails(officeId, "CREATE", "OFFICE");
        verifyOneAuditOnly(auditsRecieved, officeId, "CREATE", "OFFICE");
    }

    @Test
    public void checkAuditsWithLimitParam() {
        // Create client
        final PostClientsRequest clientRequest = ClientHelper.defaultClientCreationRequest();
        final PostClientsResponse clientResponse = ClientHelper.createClient(clientRequest);
        final String clientExternalId = clientRequest.getExternalId();

        final Long closureReasonId = retrieveOrCreateClientClosureReasonId();
        // The following loop would ensure database have atleast 8 audits.
        for (int i = 0; i < 4; i++) {
            // Close client
            ClientHelper.closeClient(clientExternalId, closureReasonId.intValue());
            // Activate client
            ClientHelper.reactivateClient(clientExternalId);
        }

        for (int i = 0; i < 3; i++) {
            // limit contains a number between 1-8
            int limit = rand.nextInt(7) + 1;
            verifyLimitParameterfor(limit);
        }
    }

    @Test
    public void checkIfOrderBySupported() {
        final List<String> shouldBeSupportedFor = Arrays.asList("checkedOnDate", "officeName", "resourceId", "clientId", "processingResult",
                "clientName", "maker", "subresourceId", "checker", "savingsAccountNo", "loanAccountNo", "groupName", "entityName",
                "madeOnDate", "id", "loanId", "actionName");

        for (int i = 0; i < shouldBeSupportedFor.size(); i++) {
            verifyOrderBysupported(shouldBeSupportedFor.get(i));
        }

    }

    @Test
    public void executeSchedulerJobShouldCreateAuditEntry() {
        // given
        final GetJobsResponse job = Calls.ok(FineractClientHelper.getFineractClient().jobs.retrieveByShortName("SA_AANF"));
        assertNotNull(job);
        final int jobId = job.getJobId().intValue();
        JsonNode auditsRecievedInitial = getAuditDetails(jobId, "EXECUTEJOB", "SCHEDULER");

        // when
        Calls.ok(FineractClientHelper.getFineractClient().jobs.executeJob((long) jobId, "executeJob"));

        // then
        JsonNode auditsRecieved = getAuditDetails(jobId, "EXECUTEJOB", "SCHEDULER");
        verifyMultipleAuditsOnserver(auditsRecievedInitial, auditsRecieved, jobId, "EXECUTEJOB", "SCHEDULER");
    }

    private JsonNode getAuditDetails(final Integer resourceId, final String actionName, final String entityName) {
        return readBody(auditApi.getAuditDetails(entityName, resourceId, actionName));
    }

    private void verifyOneAuditOnly(JsonNode auditsToCheck, Integer id, String actionName, String entityType) {
        assertEquals(1, auditsToCheck.size(), "More than one audit created");
        JsonNode auditToCheck = auditsToCheck.get(0);
        String actual = auditToCheck.get("actionName").asText() + " is done on " + auditToCheck.get("entityName").asText() + " with id "
                + auditToCheck.get("resourceId").asText();
        String expected = actionName + " is done on " + entityType + " with id " + id;
        assertEquals(expected, actual, "Error in creating audit!");
    }

    private void verifyMultipleAuditsOnserver(JsonNode auditsRecievedInitial, JsonNode auditsRecieved, Integer id, String actionName,
            String entityType) {
        assertEquals(auditsRecievedInitial.size() + 1, auditsRecieved.size(), "Audit is not Created");

        final List<JsonNode> sorted = new ArrayList<>();
        auditsRecieved.forEach(sorted::add);
        sorted.sort(Comparator.comparing((JsonNode a) -> a.get("id").asText()).reversed());

        // First element is new audit created(Sorted DESC by Id)
        JsonNode auditToCheck = sorted.get(0);
        String actual = auditToCheck.get("actionName").asText() + " is done on " + auditToCheck.get("entityName").asText() + " with id "
                + auditToCheck.get("resourceId").asText();
        String expected = actionName + " is done on " + entityType + " with id " + id;
        assertEquals(expected, actual, "Error in creating audit!");
    }

    private void verifyLimitParameterfor(final int limit) {
        final JsonNode pageItems = readBody(auditApi.getAuditsWithLimit(limit)).get("pageItems");
        assertEquals(limit, pageItems.size(), "Incorrect number of audits recieved for limit: " + limit);
    }

    private void verifyOrderBysupported(final String orderByValue) {
        try (Response response = auditApi.getAuditsOrderBy(orderByValue)) {
            assertEquals(200, response.status(), "Order by not supported for: " + orderByValue);
        }
    }

    private Long retrieveOrCreateClientClosureReasonId() {
        final Long codeId = ok(codesApi::retrieveCodes).stream().filter(c -> "ClientClosureReason".equals(c.getName()))
                .map(GetCodesResponse::getId).findFirst().orElseThrow();
        List<GetCodeValuesDataResponse> codeValues = ok(() -> codeValuesApi.retrieveAllCodeValues(codeId));
        if (codeValues.isEmpty()) {
            ok(() -> codeValuesApi.createCodeValue(codeId,
                    new PostCodeValuesDataRequest().name(Utils.randomStringGenerator("", 3)).position(0)));
            codeValues = ok(() -> codeValuesApi.retrieveAllCodeValues(codeId));
        }
        return codeValues.get(0).getId();
    }

    private static JsonNode readBody(final Response response) {
        try (Response r = response) {
            assertEquals(200, r.status(), "Unexpected status code");
            return MAPPER.readTree(Util.toString(r.body().asReader(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
