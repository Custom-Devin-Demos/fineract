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

import static org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder.ACCRUAL_PERIODIC;
import static org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder.DEFAULT_STRATEGY;
import static org.apache.fineract.portfolio.loanaccount.domain.transactionprocessor.impl.AdvancedPaymentScheduleTransactionProcessor.ADVANCED_PAYMENT_ALLOCATION_STRATEGY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.CreditAllocationData;
import org.apache.fineract.client.models.CreditAllocationOrder;
import org.apache.fineract.client.models.DelinquencyBucketResponse;
import org.apache.fineract.client.models.DelinquencyRangeData;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentSchedule;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.client.models.PaymentAllocationOrder;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.fineract.portfolio.loanproduct.domain.PaymentAllocationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Slf4j
public class LoanTransactionChargebackTest extends BaseLoanIntegrationTest {

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    private final String amountVal = "1000";
    private LocalDate todaysDate;
    private String operationDate;
    private static Long clientId;

    @BeforeEach
    public void setup() {
        clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getResourceId();
        this.todaysDate = Utils.getLocalDateOfTenant();
        this.operationDate = Utils.dateFormatter.format(this.todaysDate);
    }

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("POST v1/loans/{loanId}?command={command}")
        Response loanCommand(@Param("loanId") Integer loanId, @Param("command") String command, JsonNode body);

