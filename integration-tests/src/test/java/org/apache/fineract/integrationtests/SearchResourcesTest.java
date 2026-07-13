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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.fineract.client.models.GetClientsClientIdResponse;
import org.apache.fineract.client.models.GetSearchResponse;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.integrationtests.client.feign.helpers.FeignSearchHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.shares.ShareAccountHelper;
import org.junit.jupiter.api.Test;

public class SearchResourcesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FeignSearchHelper searchHelper = new FeignSearchHelper(FineractFeignClientHelper.getFineractFeignClient());

    private final RawApi rawApi = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/savingsproducts")
        Response createSavingsProduct(JsonNode body);

        @RequestLine("POST v1/savingsaccounts")
        Response createSavingsAccount(JsonNode body);

        @RequestLine("POST v1/savingsaccounts/{id}?command={command}")
        Response savingsCommand(@Param("id") Integer id, @Param("command") String command, JsonNode body);

        @RequestLine("GET v1/savingsaccounts/{id}?associations=all")
        Response getSavingsAccount(@Param("id") Integer id);

        @RequestLine("POST v1/products/share")
        Response createShareProduct(JsonNode body);

        @RequestLine("POST v1/accounts/share")
        Response createShareAccount(JsonNode body);

        @RequestLine("POST v1/accounts/share/{id}?command={command}")
        Response shareCommand(@Param("id") Integer id, @Param("command") String command, JsonNode body);

        @RequestLine("GET v1/accounts/share/{id}")
        Response getShareAccount(@Param("id") Integer id);
    }

    @Test
    public void searchAnyValueOverAllResources() {
        final String resources = "clients,clientIdentifiers,groups,savings,shares,loans";

        final String query = Utils.randomStringGenerator("C", 12);
        final List<GetSearchResponse> searchResponse = searchHelper.search(query, resources, Boolean.TRUE);
        assertNotNull(searchResponse);
        assertEquals(0, searchResponse.size());
    }

    @Test
    public void searchAnyValueOverClientResources() {
        final String resources = "clients";

        final String query = Utils.randomStringGenerator("C", 12);
        final List<GetSearchResponse> searchResponse = searchHelper.search(query, resources, Boolean.TRUE);
        assertNotNull(searchResponse);
        assertEquals(0, searchResponse.size());
    }

    @Test
    public void searchOverClientResources() {
        final String resources = "clients";

        final PostClientsResponse clientResponse = ClientHelper.addClientAsPerson(ClientHelper.DEFAULT_OFFICE_ID,
                ClientHelper.LEGALFORM_ID_PERSON, null);
        final Long clientId = clientResponse.getClientId();
        final GetClientsClientIdResponse getClientResponse = ClientHelper.getClient(clientId);
        final String query = getClientResponse.getAccountNo();

        final List<GetSearchResponse> searchResponse = searchHelper.search(query, resources, Boolean.FALSE);
        assertNotNull(searchResponse);
        assertEquals(1, searchResponse.size());
        assertEquals(getClientResponse.getDisplayName(), searchResponse.get(0).getEntityName(), "Client name comparation");
    }

    @Test
    public void searchAnyValueOverLoanResources() {
        final String resources = "loans";

        final String query = Utils.randomStringGenerator("L", 12);
        final List<GetSearchResponse> searchResponse = searchHelper.search(query, resources, Boolean.TRUE);
        assertNotNull(searchResponse);
        assertEquals(0, searchResponse.size());
    }

    @Test
    public void searchOverSavingsResources() {
        final String resources = "savings";

        final PostClientsResponse clientResponse = ClientHelper.addClientAsPerson(ClientHelper.DEFAULT_OFFICE_ID,
                ClientHelper.LEGALFORM_ID_PERSON, null);
        final Long clientId = clientResponse.getClientId();

        final Integer savingsId = openSavingsAccount(clientId.intValue(), "1000");
        final String query = getSavingsAccountNo(savingsId);

        final List<GetSearchResponse> searchResponse = searchHelper.search(query, resources, Boolean.FALSE);

        assertNotNull(searchResponse);
        assertEquals(1, searchResponse.size());

        final GetSearchResponse result = searchResponse.getFirst();

        assertEquals("SAVING", result.getEntityType());
        assertNotNull(result.getEntityStatus());
        assertNotNull(result.getEntityStatus().getId());
        assertNotNull(result.getEntityStatus().getCode());
        assertNotNull(result.getEntityStatus().getValue());
    }

    @Test
    public void searchOverSharesResources() {
        final String resources = "shares";

        final PostClientsResponse clientsResponse = ClientHelper.addClientAsPerson(ClientHelper.DEFAULT_OFFICE_ID,
                ClientHelper.LEGALFORM_ID_PERSON, null);
        final Long clientId = clientsResponse.getClientId();

        final Integer productId = createShareProduct();

        final Integer savingsId = openSavingsAccount(clientId.intValue(), "1000");

        final String shareJson = new ShareAccountHelper().withClientId(String.valueOf(clientId)).withProductId(String.valueOf(productId))
                .withSavingsAccountId(String.valueOf(savingsId)).withSubmittedDate("01 January 2026").withApplicationDate("01 January 2026")
                .withRequestedShares("10").build();

        final Integer shareAccountId = createShareAccount(shareJson);

        final String approveJson = "{}";
        shareAccountCommand("approve", shareAccountId, approveJson);

        final String activateJson = """
                {
                  "activatedDate": "01 January 2026",
                  "dateFormat": "dd MMMM yyyy",
                  "locale": "en"
                }
                """;
        shareAccountCommand("activate", shareAccountId, activateJson);

        final String query = getShareAccountNo(shareAccountId);

        final List<GetSearchResponse> searchResponse = searchHelper.search(query, resources, Boolean.FALSE);

        assertNotNull(searchResponse);
        assertEquals(1, searchResponse.size());

        final GetSearchResponse result = searchResponse.getFirst();

        assertEquals("SHARE", result.getEntityType());
        assertNotNull(result.getEntityStatus());
        assertNotNull(result.getEntityStatus().getId());
        assertNotNull(result.getEntityStatus().getCode());
        assertNotNull(result.getEntityStatus().getValue());
    }

    private Integer openSavingsAccount(final Integer clientId, final String minimumOpeningBalance) {
        final Integer savingsProductId = readBody(rawApi.createSavingsProduct(buildSavingsProductJson(minimumOpeningBalance)))
                .get("resourceId").asInt();

        final String savingsApplicationJson = new SavingsApplicationTestBuilder().withSubmittedOnDate(SavingsAccountHelper.CREATED_DATE)
                .build(clientId.toString(), savingsProductId.toString(), SavingsAccountHelper.ACCOUNT_TYPE_INDIVIDUAL);
        final Integer savingsId = readBody(rawApi.createSavingsAccount(readTree(savingsApplicationJson))).get("savingsId").asInt();

        final ObjectNode approve = MAPPER.createObjectNode();
        approve.put("locale", "en");
        approve.put("dateFormat", "dd MMMM yyyy");
        approve.put("approvedOnDate", SavingsAccountHelper.CREATED_DATE_PLUS_ONE);
        approve.put("note", "Approval NOTE");
        readBody(rawApi.savingsCommand(savingsId, "approve", approve));

        final ObjectNode activate = MAPPER.createObjectNode();
        activate.put("locale", "en");
        activate.put("dateFormat", "dd MMMM yyyy");
        activate.put("activatedOnDate", SavingsAccountHelper.TRANSACTION_DATE);
        readBody(rawApi.savingsCommand(savingsId, "activate", activate));

        return savingsId;
    }

    private String getSavingsAccountNo(final Integer savingsId) {
        return readBody(rawApi.getSavingsAccount(savingsId)).get("accountNo").asText();
    }

    private Integer createShareProduct() {
        return readBody(rawApi.createShareProduct(buildShareProductJson())).get("resourceId").asInt();
    }

    private Integer createShareAccount(final String shareJson) {
        return readBody(rawApi.createShareAccount(readTree(shareJson))).get("resourceId").asInt();
    }

    private void shareAccountCommand(final String command, final Integer shareAccountId, final String jsonBody) {
        readBody(rawApi.shareCommand(shareAccountId, command, readTree(jsonBody)));
    }

    private String getShareAccountNo(final Integer shareAccountId) {
        return readBody(rawApi.getShareAccount(shareAccountId)).get("accountNo").asText();
    }

    private static JsonNode buildSavingsProductJson(final String minimumOpeningBalance) {
        final ObjectNode map = MAPPER.createObjectNode();
        map.put("name", Utils.uniqueRandomStringGenerator("SAVINGS_PRODUCT_", 6));
        map.put("shortName", Utils.uniqueRandomStringGenerator("", 4));
        map.put("description", Utils.randomStringGenerator("", 20));
        map.put("currencyCode", "USD");
        map.put("interestCalculationDaysInYearType", "365");
        map.put("locale", "en_GB");
        map.put("digitsAfterDecimal", "4");
        map.put("inMultiplesOf", "0");
        map.put("interestCalculationType", "1");
        map.put("nominalAnnualInterestRate", "10.0");
        map.put("interestCompoundingPeriodType", "1");
        map.put("interestPostingPeriodType", "4");
        map.put("accountingRule", "1");
        map.put("minRequiredOpeningBalance", minimumOpeningBalance);
        map.put("lockinPeriodFrequency", "0");
        map.put("lockinPeriodFrequencyType", "0");
        map.put("withdrawalFeeForTransfers", "true");
        map.put("allowOverdraft", "false");
        map.put("enforceMinRequiredBalance", "false");
        map.put("lienAllowed", "false");
        map.put("withHoldTax", "false");
        return map;
    }

    private static JsonNode buildShareProductJson() {
        final ObjectNode map = MAPPER.createObjectNode();
        map.put("name", Utils.uniqueRandomStringGenerator("SHARE_PRODUCT_", 6));
        map.put("shortName", Utils.uniqueRandomStringGenerator("", 4));
        map.put("description", Utils.randomStringGenerator("", 20));
        map.put("currencyCode", "USD");
        map.put("locale", "en_GB");
        map.put("digitsAfterDecimal", "4");
        map.put("inMultiplesOf", "0");
        map.put("totalShares", "10000");
        map.put("sharesIssued", "10000");
        map.put("unitPrice", "2.0");
        map.put("minimumShares", "10");
        map.put("nominalShares", "20");
        map.put("maximumShares", "3000");
        map.put("allowDividendCalculationForInactiveClients", "true");
        map.put("accountingRule", "1");
        map.put("minimumActivePeriodForDividends", "1");
        map.put("minimumactiveperiodFrequencyType", "0");
        map.put("lockinPeriodFrequency", "1");
        map.put("lockinPeriodFrequencyType", "0");
        return map;
    }

    private static JsonNode readTree(final String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode readBody(final Response response) {
        try (Response r = response) {
            return MAPPER.readTree(Util.toString(r.body().asReader(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
