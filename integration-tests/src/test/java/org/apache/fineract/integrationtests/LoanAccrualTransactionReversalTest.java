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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.DelinquencyBucketResponse;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.PostLoansLoanIdChargesRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostRunaccrualsRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.PeriodicAccrualAccountingHelper;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoanAccrualTransactionReversalTest extends BaseLoanIntegrationTest {

    private static final String DATE_FORMAT = "dd MMMM yyyy";

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    private final DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder().appendPattern(DATE_FORMAT).toFormatter();
    private PeriodicAccrualAccountingHelper periodicAccrualAccountingHelper;
    private LoanProductHelper loanProductHelper;

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("POST v1/loans/{loanId}?command={command}")
        Response loanCommand(@Param("loanId") Integer loanId, @Param("command") String command, JsonNode body);
    }

    private static JsonNode body(Response response) {
        try (Response r = response) {
            return RAW_MAPPER.readTree(r.body().asInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode json(String raw) {
        try {
            return RAW_MAPPER.readTree(raw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void discard(Response response) {
        response.close();
    }

    @BeforeEach
    public void setup() {
        this.periodicAccrualAccountingHelper = new PeriodicAccrualAccountingHelper();
        this.loanProductHelper = new LoanProductHelper();
    }

    @Test
    public void testNoAccrualTransactionReversalForMultipleDisbursementWithChargeForLoanAccountWithNoInterestBearingSchedulePeriodicAccrual() {

        // Accounts for periodic accrual
        final Account assetAccount = this.accountHelper.createAssetAccount();
        final Account incomeAccount = this.accountHelper.createIncomeAccount();
        final Account expenseAccount = this.accountHelper.createExpenseAccount();
        final Account overpaymentAccount = this.accountHelper.createLiabilityAccount();

        // Loan ExternalId
        String loanExternalIdStr = UUID.randomUUID().toString();

        // Delinquency Bucket
        final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
        final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);

        // Client and Loan account creation

        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProductWithMultipleDisbursement(
                delinquencyBucketId, assetAccount, incomeAccount, expenseAccount, overpaymentAccount);
        assertNotNull(getLoanProductsProductResponse);

        final Integer loanId = createLoanAccount(clientId, getLoanProductsProductResponse.getId(), loanExternalIdStr);
        // 1st disbursement
        loanTransactionHelper.disburseLoan(loanId.longValue(), new PostLoansLoanIdRequest().actualDisbursementDate("03 September 2022")
                .dateFormat(DATE_FORMAT).locale("en").transactionAmount(new BigDecimal("100")).note("DISBURSE NOTE"));
        // 2nd disbursement
        loanTransactionHelper.disburseLoan(loanId.longValue(), new PostLoansLoanIdRequest().actualDisbursementDate("04 September 2022")
                .dateFormat(DATE_FORMAT).locale("en").transactionAmount(new BigDecimal("300")).note("DISBURSE NOTE"));

        // Add Charge
        Long penalty = chargesHelper.createCharges(new ChargeRequest().active(true).amount(10.0).chargeAppliesTo(1)
                .chargeCalculationType(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT).currencyCode("USD").locale("en").monthDayFormat("dd MMM")
                .name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6)).chargeTimeType(ChargesHelper.CHARGE_SPECIFIED_DUE_DATE)
                .chargePaymentMode(0).penalty(true)).getResourceId();

        LocalDate targetDate = LocalDate.of(2022, 9, 4);
        final String penaltyCharge1AddedDate = dateFormatter.format(targetDate);

        loanTransactionHelper.addLoanCharge(loanId.longValue(), new PostLoansLoanIdChargesRequest().chargeId(penalty).amount(10.0)
                .dueDate(penaltyCharge1AddedDate).dateFormat(DATE_FORMAT).locale("en_GB"));

        // Run accrual till charge date
        this.periodicAccrualAccountingHelper.runPeriodicAccrualAccounting(
                new PostRunaccrualsRequest().dateFormat(DATE_FORMAT).locale("en_GB").tillDate(penaltyCharge1AddedDate));

        // verify accrual transaction created
        checkAccrualTransaction(targetDate, 0.0f, 0.0f, 10.0f, loanId);

        // 3rd disbursement
        loanTransactionHelper.disburseLoan(loanId.longValue(), new PostLoansLoanIdRequest().actualDisbursementDate("05 September 2022")
                .dateFormat(DATE_FORMAT).locale("en").transactionAmount(new BigDecimal("600")).note("DISBURSE NOTE"));

        // verify accrual transaction exists with same date,amount and is not reversed by regeneration of repayment
        // schedule
        checkAccrualTransaction(targetDate, 0.0f, 0.0f, 10.0f, loanId);

    }

    @Test
    public void testLastAccrualTransactionReversalRecalculationForLoanAccountWithInterestBearingScheduleWithDecliningBalance() {

        try {
            // Set business date
            LocalDate currentDate = LocalDate.of(2022, 05, 8);
            final String accrualRunTillDate = dateFormatter.format(currentDate);

            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(dateFormatter.format(currentDate)).dateFormat(DATE_FORMAT).locale("en"));

            // Accounts oof periodic accrual
            final Account assetAccount = this.accountHelper.createAssetAccount();
            final Account incomeAccount = this.accountHelper.createIncomeAccount();
            final Account expenseAccount = this.accountHelper.createExpenseAccount();
            final Account overpaymentAccount = this.accountHelper.createLiabilityAccount();

            // Loan ExternalId
            String loanExternalIdStr = UUID.randomUUID().toString();

            // Client and Loan account creation

            final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();

            // create loan product
            final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProductWithInterestRecalculation(assetAccount,
                    incomeAccount, expenseAccount, overpaymentAccount);
            assertNotNull(getLoanProductsProductResponse);
            // create loan account
            final Integer loanId = createLoanAccountWithInterestRecalculation(clientId, getLoanProductsProductResponse.getId(),
                    loanExternalIdStr);
            // run accruals till business date
            this.periodicAccrualAccountingHelper.runPeriodicAccrualAccounting(
                    new PostRunaccrualsRequest().dateFormat(DATE_FORMAT).locale("en_GB").tillDate(accrualRunTillDate));
            // check amount for last accrual on business date
            checkAccrualTransaction(currentDate, 0.82f, 0.0f, 0.0f, loanId);
            // make repayment on due date
            final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("5 February 2022").locale("en")
                            .transactionAmount(106.57));
            // check previous accrual is reversed and new accrual created for same date and different amount.
            checkAccrualTransaction(currentDate, 0.71f, 0.0f, 0.0f, loanId);
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }

    }

    private Integer createLoanAccountWithInterestRecalculation(final Integer clientID, final Long loanProductID, final String externalId) {

        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("1000").withLoanTermFrequency("12")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("12").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestCalculationPeriodTypeAsDays()
                .withInterestRatePerPeriod("12").withInterestTypeAsDecliningBalance().withPrincipalGrace("2").withInterestGrace("2")
                .withExpectedDisbursementDate("05 January 2022").withSubmittedOnDate("05 January 2022").withLoanType("individual")
                .withExternalId(externalId).build(clientID.toString(), loanProductID.toString(), null);

        final Integer loanId = body(RAW.createLoan(json(loanApplicationJSON))).get("loanId").intValue();
        loanTransactionHelper.approveLoan(loanId.longValue(), new PostLoansLoanIdRequest().approvedOnDate("05 January 2022")
                .approvedLoanAmount(new BigDecimal("1000")).dateFormat(DATE_FORMAT).locale("en"));
        ObjectNode disburseBody = RAW_MAPPER.createObjectNode();
        disburseBody.put("locale", "en");
        disburseBody.put("dateFormat", DATE_FORMAT);
        disburseBody.put("actualDisbursementDate", "05 January 2022");
        disburseBody.put("netDisbursalAmount", "1000");
        disburseBody.put("note", "DISBURSE NOTE");
        discard(RAW.loanCommand(loanId, "disburse", disburseBody));
        return loanId;
    }

    private GetLoanProductsProductIdResponse createLoanProductWithInterestRecalculation(final Account... accounts) {

        final String interestRecalculationCompoundingMethod = LoanProductTestBuilder.RECALCULATION_COMPOUNDING_METHOD_NONE;
        final String rescheduleStrategyMethod = LoanProductTestBuilder.RECALCULATION_STRATEGY_REDUCE_NUMBER_OF_INSTALLMENTS;
        final String recalculationRestFrequencyType = LoanProductTestBuilder.RECALCULATION_FREQUENCY_TYPE_DAILY;
        final String recalculationRestFrequencyInterval = "0";
        final String preCloseInterestCalculationStrategy = LoanProductTestBuilder.INTEREST_APPLICABLE_STRATEGY_ON_PRE_CLOSE_DATE;
        final String recalculationCompoundingFrequencyType = null;
        final String recalculationCompoundingFrequencyInterval = null;
        final Integer recalculationCompoundingFrequencyOnDayType = null;
        final Integer recalculationCompoundingFrequencyDayOfWeekType = null;
        final Integer recalculationRestFrequencyOnDayType = null;
        final Integer recalculationRestFrequencyDayOfWeekType = null;

        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal("1000").withNumberOfRepayments("12")
                .withinterestRatePerPeriod("12").withInterestRateFrequencyTypeAsYear().withInterestTypeAsDecliningBalance()
                .withInterestCalculationPeriodTypeAsDays()
                .withInterestRecalculationDetails(interestRecalculationCompoundingMethod, rescheduleStrategyMethod,
                        preCloseInterestCalculationStrategy)
                .withInterestRecalculationRestFrequencyDetails(recalculationRestFrequencyType, recalculationRestFrequencyInterval,
                        recalculationRestFrequencyOnDayType, recalculationRestFrequencyDayOfWeekType)
                .withInterestRecalculationCompoundingFrequencyDetails(recalculationCompoundingFrequencyType,
                        recalculationCompoundingFrequencyInterval, recalculationCompoundingFrequencyOnDayType,
                        recalculationCompoundingFrequencyDayOfWeekType)
                .withAccountingRulePeriodicAccrual(accounts).build(null);

        final Integer loanProductId = body(RAW.createLoanProduct(json(loanProductJSON))).get("resourceId").intValue();
        return loanProductHelper.retrieveLoanProductById(loanProductId.longValue());
    }

    private GetLoanProductsProductIdResponse createLoanProductWithMultipleDisbursement(final Long delinquencyBucketId,
            final Account... accounts) {

        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().withPrincipal("1000").withRepaymentTypeAsMonth()
                .withRepaymentAfterEvery("1").withNumberOfRepayments("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("0")
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualPrincipalPayment().withInterestTypeAsDecliningBalance()
                .withAccountingRulePeriodicAccrual(accounts).withInterestCalculationPeriodTypeAsRepaymentPeriod(true).withDaysInMonth("30")
                .withDaysInYear("365").withMoratorium("0", "0").withMultiDisburse().withDisallowExpectedDisbursements(true)
                .build(null, delinquencyBucketId);
        final Integer loanProductId = body(RAW.createLoanProduct(json(Utils.convertToJson(loanProductMap)))).get("resourceId").intValue();
        return loanProductHelper.retrieveLoanProductById(loanProductId.longValue());
    }

    private Integer createLoanAccount(final Integer clientID, final Long loanProductID, final String externalId) {

        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("1000").withLoanTermFrequency("1")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("1").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("0").withInterestTypeAsDecliningBalance()
                .withAmortizationTypeAsEqualPrincipalPayments().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate("03 September 2022").withSubmittedOnDate("01 September 2022").withLoanType("individual")
                .withExternalId(externalId).build(clientID.toString(), loanProductID.toString(), null);

        final Integer loanId = body(RAW.createLoan(json(loanApplicationJSON))).get("loanId").intValue();
        loanTransactionHelper.approveLoan(loanId.longValue(), new PostLoansLoanIdRequest().approvedOnDate("02 September 2022")
                .approvedLoanAmount(new BigDecimal("1000")).dateFormat(DATE_FORMAT).locale("en"));
        return loanId;
    }

    private void checkAccrualTransaction(final LocalDate transactionDate, final Float interestPortion, final Float feePortion,
            final Float penaltyPortion, final Integer loanID) {

        List<GetLoansLoanIdTransactions> transactions = loanTransactionHelper.getLoanDetails(loanID.longValue()).getTransactions();
        boolean isTransactionFound = false;
        for (GetLoansLoanIdTransactions transaction : transactions) {
            boolean isAccrualTransaction = transaction.getType() != null && Boolean.TRUE.equals(transaction.getType().getAccrual());

            if (isAccrualTransaction) {
                LocalDate accrualEntryDate = transaction.getDate();

                if (DateUtils.isEqual(transactionDate, accrualEntryDate)) {
                    isTransactionFound = true;
                    assertEquals(interestPortion, toFloat(transaction.getInterestPortion()), "Mismatch in transaction amounts");
                    assertEquals(feePortion, toFloat(transaction.getFeeChargesPortion()), "Mismatch in transaction amounts");
                    assertEquals(penaltyPortion, toFloat(transaction.getPenaltyChargesPortion()), "Mismatch in transaction amounts");
                    break;
                }
            }
        }
        assertTrue(isTransactionFound, "No Accrual entries are posted");
    }

    private static Float toFloat(final BigDecimal value) {
        return value == null ? 0.0f : value.floatValue();
    }

}
