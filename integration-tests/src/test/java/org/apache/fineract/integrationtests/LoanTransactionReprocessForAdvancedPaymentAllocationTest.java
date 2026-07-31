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
import feign.RequestLine;
import feign.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.GetLoansLoanIdLoanTransactionEnumData;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.client.models.PostLoansLoanIdChargesRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.junit.jupiter.api.Test;

public class LoanTransactionReprocessForAdvancedPaymentAllocationTest extends BaseLoanIntegrationTest {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder().appendPattern(DATE_FORMAT).toFormatter();

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);
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

    @Test
    public void loanTransactionReprocessForAddChargeTest() {
        try {
            // Set business date
            LocalDate businessDate = LocalDate.of(2023, 3, 15);

            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(DATE_FORMATTER.format(businessDate)).dateFormat(DATE_FORMAT).locale("en"));

            // Accounts oof periodic accrual
            final Account assetAccount = accountHelper.createAssetAccount();
            final Account incomeAccount = accountHelper.createIncomeAccount();
            final Account expenseAccount = accountHelper.createExpenseAccount();
            final Account overpaymentAccount = accountHelper.createLiabilityAccount();

            // Loan ExternalId
            String loanExternalIdStr = UUID.randomUUID().toString();

            final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();

            final Integer loanProductId = createLoanProduct(assetAccount, incomeAccount, expenseAccount, overpaymentAccount);

            final Integer loanId = createLoanAccount(clientId, loanProductId, loanExternalIdStr);

            // disburse principal amount
            loanTransactionHelper.disburseLoan(loanId.longValue(), new PostLoansLoanIdRequest().actualDisbursementDate("15 February 2023")
                    .dateFormat(DATE_FORMAT).locale("en").transactionAmount(new BigDecimal("1000")).note("DISBURSE NOTE"));

            // add loan charge
            // apply fee
            Long feeCharge = chargesHelper.createCharges(new ChargeRequest().active(true).amount(50.0).chargeAppliesTo(1)
                    .chargeCalculationType(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT).currencyCode("USD").locale("en")
                    .monthDayFormat("dd MMM").name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6))
                    .chargeTimeType(ChargesHelper.CHARGE_SPECIFIED_DUE_DATE).chargePaymentMode(0).penalty(false)).getResourceId();

            LocalDate targetDate = LocalDate.of(2023, 2, 22);
            final String feeCharge1AddedDate = DATE_FORMATTER.format(targetDate);
            loanTransactionHelper.addLoanCharge(loanId.longValue(), new PostLoansLoanIdChargesRequest().chargeId(feeCharge).amount(50.0)
                    .dueDate(feeCharge1AddedDate).dateFormat(DATE_FORMAT).locale("en_GB"));

            // Set Loan transaction externalId for transaction getting reversed and replayed
            String loanTransactionExternalIdStr = UUID.randomUUID().toString();

            // make repayment
            final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("20 February 2023").locale("en")
                            .transactionAmount(50.0).externalId(loanTransactionExternalIdStr));

            // verify transaction amounts
            verifyTransaction(LocalDate.of(2023, 2, 20), 50.0f, 50.0f, 0.0f, 0.0f, 0.0f, loanId, "repayment");

            // add loan charge for a date later than repayment date
            // apply penalty

            targetDate = LocalDate.of(2023, 2, 22);

            Long penalty = chargesHelper.createCharges(new ChargeRequest().active(true).amount(10.0).chargeAppliesTo(1)
                    .chargeCalculationType(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT).currencyCode("USD").locale("en")
                    .monthDayFormat("dd MMM").name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6))
                    .chargeTimeType(ChargesHelper.CHARGE_SPECIFIED_DUE_DATE).chargePaymentMode(0).penalty(true)).getResourceId();

            final String penaltyCharge1AddedDate = DATE_FORMATTER.format(targetDate);

            loanTransactionHelper.addLoanCharge(loanId.longValue(), new PostLoansLoanIdChargesRequest().chargeId(penalty).amount(10.0)
                    .dueDate(penaltyCharge1AddedDate).dateFormat(DATE_FORMAT).locale("en_GB"));

            // verify no reverse replay
            GetLoansLoanIdTransactionsTransactionIdResponse getLoansTransactionResponse = loanTransactionHelper
                    .getLoanTransactionDetails((long) loanId, loanTransactionExternalIdStr);
            assertNotNull(getLoansTransactionResponse);
            assertEquals(0, getLoansTransactionResponse.getTransactionRelations().size());

            // add loan charge for a date earlier than repayment date
            targetDate = LocalDate.of(2023, 2, 18);

            Long penalty_1 = chargesHelper.createCharges(new ChargeRequest().active(true).amount(10.0).chargeAppliesTo(1)
                    .chargeCalculationType(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT).currencyCode("USD").locale("en")
                    .monthDayFormat("dd MMM").name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6))
                    .chargeTimeType(ChargesHelper.CHARGE_SPECIFIED_DUE_DATE).chargePaymentMode(0).penalty(true)).getResourceId();

            final String penaltyCharge1AddedDate_1 = DATE_FORMATTER.format(targetDate);

            loanTransactionHelper.addLoanCharge(loanId.longValue(), new PostLoansLoanIdChargesRequest().chargeId(penalty_1).amount(10.0)
                    .dueDate(penaltyCharge1AddedDate_1).dateFormat(DATE_FORMAT).locale("en_GB"));

            // verify reverse replay
            getLoansTransactionResponse = loanTransactionHelper.getLoanTransactionDetails((long) loanId, loanTransactionExternalIdStr);
            assertNotNull(getLoansTransactionResponse);
            assertEquals(1, getLoansTransactionResponse.getTransactionRelations().size());

            // verify transaction amounts
            verifyTransaction(LocalDate.of(2023, 2, 20), 50.0f, 40.0f, 0.0f, 0.0f, 10.0f, loanId, "repayment");

        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    private Integer createLoanProduct(final Account... accounts) {
        String futureInstallmentAllocationRule = "NEXT_INSTALLMENT";
        AdvancedPaymentData defaultAllocation = createDefaultPaymentAllocation(futureInstallmentAllocationRule);
        String loanProductCreateJSON = new LoanProductTestBuilder().withPrincipal("15,000.00").withNumberOfRepayments("4")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("0")
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestTypeAsDecliningBalance()
                .withAccountingRulePeriodicAccrual(accounts).withInterestCalculationPeriodTypeAsRepaymentPeriod(true)
                .addAdvancedPaymentAllocation(defaultAllocation).withLoanScheduleType(LoanScheduleType.PROGRESSIVE).withMultiDisburse()
                .withDisallowExpectedDisbursements(true).build();
        return body(RAW.createLoanProduct(json(loanProductCreateJSON))).get("resourceId").intValue();

    }

    private Integer createLoanAccount(final Integer clientID, final Integer loanProductID, final String externalId) {

        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("1000").withLoanTermFrequency("60")
                .withLoanTermFrequencyAsDays().withNumberOfRepayments("4").withRepaymentEveryAfter("15").withRepaymentFrequencyTypeAsDays()
                .withInterestRatePerPeriod("0").withInterestTypeAsFlatBalance().withAmortizationTypeAsEqualPrincipalPayments()
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod().withExpectedDisbursementDate("15 February 2023")
                .withSubmittedOnDate("15 February 2023").withLoanType("individual").withExternalId(externalId)
                .withRepaymentStrategy("advanced-payment-allocation-strategy").build(clientID.toString(), loanProductID.toString(), null);

        final Integer loanId = body(RAW.createLoan(json(loanApplicationJSON))).get("loanId").intValue();
        loanTransactionHelper.approveLoan(loanId.longValue(), new PostLoansLoanIdRequest().approvedOnDate("15 February 2023")
                .approvedLoanAmount(new BigDecimal("1000")).dateFormat(DATE_FORMAT).locale("en"));
        return loanId;
    }

    private void verifyTransaction(final LocalDate transactionDate, final Float transactionAmount, final Float principalPortion,
            final Float interestPortion, final Float feePortion, final Float penaltyPortion, final Integer loanID,
            final String transactionOfType) {
        List<GetLoansLoanIdTransactions> transactions = loanTransactionHelper.getLoanDetails(loanID.longValue()).getTransactions();
        boolean isTransactionFound = false;
        for (GetLoansLoanIdTransactions transaction : transactions) {
            boolean isTransaction = matchesType(transaction.getType(), transactionOfType);

            if (isTransaction) {
                LocalDate transactionEntryDate = transaction.getDate();

                if (transactionDate.isEqual(transactionEntryDate)) {
                    isTransactionFound = true;
                    assertEquals(transactionAmount, toFloat(transaction.getAmount()), "Mismatch in transaction amounts");
                    assertEquals(principalPortion, toFloat(transaction.getPrincipalPortion()), "Mismatch in transaction amounts");
                    assertEquals(interestPortion, toFloat(transaction.getInterestPortion()), "Mismatch in transaction amounts");
                    assertEquals(feePortion, toFloat(transaction.getFeeChargesPortion()), "Mismatch in transaction amounts");
                    assertEquals(penaltyPortion, toFloat(transaction.getPenaltyChargesPortion()), "Mismatch in transaction amounts");
                    break;
                }
            }
        }
        assertTrue(isTransactionFound, "No Transaction entries are posted");
    }

    private static boolean matchesType(final GetLoansLoanIdLoanTransactionEnumData type, final String transactionOfType) {
        if (type == null) {
            return false;
        }
        if ("repayment".equals(transactionOfType)) {
            return Boolean.TRUE.equals(type.getRepayment());
        }
        throw new IllegalArgumentException("Unsupported transaction type: " + transactionOfType);
    }

    private static Float toFloat(final BigDecimal value) {
        return value == null ? 0.0f : value.floatValue();
    }

}