        @RequestLine("POST v1/loans/{loanId}/transactions/{transactionId}?command={command}")
        Response loanTransactionCommand(@Param("loanId") Integer loanId, @Param("transactionId") Long transactionId,
                @Param("command") String command, JsonNode body);
    }

    private static String rawBody(final Response response) {
        try (Response r = response) {
            return Util.toString(r.body().asReader(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode rawJson(final String json) {
        try {
            return RAW_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertRawStatus(final Response response, final int expectedStatus) {
        try (Response r = response) {
            assertEquals(expectedStatus, r.status());
        }
    }

    private PostLoansLoanIdTransactionsResponse makeRepayment(final String date, final Float amount, final Integer loanId) {
        return loanTransactionHelper.makeLoanRepayment(loanId.longValue(),
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").locale("en").transactionDate(date)
                        .transactionAmount(amount.doubleValue()).note("Repayment Made!!!"));
    }

    private Long applyChargeback(final Integer loanId, final Long transactionId, final String amount, final Integer paymentTypeIdx) {
        final Long paymentTypeId = PaymentTypeHelper.getAllPaymentTypes(false).get(paymentTypeIdx).getId();
        return loanTransactionHelper
                .chargebackLoanTransaction(loanId.longValue(), transactionId, new PostLoansLoanIdTransactionsTransactionIdRequest()
                        .transactionAmount(Double.valueOf(amount)).paymentTypeId(paymentTypeId).locale("en"))
                .getResourceId();
    }

    private void applyChargebackExpectingError(final Integer loanId, final Long transactionId, final String amount,
            final Integer paymentTypeIdx, final int expectedStatus) {
        final Long paymentTypeId = PaymentTypeHelper.getAllPaymentTypes(false).get(paymentTypeIdx).getId();
        final HashMap<String, Object> body = new HashMap<>();
        body.put("transactionAmount", amount);
        body.put("paymentTypeId", paymentTypeId);
        body.put("locale", "en");
        assertRawStatus(RAW.loanTransactionCommand(loanId, transactionId, "chargeback", rawJson(new Gson().toJson(body))), expectedStatus);
    }

    private void reverseLoanTransactionExpectingError(final Integer loanId, final Long transactionId, final String date,
            final int expectedStatus) {
        assertRawStatus(RAW.loanTransactionCommand(loanId, transactionId, "undo", adjustBody(date, "0")), expectedStatus);
    }

    private void adjustLoanTransactionExpectingError(final Integer loanId, final Long transactionId, final String date,
            final int expectedStatus) {
        assertRawStatus(RAW.loanTransactionCommand(loanId, transactionId, "adjust", adjustBody(date, "10")), expectedStatus);
    }

    private JsonNode adjustBody(final String date, final String amount) {
        final HashMap<String, String> map = new HashMap<>();
        map.put("transactionDate", date);
        map.put("transactionAmount", amount);
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("locale", "en");
        return rawJson(new Gson().toJson(map));
    }

    private void updateBusinessDate(final LocalDate date) {
        BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                .date(Utils.dateFormatter.format(date)).dateFormat(Utils.DATE_FORMAT).locale("en"));
    }

    private Integer postLoanProduct(final String loanProductJSON) {
        return rawJson(rawBody(RAW.createLoanProduct(rawJson(loanProductJSON)))).get("resourceId").asInt();
    }

    private Integer postLoanApplication(final String loanApplicationJSON) {
        return rawJson(rawBody(RAW.createLoan(rawJson(loanApplicationJSON)))).get("loanId").asInt();
    }

    private void disburseLoanWithNetDisbursalAmount(final Integer loanId, final String date, final String netDisbursalAmount) {
        final HashMap<String, String> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("actualDisbursementDate", date);
        map.put("netDisbursalAmount", netDisbursalAmount);
        map.put("note", "DISBURSE NOTE");
        rawBody(RAW.loanCommand(loanId, "disburse", rawJson(new Gson().toJson(map))));
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargeback(LoanProductTestBuilder loanProductTestBuilder) {
        // Client and Loan account creation
        final Integer loanId = createAccounts(15, 1, true, loanProductTestBuilder);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);

        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

        Float amount = Float.valueOf(amountVal);
        PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
        assertNotNull(loanIdTransactionsResponse);
        final Long transactionId = loanIdTransactionsResponse.getResourceId();
        assertNotNull(transactionId);

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.closed.obligations.met");

        reviewLoanTransactionRelations(loanId, transactionId, 0, Double.valueOf("0.00"));

        final Long chargebackTransactionId = applyChargeback(loanId, transactionId, "1000.00", 0);

        reviewLoanTransactionRelations(loanId, transactionId, 1, Double.valueOf("0.00"));
        reviewLoanTransactionRelations(loanId, chargebackTransactionId, 0, Double.valueOf("1000.00"));

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.active");

        loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, amount.doubleValue());

        verifyTRJournalEntries(chargebackTransactionId, //
                credit(fundSource, 1000.0), //
                debit(loansReceivableAccount, 1000.0) //
        );

        // Try to reverse a Loan Transaction charge back
        reverseLoanTransactionExpectingError(loanId, chargebackTransactionId, operationDate, 403);

        // Try to reverse a Loan Transaction repayment with linked transactions
        reverseLoanTransactionExpectingError(loanId, transactionId, operationDate, 403);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyAndAdjustLoanTransactionChargeback(LoanProductTestBuilder loanProductTestBuilder) {
        // Client and Loan account creation
        final Integer loanId = createAccounts(15, 1, false, loanProductTestBuilder);

        Float amount = Float.valueOf(amountVal);
        PostLoansLoanIdTransactionsResponse loanTransactionResponse = makeRepayment(operationDate, amount, loanId);
        assertNotNull(loanTransactionResponse);
        final Long transactionId = loanTransactionResponse.getResourceId();

        final Long chargebackTransactionId = applyChargeback(loanId, transactionId, "1000.00", 0);

        // Then
        adjustLoanTransactionExpectingError(loanId, chargebackTransactionId, operationDate, 403);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargebackWithAmountZero(LoanProductTestBuilder loanProductTestBuilder) {
        // Client and Loan account creation
        final Integer loanId = createAccounts(15, 1, false, loanProductTestBuilder);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);

        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

        Float amount = Float.valueOf(amountVal);
        PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
        assertNotNull(loanIdTransactionsResponse);
        final Long transactionId = loanIdTransactionsResponse.getResourceId();

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.closed.obligations.met");

        applyChargebackExpectingError(loanId, transactionId, "0.00", 0, 400);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargebackInLongTermLoan(LoanProductTestBuilder loanProductTestBuilder) {
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            LocalDate businessDate = LocalDate.of(2023, 1, 20);
            todaysDate = businessDate;
            updateBusinessDate(businessDate);
            // Client and Loan account creation
            final Integer daysToSubtract = 1;
            final Integer numberOfRepayments = 3;
            final Integer loanId = createAccounts(daysToSubtract, numberOfRepayments, false, loanProductTestBuilder);

            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);

            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            final String baseAmount = "333.33";
            Float amount = Float.valueOf(baseAmount);
            final LocalDate transactionDate = this.todaysDate.minusMonths(numberOfRepayments - 1).plusDays(3);
            String operationDate = Utils.dateFormatter.format(transactionDate);

            PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
            assertNotNull(loanIdTransactionsResponse);
            final Long transactionId = loanIdTransactionsResponse.getResourceId();
            reviewLoanTransactionRelations(loanId, transactionId, 0, Double.valueOf("666.67"));

            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);

            final Long chargebackTransactionId = applyChargeback(loanId, transactionId, amount.toString(), 0);
            reviewLoanTransactionRelations(loanId, transactionId, 1, Double.valueOf("666.67"));
            reviewLoanTransactionRelations(loanId, chargebackTransactionId, 0, Double.valueOf("1000.00"));

            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);

            loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, Double.valueOf(amountVal));

            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);
            GetLoansLoanIdRepaymentSchedule getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
            for (GetLoansLoanIdRepaymentPeriod period : getLoanRepaymentSchedule.getPeriods()) {
                if (period.getPeriod() != null && period.getPeriod() == 3) {
                    log.info("Period number {} for due date {} and totalDueForPeriod {}", period.getPeriod(), period.getDueDate(),
                            period.getTotalDueForPeriod());
                    assertEquals(Double.valueOf("666.67"), Utils.getDoubleValue(period.getTotalDueForPeriod()));
                }
            }

            loanTransactionHelper.evaluateLoanSummaryAdjustments(getLoansLoanIdResponse, Double.valueOf(baseAmount));
            DelinquencyBucketsHelper.evaluateLoanCollectionData(getLoansLoanIdResponse, 0, Double.valueOf("0.00"));
        } finally {
            final LocalDate todaysDate = Utils.getLocalDateOfTenant();
            updateBusinessDate(todaysDate);
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargebackOverNoRepaymentType(LoanProductTestBuilder loanProductTestBuilder) {
        // Client and Loan account creation
        final Integer loanId = createAccounts(15, 1, false, loanProductTestBuilder);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);

        List<GetLoansLoanIdTransactions> loanTransactions = getLoansLoanIdResponse.getTransactions();
        assertNotNull(loanTransactions);
        log.info("Loan Id {} with {} transactions", loanId, loanTransactions.size());
        assertEquals(1, loanTransactions.size());
        GetLoansLoanIdTransactions loanTransaction = loanTransactions.iterator().next();
        log.info("Try to apply the Charge back over transaction Id {} with type {}", loanTransaction.getId(),
                loanTransaction.getType().getCode());

        applyChargebackExpectingError(loanId, loanTransaction.getId(), amountVal, 0, 503);
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargebackAfterMature(LoanProductTestBuilder loanProductTestBuilder) {
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));

            final LocalDate todaysDate = Utils.getLocalDateOfTenant();
            updateBusinessDate(todaysDate);
            log.info("Current Business date {}", todaysDate);

            // Client and Loan account creation
            final Integer loanId = createAccounts(45, 1, false, loanProductTestBuilder);

            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);

            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            DelinquencyRangeData delinquencyRange = getLoansLoanIdResponse.getDelinquencyRange();
            assertNotNull(delinquencyRange);
            log.info("Loan Delinquency Range is {}", delinquencyRange.getClassification());

            GetLoansLoanIdRepaymentSchedule getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
            log.info("Loan with {} periods", getLoanRepaymentSchedule.getPeriods().size());
            assertEquals(2, getLoanRepaymentSchedule.getPeriods().size());

            Float amount = Float.valueOf(amountVal);
            PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
            assertNotNull(loanIdTransactionsResponse);
            final Long transactionId = loanIdTransactionsResponse.getResourceId();

            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);
            loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.closed.obligations.met");
            assertNotNull(getLoansLoanIdResponse.getTimeline());
            assertEquals(todaysDate, getLoansLoanIdResponse.getTimeline().getActualMaturityDate());

            reviewLoanTransactionRelations(loanId, transactionId, 0, Double.valueOf("0.00"));

            Long chargebackTransactionId = applyChargeback(loanId, transactionId, "500.00", 0);

            reviewLoanTransactionRelations(loanId, transactionId, 1, Double.valueOf("0.00"));
            reviewLoanTransactionRelations(loanId, chargebackTransactionId, 0, Double.valueOf("500.00"));

            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);
            loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.active");

            loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, Double.valueOf("500.00"));

            assertNotNull(getLoansLoanIdResponse.getTimeline());
            assertEquals(getLoansLoanIdResponse.getTimeline().getExpectedMaturityDate(),
                    getLoansLoanIdResponse.getTimeline().getActualMaturityDate());

            // N+1 Scenario
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);
            getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
            log.info("Loan with {} periods", getLoanRepaymentSchedule.getPeriods().size());
            assertEquals(3, getLoanRepaymentSchedule.getPeriods().size());
            getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
            for (GetLoansLoanIdRepaymentPeriod period : getLoanRepaymentSchedule.getPeriods()) {
                if (period.getPeriod() != null && period.getPeriod() == 2) {
                    log.info("Period number {} for due date {} and totalDueForPeriod {}", period.getPeriod(), period.getDueDate(),
                            period.getTotalDueForPeriod());
                    assertEquals(Double.valueOf("500.00"), Utils.getDoubleValue(period.getPrincipalDue()));
                }
            }

            chargebackTransactionId = applyChargeback(loanId, transactionId, "300.00", 0);

            reviewLoanTransactionRelations(loanId, transactionId, 2, Double.valueOf("0.00"));
            reviewLoanTransactionRelations(loanId, chargebackTransactionId, 0, Double.valueOf("800.00"));

            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);
            loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.active");

            delinquencyRange = getLoansLoanIdResponse.getDelinquencyRange();
            assertNull(delinquencyRange);
            log.info("Loan Delinquency Range is null {}", (delinquencyRange == null));

            loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, Double.valueOf("800.00"));

            // N+1 Scenario -- Remains the same periods number
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);
            getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
            log.info("Loan with {} periods", getLoanRepaymentSchedule.getPeriods().size());
            assertEquals(3, getLoanRepaymentSchedule.getPeriods().size());
            getLoanRepaymentSchedule = getLoansLoanIdResponse.getRepaymentSchedule();
            for (GetLoansLoanIdRepaymentPeriod period : getLoanRepaymentSchedule.getPeriods()) {
                if (period.getPeriod() != null && period.getPeriod() == 2) {
                    log.info("Period number {} for due date {} and totalDueForPeriod {}", period.getPeriod(), period.getDueDate(),
                            period.getTotalDueForPeriod());
                    assertEquals(Double.valueOf("800.00"), Utils.getDoubleValue(period.getPrincipalDue()));
                }
            }

            // Move the Business date few days to get Collection data
            LocalDate businessDate = todaysDate.plusDays(4);
            updateBusinessDate(businessDate);
            log.info("Current Business date {}", businessDate);

            // Get loan details expecting to have a delinquency classification
            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            DelinquencyBucketsHelper.evaluateLoanCollectionData(getLoansLoanIdResponse, 4, Double.valueOf("800.00"));
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargebackWithLoanOverpaidToLoanActive(LoanProductTestBuilder loanProductTestBuilder) {
        // Client and Loan account creation
        final Integer loanId = createAccounts(15, 1, true, loanProductTestBuilder);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);

        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

        Float amount = Float.valueOf("1100.00");
        PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
        assertNotNull(loanIdTransactionsResponse);
        final Long transactionId = loanIdTransactionsResponse.getResourceId();

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.overpaid");

        reviewLoanTransactionRelations(loanId, transactionId, 0, Double.valueOf("0.00"));

        final Long chargebackTransactionId = applyChargeback(loanId, transactionId, "200.00", 0);

        reviewLoanTransactionRelations(loanId, transactionId, 1, Double.valueOf("0.00"));
        reviewLoanTransactionRelations(loanId, chargebackTransactionId, 0, Double.valueOf("100.00"));

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.active");

        loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, Double.valueOf("100.00"));

        assertNotNull(getLoansLoanIdResponse.getTimeline());
        assertEquals(getLoansLoanIdResponse.getTimeline().getExpectedMaturityDate(),
                getLoansLoanIdResponse.getTimeline().getActualMaturityDate());

        verifyTRJournalEntries(chargebackTransactionId, //
                credit(fundSource, 200.0), //
                debit(loansReceivableAccount, 100.0), //
                debit(overpaymentAccount, 100.0) //
        );

        final DelinquencyRangeData delinquencyRange = getLoansLoanIdResponse.getDelinquencyRange();
        assertNull(delinquencyRange);
        log.info("Loan Delinquency Range is null {}", (delinquencyRange == null));
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargebackWithLoanOverpaidToLoanClose(LoanProductTestBuilder loanProductTestBuilder) {
        // Client and Loan account creation
        final Integer loanId = createAccounts(15, 1, false, loanProductTestBuilder);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);

        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

        Float amount = Float.valueOf("1100.00");
        PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
        assertNotNull(loanIdTransactionsResponse);
        final Long transactionId = loanIdTransactionsResponse.getResourceId();

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.overpaid");

        reviewLoanTransactionRelations(loanId, transactionId, 0, Double.valueOf("0.00"));

        final Long chargebackTransactionId = applyChargeback(loanId, transactionId, "100.00", 0);

        reviewLoanTransactionRelations(loanId, transactionId, 1, Double.valueOf("0.00"));
        reviewLoanTransactionRelations(loanId, chargebackTransactionId, 0, Double.valueOf("0.00"));

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.closed.obligations.met");

        loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, Double.valueOf("0.00"));
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyLoanTransactionChargebackWithLoanOverpaidToKeepAsLoanOverpaid(LoanProductTestBuilder loanProductTestBuilder) {
        // Client and Loan account creation
        final Integer loanId = createAccounts(15, 1, true, loanProductTestBuilder);

        GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);

        loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

        Float amount = Float.valueOf("1100.00");
        PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
        assertNotNull(loanIdTransactionsResponse);
        final Long transactionId = loanIdTransactionsResponse.getResourceId();

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.overpaid");

        reviewLoanTransactionRelations(loanId, transactionId, 0, Double.valueOf("0.00"));

        DelinquencyRangeData delinquencyRange = getLoansLoanIdResponse.getDelinquencyRange();
        assertNull(delinquencyRange);
        log.info("Loan Delinquency Range is null {}", (delinquencyRange == null));
        final Long chargebackTransactionId = applyChargeback(loanId, transactionId, "50.00", 0);
        reviewLoanTransactionRelations(loanId, transactionId, 1, Double.valueOf("0.00"));
        reviewLoanTransactionRelations(loanId, chargebackTransactionId, 0, Double.valueOf("0.00"));

        getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
        assertNotNull(getLoansLoanIdResponse);
        loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.overpaid");

        delinquencyRange = getLoansLoanIdResponse.getDelinquencyRange();
        assertNull(delinquencyRange);
        log.info("Loan Delinquency Range is null {}", (delinquencyRange == null));

        loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, Double.valueOf("0.00"));

        verifyTRJournalEntries(chargebackTransactionId, //
                credit(fundSource, 50.0), //
                debit(overpaymentAccount, 50.0) //
        );
    }

    @ParameterizedTest
    @MethodSource("loanProductFactory")
    public void applyMultipleLoanTransactionChargeback(LoanProductTestBuilder loanProductTestBuilder) {
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            final LocalDate todaysDate = Utils.getLocalDateOfTenant();
            updateBusinessDate(todaysDate);
            log.info("Current Business date {}", todaysDate);

            // Client and Loan account creation
            final Integer loanId = createAccounts(15, 1, false, loanProductTestBuilder);

            GetLoansLoanIdResponse getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);

            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            Float amount = Float.valueOf(amountVal);
            PostLoansLoanIdTransactionsResponse loanIdTransactionsResponse = makeRepayment(operationDate, amount, loanId);
            assertNotNull(loanIdTransactionsResponse);
            final Long transactionId = loanIdTransactionsResponse.getResourceId();

            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            assertNotNull(getLoansLoanIdResponse);
            loanTransactionHelper.validateLoanStatus(getLoansLoanIdResponse, "loanStatusType.closed.obligations.met");

            // First round, empty array
            reviewLoanTransactionRelations(loanId, transactionId, 0, Double.valueOf("0.00"));

            applyChargeback(loanId, transactionId, "200.00", 0);

            Double expectedAmount = Double.valueOf("200.00");
            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, expectedAmount);

            loanTransactionHelper.evaluateLoanSummaryAdjustments(getLoansLoanIdResponse, expectedAmount);
            loanTransactionHelper.printDelinquencyData(getLoansLoanIdResponse);
            DelinquencyBucketsHelper.evaluateLoanCollectionData(getLoansLoanIdResponse, 0, Double.valueOf("0.00"));

            // Second round, array size equal to 1
            reviewLoanTransactionRelations(loanId, transactionId, 1, Double.valueOf("0.00"));

            applyChargeback(loanId, transactionId, "300.00", 1);

            expectedAmount = Double.valueOf("500.00");
            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, expectedAmount);

            loanTransactionHelper.evaluateLoanSummaryAdjustments(getLoansLoanIdResponse, expectedAmount);
            DelinquencyBucketsHelper.evaluateLoanCollectionData(getLoansLoanIdResponse, 0, Double.valueOf("0.00"));

            // Third round, array size equal to 2
            reviewLoanTransactionRelations(loanId, transactionId, 2, Double.valueOf("0.00"));

            applyChargeback(loanId, transactionId, "500.00", 0);

            expectedAmount = Double.valueOf("1000.00");
            getLoansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanId.longValue());
            loanTransactionHelper.validateLoanPrincipalOustandingBalance(getLoansLoanIdResponse, expectedAmount);

            loanTransactionHelper.evaluateLoanSummaryAdjustments(getLoansLoanIdResponse, expectedAmount);
            loanTransactionHelper.printRepaymentSchedule(getLoansLoanIdResponse);

            DelinquencyBucketsHelper.evaluateLoanCollectionData(getLoansLoanIdResponse, 0, Double.valueOf("0.00"));
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    @Nested
    public class ProgressiveInterestBearingLoanWithInterestRecalculationTest {

        Long applyApproveDisburseLoan(Long loanProductId) {
            AtomicReference<Long> loanIdRef = new AtomicReference<>();
            runAt("1 January 2024", () -> {
                Long loanId = applyAndApproveProgressiveLoan(clientId, loanProductId, "1 January 2024", 100.0, 7.0, 6, null);
                loanIdRef.set(loanId);
                disburseLoan(loanId, BigDecimal.valueOf(100.0), "01 January 2024");
            });
            return loanIdRef.get();
        }

        List<CreditAllocationData> chargebackCreditAllocationOrders(List<String> allocationIds) {
            List<CreditAllocationOrder> creditAllocationOrders = new ArrayList<>(allocationIds.size());
            for (int i = 0; i < allocationIds.size(); i++) {
                String allocationId = allocationIds.get(i);
                creditAllocationOrders.add(new CreditAllocationOrder().order(i + 1).creditAllocationRule(allocationId));
            }
            return List.of(new CreditAllocationData().transactionType("CHARGEBACK").creditAllocationOrder(creditAllocationOrders));
        }

        @Nested
        public class WithoutChargebackAllocation {

            final PostLoanProductsResponse loanProductWithoutChargebackAllocation = loanProductHelper
                    .createLoanProduct(create4IProgressive().isInterestRecalculationEnabled(true).daysInYearType(DaysInYearType.DAYS_360)
                            .daysInMonthType(DaysInMonthType.DAYS_30));

            @Test
            public void testS1FullChargebackBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(loanProductWithoutChargebackAllocation.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    Long repaymentId = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01).getResourceId();
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                    addChargebackForLoan(loanId, repaymentId, 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(33.53, 0.49, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(17.0, 0.10, "01 July 2024") //
                    ); //
                    Long prepayId = verifyPrepayAmountByRepayment(loanId, "1 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, prepayId, "1 March 2024");
                    GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails(loanId);
                    verifyLoanStatus(loanDetails, LoanStatus.ACTIVE);
                });
            }

            @Test
            public void testS2AndS3PartialChargebackThenFullChargebackBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(loanProductWithoutChargebackAllocation.getResourceId());
                AtomicReference<Long> repaymentFebruaryRef = new AtomicReference<>();
                runAt("1 February 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 February 2024", 17.01);
                    repaymentFebruaryRef.set(repayment.getResourceId());
                });
                runAt("1 March 2024", () -> {
                    Long repaymentId = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01).getResourceId();
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                    addChargebackForLoan(loanId, repaymentFebruaryRef.get(), 15.0);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(31.53, 0.48, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.99, 0.10, "01 July 2024") //
                    ); //
                    addChargebackForLoan(loanId, repaymentId, 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(48.44, 0.58, "01 April 2024"), //
                            unpaidInstallment(16.71, 0.30, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(17.10, 0.10, "01 July 2024") //
                    ); //
                    Long prepayId = verifyPrepayAmountByRepayment(loanId, "1 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, prepayId, "1 March 2024");
                });
            }

            @Test
            public void testS4FullChargebackMiddleOfRepaymentPeriodBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(loanProductWithoutChargebackAllocation.getResourceId());
                AtomicReference<Long> repaymentMarchId = new AtomicReference<>();
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    repaymentMarchId
                            .set(loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01).getResourceId());
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                });
                runAt("15 March 2024", () -> {
                    addChargebackForLoan(loanId, repaymentMarchId.get(), 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(33.57, 0.45, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.96, 0.10, "01 July 2024") //
                    ); //
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "15 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "15 March 2024");
                });
            }

            @Test
            public void testS7ChargebacksOnMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(loanProductWithoutChargebackAllocation.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01);
                });
                runAt("1 April 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 April 2024", 17.01);
                });
                runAt("1 May 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 May 2024", 17.01);
                });
                AtomicReference<Long> repaymentJuneRef = new AtomicReference<>();
                runAt("1 June 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 June 2024", 17.01);
                    repaymentJuneRef.set(repayment.getResourceId());
                });
                AtomicReference<Long> repaymentJulyRef = new AtomicReference<>();
                runAt("1 July 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 July 2024", 17.00);
                    repaymentJulyRef.set(repayment.getResourceId());
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                    addChargebackForLoan(loanId, repaymentJulyRef.get(), 17.00);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            installment(33.9, 0.10, 17.0, false, "01 July 2024") //
                    ); //
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "01 July 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "01 July 2024");
                });

            }

            @Test
            public void testS5AndS6ChargebacksAfterMaturityDateVerifyNPlus1ThPeriod() {
                final Long loanId = applyApproveDisburseLoan(loanProductWithoutChargebackAllocation.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01);
                });
                runAt("1 April 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 April 2024", 17.01);
                });
                runAt("1 May 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 May 2024", 17.01);
                });
                AtomicReference<Long> repaymentJuneRef = new AtomicReference<>();
                runAt("1 June 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 June 2024", 17.01);
                    repaymentJuneRef.set(repayment.getResourceId());
                });
                AtomicReference<Long> repaymentJulyRef = new AtomicReference<>();
                runAt("1 July 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 July 2024", 17.00);
                    repaymentJulyRef.set(repayment.getResourceId());
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                });
                runAt("15 July 2024", () -> {
                    addChargebackForLoan(loanId, repaymentJuneRef.get(), 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024"), //
                            unpaidInstallment(17.01, 0.0, "15 July 2024") //
                    ); //
                });
                runAt("30 July 2024", () -> {
                    addChargebackForLoan(loanId, repaymentJulyRef.get(), 17.00);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024"), //
                            unpaidInstallment(34.01, 0.0, "30 July 2024") //
                    ); //
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "30 July 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "30 July 2024");
                });

            }
        }

        @Nested
        public class WithChargebackAllocationPrincipalInterestFeesPenalties {

            final PostLoanProductsResponse loanProductWithChargebackAllocationPrincipalInterestFeesPenalties = loanProductHelper
                    .createLoanProduct(create4IProgressive().isInterestRecalculationEnabled(true).daysInYearType(DaysInYearType.DAYS_360)
                            .daysInMonthType(DaysInMonthType.DAYS_30)
                            .creditAllocation(chargebackCreditAllocationOrders(List.of("PRINCIPAL", "PENALTY", "FEE", "INTEREST"))));

            @Test
            public void testS1FullChargebackBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationPrincipalInterestFeesPenalties.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    Long repaymentId = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01).getResourceId();
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    addChargebackForLoan(loanId, repaymentId, 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(33.04, 0.98, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(17.0, 0.10, "01 July 2024") //
                    ); //
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Chargeback", "01 March 2024", 83.57, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    Long prepayId = verifyPrepayAmountByRepayment(loanId, "1 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, prepayId, "1 March 2024");
                    GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails(loanId);
                    verifyLoanStatus(loanDetails, LoanStatus.ACTIVE);
                });
            }

            @Test
            public void testS2AndS3PartialChargebackThenFullChargebackBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationPrincipalInterestFeesPenalties.getResourceId());
                AtomicReference<Long> repaymentFebruaryRef = new AtomicReference<>();
                runAt("1 February 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 February 2024", 17.01);
                    repaymentFebruaryRef.set(repayment.getResourceId());
                });
                runAt("1 March 2024", () -> {
                    runAt("1 March 2024", () -> {
                        Long repaymentMarchId = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01)
                                .getResourceId();
                        verifyRepaymentSchedule(loanId, //
                                installment(100.0, null, "01 January 2024"), //
                                fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                                fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                                unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                                unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                                unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                                unpaidInstallment(16.9, 0.10, "01 July 2024") //
                        ); //
                        verifyTransactions(loanId,
                                new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                        addChargebackForLoan(loanId, repaymentFebruaryRef.get(), 15.0);
                        verifyTransactions(loanId,
                                new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(15.00, "Chargeback", "01 March 2024", 82.05, 15.0, 0.0, 0.0, 0.0, 0.0, 0.0, false));
                        verifyRepaymentSchedule(loanId, //
                                installment(100.0, null, "01 January 2024"), //
                                fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                                fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                                unpaidInstallment(31.53, 0.48, "01 April 2024"), //
                                unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                                unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                                unpaidInstallment(16.99, 0.10, "01 July 2024") //
                        ); //

                        addChargebackForLoan(loanId, repaymentMarchId, 17.01);
                        verifyTransactions(loanId,
                                new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(15.00, "Chargeback", "01 March 2024", 82.05, 15.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                                new TransactionExt(17.01, "Chargeback", "01 March 2024", 98.57, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                        verifyRepaymentSchedule(loanId, //
                                installment(100.0, null, "01 January 2024"), //
                                fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                                fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                                unpaidInstallment(47.96, 1.06, "01 April 2024"), //
                                unpaidInstallment(16.71, 0.30, "01 May 2024"), //
                                unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                                unpaidInstallment(17.09, 0.10, "01 July 2024") //
                        ); //
                        Long prepayId = verifyPrepayAmountByRepayment(loanId, "1 March 2024");
                        loanTransactionHelper.reverseLoanTransaction(loanId, prepayId, "1 March 2024");
                    });
                });
            }

            @Test
            public void testS4FullChargebackMiddleOfRepaymentPeriodBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationPrincipalInterestFeesPenalties.getResourceId());
                AtomicReference<Long> repaymentMarchId = new AtomicReference<>();
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    repaymentMarchId
                            .set(loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01).getResourceId());
                });
                runAt("15 March 2024", () -> {
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    );
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    addChargebackForLoan(loanId, repaymentMarchId.get(), 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(33.09, 0.93, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.95, 0.10, "01 July 2024") //
                    ); //
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Chargeback", "15 March 2024", 83.57, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "15 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "15 March 2024");
                });
            }

            @Test
            public void testS5AndS6ChargebacksAfterMaturityDateVerifyNPlus1ThPeriod() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationPrincipalInterestFeesPenalties.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01);
                });
                runAt("1 April 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 April 2024", 17.01);
                });
                runAt("1 May 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 May 2024", 17.01);
                });
                AtomicReference<Long> repaymentJuneRef = new AtomicReference<>();
                runAt("1 June 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 June 2024", 17.01);
                    repaymentJuneRef.set(repayment.getResourceId());
                });
                AtomicReference<Long> repaymentJulyRef = new AtomicReference<>();
                runAt("1 July 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 July 2024", 17.00);
                    repaymentJulyRef.set(repayment.getResourceId());
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                });
                runAt("15 July 2024", () -> {
                    addChargebackForLoan(loanId, repaymentJuneRef.get(), 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024"), //
                            unpaidInstallment(16.81, 0.2, "15 July 2024") //
                    ); //
                });
                runAt("30 July 2024", () -> {
                    addChargebackForLoan(loanId, repaymentJulyRef.get(), 17.00);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024"), //
                            unpaidInstallment(33.71, 0.3, "30 July 2024") //
                    ); //
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "30 July 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "30 July 2024");
                });

            }
        }

        @Nested
        public class WithChargebackAllocationInterestFeesPenaltiesPrincipal {

            final PostLoanProductsResponse loanProductWithChargebackAllocationInterestFeesPenaltiesPrincipal = loanProductHelper
                    .createLoanProduct(create4IProgressive().isInterestRecalculationEnabled(true).daysInYearType(DaysInYearType.DAYS_360)
                            .daysInMonthType(DaysInMonthType.DAYS_30)
                            .creditAllocation(chargebackCreditAllocationOrders(List.of("PENALTY", "FEE", "INTEREST", "PRINCIPAL"))));

            @Test
            public void testS1FullChargebackBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationInterestFeesPenaltiesPrincipal.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    Long repaymentId = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01).getResourceId();
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    addChargebackForLoan(loanId, repaymentId, 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(33.04, 0.98, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(17.0, 0.10, "01 July 2024") //
                    ); //
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Chargeback", "01 March 2024", 83.57, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    Long prepayId = verifyPrepayAmountByRepayment(loanId, "1 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, prepayId, "1 March 2024");
                    GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails(loanId);
                    verifyLoanStatus(loanDetails, LoanStatus.ACTIVE);
                });
            }

            @Test
            public void testS2AndS3PartialChargebackThenFullChargebackBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationInterestFeesPenaltiesPrincipal.getResourceId());
                AtomicReference<Long> repaymentFebruaryRef = new AtomicReference<>();
                runAt("1 February 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 February 2024", 17.01);
                    repaymentFebruaryRef.set(repayment.getResourceId());
                });
                runAt("1 March 2024", () -> {
                    Long repaymentMarchId = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01)
                            .getResourceId();
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    addChargebackForLoan(loanId, repaymentFebruaryRef.get(), 15.0);
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(15.00, "Chargeback", "01 March 2024", 81.47, 14.42, 0.58, 0.0, 0.0, 0.0, 0.0, false));
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(30.95, 1.06, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.99, 0.10, "01 July 2024") //
                    ); //

                    addChargebackForLoan(loanId, repaymentMarchId, 17.01);
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(15.00, "Chargeback", "01 March 2024", 81.47, 14.42, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Chargeback", "01 March 2024", 97.99, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(47.38, 1.64, "01 April 2024"), //
                            unpaidInstallment(16.71, 0.30, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(17.09, 0.10, "01 July 2024") //
                    ); //
                    Long prepayId = verifyPrepayAmountByRepayment(loanId, "1 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, prepayId, "1 March 2024");
                });
            }

            @Test
            public void testS4FullChargebackMiddleOfRepaymentPeriodBeforeMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationInterestFeesPenaltiesPrincipal.getResourceId());
                AtomicReference<Long> repaymentMarchId = new AtomicReference<>();
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    repaymentMarchId
                            .set(loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01).getResourceId());
                });
                runAt("15 March 2024", () -> {
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(16.62, 0.39, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.9, 0.10, "01 July 2024") //
                    );
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    addChargebackForLoan(loanId, repaymentMarchId.get(), 17.01);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            unpaidInstallment(33.09, 0.93, "01 April 2024"), //
                            unpaidInstallment(16.72, 0.29, "01 May 2024"), //
                            unpaidInstallment(16.81, 0.20, "01 June 2024"), //
                            unpaidInstallment(16.95, 0.10, "01 July 2024") //
                    ); //
                    verifyTransactions(loanId,
                            new TransactionExt(100.0, "Disbursement", "01 January 2024", 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 February 2024", 83.57, 16.43, 0.58, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Repayment", "01 March 2024", 67.05, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false),
                            new TransactionExt(17.01, "Chargeback", "15 March 2024", 83.57, 16.52, 0.49, 0.0, 0.0, 0.0, 0.0, false));
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "15 March 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "15 March 2024");
                });
            }

            @Test
            public void testS5AndS6ChargebacksAfterMaturityDateVerifyNPlus1ThPeriod() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationInterestFeesPenaltiesPrincipal.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01);
                });
                runAt("1 April 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 April 2024", 17.01);
                });
                runAt("1 May 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 May 2024", 17.01);
                });
                AtomicReference<Long> repaymentJuneRef = new AtomicReference<>();
                runAt("1 June 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 June 2024", 17.01);
                    repaymentJuneRef.set(repayment.getResourceId());
                });
                AtomicReference<Long> repaymentJulyRef = new AtomicReference<>();
                runAt("1 July 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 July 2024", 17.00);
                    repaymentJulyRef.set(repayment.getResourceId());
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                });
                runAt("15 July 2024", () -> {
                    addChargebackForLoan(loanId, repaymentJuneRef.get(), 17.01);
                    // TODO verify TRANSACTIONS!!!!
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024"), //
                            unpaidInstallment(16.81, 0.2, "15 July 2024") //
                    ); //
                });
                runAt("30 July 2024", () -> {
                    addChargebackForLoan(loanId, repaymentJulyRef.get(), 17.00);
                    // TODO verify TRANSACTIONS!!!!
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024"), //
                            unpaidInstallment(33.71, 0.3, "30 July 2024") //
                    ); //
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "30 July 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "30 July 2024");
                });

            }

            @Test
            public void testS7ChargebacksOnMaturityDate() {
                final Long loanId = applyApproveDisburseLoan(
                        loanProductWithChargebackAllocationInterestFeesPenaltiesPrincipal.getResourceId());
                runAt("1 February 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 February 2024", 17.01);
                });
                runAt("1 March 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 March 2024", 17.01);
                });
                runAt("1 April 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 April 2024", 17.01);
                });
                runAt("1 May 2024", () -> {
                    loanTransactionHelper.makeLoanRepayment(loanId, "Repayment", "01 May 2024", 17.01);
                });
                AtomicReference<Long> repaymentJuneRef = new AtomicReference<>();
                runAt("1 June 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 June 2024", 17.01);
                    repaymentJuneRef.set(repayment.getResourceId());
                });
                AtomicReference<Long> repaymentJulyRef = new AtomicReference<>();
                runAt("1 July 2024", () -> {
                    PostLoansLoanIdTransactionsResponse repayment = loanTransactionHelper.makeLoanRepayment(loanId, "Repayment",
                            "01 July 2024", 17.00);
                    repaymentJulyRef.set(repayment.getResourceId());
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            fullyRepaidInstallment(16.9, 0.10, "01 July 2024") //
                    ); //
                    addChargebackForLoan(loanId, repaymentJulyRef.get(), 17.00);
                    verifyRepaymentSchedule(loanId, //
                            installment(100.0, null, "01 January 2024"), //
                            fullyRepaidInstallment(16.43, 0.58, "01 February 2024"), //
                            fullyRepaidInstallment(16.52, 0.49, "01 March 2024"), //
                            fullyRepaidInstallment(16.62, 0.39, "01 April 2024"), //
                            fullyRepaidInstallment(16.72, 0.29, "01 May 2024"), //
                            fullyRepaidInstallment(16.81, 0.20, "01 June 2024"), //
                            installment(33.8, 0.20, 17.0, false, "01 July 2024") //
                    ); //
                    Long repaymentId = verifyPrepayAmountByRepayment(loanId, "01 July 2024");
                    loanTransactionHelper.reverseLoanTransaction(loanId, repaymentId, "01 July 2024");
                });
                runAt("2 July 2024", () -> {
                    executeInlineCOB(loanId);
                });

            }
        }
    }

    private Integer createAccounts(final Integer daysToSubtract, final Integer numberOfRepayments, final boolean withJournalEntries,
            LoanProductTestBuilder loanProductTestBuilder) {
        // Delinquency Bucket
        final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
        final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);

        // Client and Loan account creation
        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getResourceId().intValue();
        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(loanTransactionHelper,
                delinquencyBucketId, withJournalEntries, loanProductTestBuilder);
        assertNotNull(getLoanProductsProductResponse);
        log.info("Loan Product Bucket Name: {}", getLoanProductsProductResponse.getDelinquencyBucket().getName());
        assertEquals(getLoanProductsProductResponse.getDelinquencyBucket().getName(), delinquencyBucket.getName());

        // Older date to have more than one overdue installment
        final LocalDate transactionDate = this.todaysDate.minusDays(daysToSubtract + (30 * (numberOfRepayments - 1)));
        String operationDate = Utils.dateFormatter.format(transactionDate);

        return createLoanAccount(loanTransactionHelper, clientId.toString(), getLoanProductsProductResponse.getId().toString(),
                operationDate, amountVal, numberOfRepayments.toString(), loanProductTestBuilder.getTransactionProcessingStrategyCode());
    }

    private GetLoanProductsProductIdResponse createLoanProduct(final LoanTransactionHelper loanTransactionHelper,
            final Long delinquencyBucketId, final boolean withJournalEntries, LoanProductTestBuilder loanProductTestBuilder) {
        final HashMap<String, Object> loanProductMap;
        if (withJournalEntries) {
            loanProductMap = loanProductTestBuilder
                    .withFullAccountingConfig(ACCRUAL_PERIODIC,
                            LoanProductTestBuilder.FullAccountingConfig.builder().fundSourceAccountId(fundSource.getAccountID().longValue())//
                                    .loanPortfolioAccountId(loansReceivableAccount.getAccountID().longValue())//
                                    .transfersInSuspenseAccountId(suspenseAccount.getAccountID().longValue())//
                                    .interestOnLoanAccountId(interestIncomeAccount.getAccountID().longValue())//
                                    .incomeFromFeeAccountId(feeIncomeAccount.getAccountID().longValue())//
                                    .incomeFromPenaltyAccountId(penaltyIncomeAccount.getAccountID().longValue())//
                                    .incomeFromRecoveryAccountId(recoveriesAccount.getAccountID().longValue())//
                                    .writeOffAccountId(writtenOffAccount.getAccountID().longValue())//
                                    .overpaymentLiabilityAccountId(overpaymentAccount.getAccountID().longValue())//
                                    .receivableInterestAccountId(interestReceivableAccount.getAccountID().longValue())//
                                    .receivableFeeAccountId(interestReceivableAccount.getAccountID().longValue())//
                                    .receivablePenaltyAccountId(interestReceivableAccount.getAccountID().longValue()).build())
                    .build(null, delinquencyBucketId);
        } else {
            loanProductMap = loanProductTestBuilder.build(null, delinquencyBucketId);
        }
        final Integer loanProductId = postLoanProduct(Utils.convertToJson(loanProductMap));
        return ok(fineractClient().loanProducts.retrieveOneLoanProduct(loanProductId.longValue()));
    }

    private Integer createLoanAccount(final LoanTransactionHelper loanTransactionHelper, final String clientId, final String loanProductId,
            final String operationDate, final String principalAmount, final String numberOfRepayments, final String repaymentStrategy) {
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(principalAmount)
                .withLoanTermFrequency(numberOfRepayments).withLoanTermFrequencyAsMonths().withNumberOfRepayments(numberOfRepayments)
                .withRepaymentEveryAfter("1").withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("0") //
                .withExpectedDisbursementDate(operationDate) //
                .withInterestTypeAsDecliningBalance() //
                .withSubmittedOnDate(operationDate) //
                .withRepaymentStrategy(repaymentStrategy) //
                .build(clientId, loanProductId, null);
        final Integer loanId = postLoanApplication(loanApplicationJSON);
        loanTransactionHelper.approveLoan(loanId.longValue(), new PostLoansLoanIdRequest().dateFormat("dd MMMM yyyy").locale("en")
                .approvedLoanAmount(new BigDecimal(principalAmount)).approvedOnDate(operationDate));
        disburseLoanWithNetDisbursalAmount(loanId, operationDate, principalAmount);
        return loanId;
    }

    private void reviewLoanTransactionRelations(final Integer loanId, final Long transactionId, final Integer expectedSize,
            final Double outstandingBalance) {
        log.info("Loan Transaction Id: {} {}", loanId, transactionId);

        GetLoansLoanIdTransactionsTransactionIdResponse getLoansTransactionResponse = loanTransactionHelper
                .getLoanTransactionDetails(loanId.longValue(), transactionId);
        log.info("Loan with {} Chargeback Transactions and balance {}", getLoansTransactionResponse.getTransactionRelations().size(),
                getLoansTransactionResponse.getOutstandingLoanBalance());
        assertNotNull(getLoansTransactionResponse);
        assertNotNull(getLoansTransactionResponse.getTransactionRelations());
        assertEquals(expectedSize, getLoansTransactionResponse.getTransactionRelations().size());
        // Outstanding amount
        assertEquals(outstandingBalance, getLoansTransactionResponse.getOutstandingLoanBalance());
    }

    private static AdvancedPaymentData createRepaymentPaymentAllocation() {
        AdvancedPaymentData advancedPaymentData = new AdvancedPaymentData();
        advancedPaymentData.setTransactionType("REPAYMENT");
        advancedPaymentData.setFutureInstallmentAllocationRule("NEXT_INSTALLMENT");

        List<PaymentAllocationOrder> paymentAllocationOrders = getPaymentAllocationOrder(PaymentAllocationType.PAST_DUE_PENALTY,
                PaymentAllocationType.PAST_DUE_FEE, PaymentAllocationType.PAST_DUE_INTEREST, PaymentAllocationType.PAST_DUE_PRINCIPAL,
                PaymentAllocationType.DUE_PENALTY, PaymentAllocationType.DUE_FEE, PaymentAllocationType.DUE_INTEREST,
                PaymentAllocationType.DUE_PRINCIPAL, PaymentAllocationType.IN_ADVANCE_PENALTY, PaymentAllocationType.IN_ADVANCE_FEE,
                PaymentAllocationType.IN_ADVANCE_PRINCIPAL, PaymentAllocationType.IN_ADVANCE_INTEREST);

        advancedPaymentData.setPaymentAllocationOrder(paymentAllocationOrders);
        return advancedPaymentData;
    }

    private static Stream<Arguments> loanProductFactory() {
        return Stream.of(Arguments.of(Named.of("DEFAULT_STRATEGY", new LoanProductTestBuilder().withRepaymentStrategy(DEFAULT_STRATEGY))),
                Arguments.of(Named.of("ADVANCED_PAYMENT_ALLOCATION_STRATEGY",
                        new LoanProductTestBuilder().withRepaymentStrategy(ADVANCED_PAYMENT_ALLOCATION_STRATEGY)
                                .withLoanScheduleType(LoanScheduleType.PROGRESSIVE)
                                .addAdvancedPaymentAllocation(createDefaultPaymentAllocation(), createRepaymentPaymentAllocation()))));
    }

}
