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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.DelinquencyBucketResponse;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.junit.jupiter.api.Test;

public class LoanAccountOverpaidDateStatusTest extends BaseLoanIntegrationTest {

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("GET v1/loanproducts/{loanProductId}")
        Response loanProduct(@Param("loanProductId") Integer loanProductId);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("POST v1/loans/{loanId}?command={command}")
        Response loanCommand(@Param("loanId") Integer loanId, @Param("command") String command, JsonNode body);
    }

    private static String rawBody(Response response) {
        try (Response r = response) {
            return Util.toString(r.body().asReader(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode toJsonNode(String json) {
        try {
            return RAW_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String localApproveLoanAsJSON(final String approvalDate, final String approvalAmount) {
        final HashMap<String, Object> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        if (approvalAmount != null) {
            map.put("approvedLoanAmount", approvalAmount);
        }
        map.put("approvedOnDate", approvalDate);
        map.put("note", "Approval NOTE");
        return new Gson().toJson(map);
    }

    private static String localDisburseWithNetDisbursalAmountAsJSON(final String actualDisbursementDate, final String netDisbursalAmount) {
        final HashMap<String, String> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("actualDisbursementDate", actualDisbursementDate);
        if (netDisbursalAmount != null) {
            map.put("netDisbursalAmount", netDisbursalAmount);
        }
        map.put("note", "DISBURSE NOTE");
        return new Gson().toJson(map);
    }

    @Test
    public void loanOverpaidDateStatusTest() {
        // Set business date
        try {
            final LocalDate todaysDate = Utils.getLocalDateOfTenant();

            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(Utils.dateFormatter.format(todaysDate)).dateFormat(Utils.DATE_FORMAT).locale("en"));

            // Loan ExternalId
            String loanExternalIdStr = UUID.randomUUID().toString();

            // Delinquency Bucket
            final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
            final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);

            // Client and Loan account creation

            final Integer clientId = clientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(delinquencyBucketId);
            assertNotNull(getLoanProductsProductResponse);

            final Integer loanId = createLoanAccount(clientId, getLoanProductsProductResponse.getId(), loanExternalIdStr);

            // make Repayments
            final PostLoansLoanIdTransactionsResponse repaymentTransaction_1 = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("5 September 2022").locale("en")
                            .transactionAmount(200.0));

            final PostLoansLoanIdTransactionsResponse repaymentTransaction_2 = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("6 September 2022").locale("en")
                            .transactionAmount(200.0));

            final PostLoansLoanIdTransactionsResponse repaymentTransaction_3 = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("7 September 2022").locale("en")
                            .transactionAmount(500.0));

            // make repayment to make loan overpaid
            final PostLoansLoanIdTransactionsResponse repaymentTransaction_4 = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("9 September 2022").locale("en")
                            .transactionAmount(200.0));

            // check loan overpaid date is not null and is set as Business date and loan status
            GetLoansLoanIdResponse loanDetailsOverpaid = loanTransactionHelper.getLoanDetails((long) loanId);
            assertTrue(loanDetailsOverpaid.getStatus().getOverpaid());
            assertNotNull(loanDetailsOverpaid.getOverpaidOnDate());
            assertEquals(loanDetailsOverpaid.getOverpaidOnDate(), LocalDate.of(2022, 9, 9));

            // reverse repayment to make loan not overpaid and overpaid date is reset
            loanTransactionHelper.reverseLoanTransaction(loanId.longValue(), repaymentTransaction_4.getResourceId(), "10 September 2022");
            GetLoansLoanIdResponse loanDetailsNotOverpaidAfterReversal = loanTransactionHelper.getLoanDetails((long) loanId);
            assertFalse(loanDetailsNotOverpaidAfterReversal.getStatus().getOverpaid());
            assertNull(loanDetailsNotOverpaidAfterReversal.getOverpaidOnDate());

            // make repayment to make loan overpaid again
            final PostLoansLoanIdTransactionsResponse repaymentTransaction_5 = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("11 September 2022").locale("en")
                            .transactionAmount(200.0));

            // check loan overpaid date is not null and is set as Business date and loan status
            GetLoansLoanIdResponse loanDetailsOverpaid_1 = loanTransactionHelper.getLoanDetails((long) loanId);
            assertTrue(loanDetailsOverpaid_1.getStatus().getOverpaid());
            assertNotNull(loanDetailsOverpaid_1.getOverpaidOnDate());
            assertEquals(loanDetailsOverpaid_1.getOverpaidOnDate(), LocalDate.of(2022, 9, 11));

            // Credit balance refund to reset overpaid status
            loanTransactionHelper.makeCreditBalanceRefund(loanId.longValue(),
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("12 September 2022").locale("en")
                            .transactionAmount(100.0).note("Credit Balance Refund Made!!!"));
            GetLoansLoanIdResponse loanDetailsNotOverpaidAfterCBR = loanTransactionHelper.getLoanDetails((long) loanId);
            assertFalse(loanDetailsNotOverpaidAfterCBR.getStatus().getOverpaid());
            assertNull(loanDetailsNotOverpaidAfterCBR.getOverpaidOnDate());

            // reverse repayment to make loan active again
            loanTransactionHelper.reverseLoanTransaction(loanId.longValue(), repaymentTransaction_2.getResourceId(), "13 September 2022");
            GetLoansLoanIdResponse loanDetailsNotOverpaidAfterReversal_1 = loanTransactionHelper.getLoanDetails((long) loanId);
            assertFalse(loanDetailsNotOverpaidAfterReversal_1.getStatus().getOverpaid());
            assertNull(loanDetailsNotOverpaidAfterReversal_1.getOverpaidOnDate());

            // make repayment to make loan overpaid again
            final PostLoansLoanIdTransactionsResponse repaymentTransaction_6 = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("14 September 2022").locale("en")
                            .transactionAmount(300.0));

            // check loan overpaid date is not null and is set as Business date and loan status
            GetLoansLoanIdResponse loanDetailsOverpaid_3 = loanTransactionHelper.getLoanDetails((long) loanId);
            assertTrue(loanDetailsOverpaid_3.getStatus().getOverpaid());
            assertNotNull(loanDetailsOverpaid_3.getOverpaidOnDate());
            assertEquals(loanDetailsOverpaid_3.getOverpaidOnDate(), LocalDate.of(2022, 9, 14));
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }

    }

    private GetLoanProductsProductIdResponse createLoanProduct(final Long delinquencyBucketId) {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().build(null, delinquencyBucketId);
        final Integer loanProductId = toJsonNode(rawBody(RAW.createLoanProduct(toJsonNode(Utils.convertToJson(loanProductMap)))))
                .get("resourceId").asInt();
        return new JSON().getGson().fromJson(rawBody(RAW.loanProduct(loanProductId)), GetLoanProductsProductIdResponse.class);
    }

    private Integer createLoanAccount(final Integer clientID, final Long loanProductID, final String externalId) {

        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("1000").withLoanTermFrequency("1")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("1").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("0").withInterestTypeAsFlatBalance()
                .withAmortizationTypeAsEqualPrincipalPayments().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate("03 September 2022").withSubmittedOnDate("01 September 2022").withLoanType("individual")
                .withExternalId(externalId).build(clientID.toString(), loanProductID.toString(), null);

        final Integer loanId = toJsonNode(rawBody(RAW.createLoan(toJsonNode(loanApplicationJSON)))).get("loanId").asInt();
        rawBody(RAW.loanCommand(loanId, "approve", toJsonNode(localApproveLoanAsJSON("02 September 2022", "1000"))));
        rawBody(RAW.loanCommand(loanId, "disburse", toJsonNode(localDisburseWithNetDisbursalAmountAsJSON("03 September 2022", "1000"))));
        return loanId;
    }
}
