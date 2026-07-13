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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.savings.SavingsStatusChecker;
import org.apache.fineract.integrationtests.common.savings.SavingsTestLifecycleExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({ "rawtypes", "unchecked" })
@ExtendWith({ SavingsTestLifecycleExtension.class })
public class SavingsInterestPostingIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(SavingsInterestPostingIntegrationTest.class);
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";

    // Typed Feign layer used to submit/fetch the exact request and response payloads that the assertions in this test
    // rely on, so the deprecated RestAssured-based helpers can be replaced while preserving every payload, response
    // shape, status code and assertion. Bodies are parsed with the same JsonPath machinery the helpers used internally.
    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/savingsproducts")
        Response createSavingsProduct(JsonNode body);

        @RequestLine("POST v1/savingsaccounts")
        Response createSavingsAccount(JsonNode body);

        @RequestLine("POST v1/savingsaccounts/{savingsId}?command={command}")
        Response savingsCommand(@Param("savingsId") Integer savingsId, @Param("command") String command, JsonNode body);

        @RequestLine("POST v1/savingsaccounts/{savingsId}/transactions?command={command}")
        Response savingsTransactionCommand(@Param("savingsId") Integer savingsId, @Param("command") String command, JsonNode body);

        @RequestLine("GET v1/savingsaccounts/{savingsId}?associations=all")
        Response savingsWithAllAssociations(@Param("savingsId") Integer savingsId);
    }

    private static String rawBody(Response response) {
        try (Response r = response) {
            return Util.toString(r.body().asReader(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode rawJson(String json) {
        try {
            return RAW_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T extract(String body, String path) {
        return JsonPath.from(body).get(path);
    }

    private GlobalConfigurationHelper globalConfigurationHelper;

    @BeforeEach
    public void setup() {
        this.globalConfigurationHelper = new GlobalConfigurationHelper();
    }

    @Test
    public void testSavingsDailyInterestPosting() {
        LocalDate today = Utils.getLocalDateOfTenant();
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(Utils.dateFormatter.format(today)).dateFormat(Utils.DATE_FORMAT).locale("en"));
            // client activation, savings activation and 1st transaction date
            final String startDate = "01 November 2021";
            final Integer clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest().activationDate(startDate))
                    .getClientId().intValue();
            Assertions.assertNotNull(clientID);

            final Integer savingsId = createSavingsAccountDailyPosting(clientID, startDate);

            depositToSavingsAccount(savingsId, "1000", startDate);

            /***
             * Perform Post interest transaction and verify the posted transaction date
             */
            postInterestForSavings(savingsId);
            HashMap accountDetails = getSavingsDetails(savingsId);
            ArrayList<HashMap<String, Object>> transactions = (ArrayList<HashMap<String, Object>>) accountDetails.get("transactions");
            HashMap<String, Object> interestPostingTransaction = transactions.get(transactions.size() - 2);
            for (Map.Entry<String, Object> entry : interestPostingTransaction.entrySet()) {
                LOG.info("{} - {}", entry.getKey(), String.valueOf(entry.getValue()));
            }
            assertEquals("0.274", interestPostingTransaction.get("amount").toString(), "Equality check for interest posted amount");
            assertEquals("[2021, 11, 2]", interestPostingTransaction.get("date").toString(), "Date check for Interest Posting transaction");
            List<Integer> submittedOnDateStringList = (List<Integer>) interestPostingTransaction.get("submittedOnDate");
            LocalDate submittedOnDate = submittedOnDateStringList.stream().collect(
                    Collectors.collectingAndThen(Collectors.toList(), list -> LocalDate.of(list.get(0), list.get(1), list.get(2))));
            assertTrue(DateUtils.isEqual(submittedOnDate, today), "Submitted On Date check for Interest Posting transaction");
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }

    }

    private Integer createSavingsAccountDailyPosting(final Integer clientID, final String startDate) {
        final Integer savingsProductID = createSavingsProductDailyPosting();
        Assertions.assertNotNull(savingsProductID);
        final Integer savingsId = applyForSavingsApplicationOnDate(clientID, savingsProductID, ACCOUNT_TYPE_INDIVIDUAL, startDate);
        Assertions.assertNotNull(savingsId);
        HashMap savingsStatusHashMap = approveSavingsOnDate(savingsId, startDate);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);
        savingsStatusHashMap = activateSavingsAccount(savingsId, startDate);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);
        return savingsId;
    }

    // ---- Local adapters preserving the exact payloads/response shapes of the removed deprecated helpers -------------

    private static Integer createSavingsProductDailyPosting() {
        final Map<String, String> map = new HashMap<>();
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
        map.put("interestPostingPeriodType", "1");
        map.put("accountingRule", "1");
        map.put("lockinPeriodFrequency", "0");
        map.put("withdrawalFeeForTransfers", "true");
        map.put("lockinPeriodFrequencyType", "0");
        map.put("allowOverdraft", "false");
        map.put("enforceMinRequiredBalance", "false");
        map.put("lienAllowed", "false");
        map.put("withHoldTax", "false");
        return extract(rawBody(RAW.createSavingsProduct(rawJson(new Gson().toJson(map)))), "resourceId");
    }

    private static Integer applyForSavingsApplicationOnDate(final Integer clientOrGroupId, final Integer savingsProductID,
            final String accountType, final String submittedOnDate) {
        final Map<String, Object> map = new HashMap<>();
        map.put("dateFormat", "dd MMMM yyyy");
        if (accountType.equals("GROUP")) {
            map.put("groupId", clientOrGroupId.toString());
        } else {
            map.put("clientId", clientOrGroupId.toString());
        }
        map.put("productId", savingsProductID.toString());
        map.put("locale", "en_GB");
        map.put("submittedOnDate", submittedOnDate);
        map.put("withdrawalFeeForTransfers", false);
        return extract(rawBody(RAW.createSavingsAccount(rawJson(new Gson().toJson(map)))), "savingsId");
    }

    private static HashMap approveSavingsOnDate(final Integer savingsID, final String approvalDate) {
        final Map<String, Object> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("approvedOnDate", approvalDate);
        map.put("note", "Approval NOTE");
        return extract(rawBody(RAW.savingsCommand(savingsID, "approve", rawJson(new Gson().toJson(map)))), "changes.status");
    }

    private static HashMap activateSavingsAccount(final Integer savingsID, final String activationDate) {
        final Map<String, Object> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("activatedOnDate", activationDate);
        return extract(rawBody(RAW.savingsCommand(savingsID, "activate", rawJson(new Gson().toJson(map)))), "changes.status");
    }

    private static void depositToSavingsAccount(final Integer savingsID, final String amount, final String date) {
        final Map<String, Object> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("transactionDate", date);
        map.put("transactionAmount", amount);
        map.put("paymentTypeId", 1L);
        rawBody(RAW.savingsTransactionCommand(savingsID, "deposit", rawJson(new Gson().toJson(map))));
    }

    private static void postInterestForSavings(final Integer savingsId) {
        rawBody(RAW.savingsCommand(savingsId, "postInterest", rawJson("{}")));
    }

    private static HashMap getSavingsDetails(final Integer savingsID) {
        return extract(rawBody(RAW.savingsWithAllAssociations(savingsID)), "");
    }

    // Reset configuration fields
    @AfterEach
    public void tearDown() {
        globalConfigurationHelper.resetAllDefaultGlobalConfigurations();
        globalConfigurationHelper.verifyAllDefaultGlobalConfigurations();
    }

}
