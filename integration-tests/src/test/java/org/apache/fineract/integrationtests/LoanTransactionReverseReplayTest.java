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

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.JournalEntryTransactionItem;
import org.apache.fineract.client.models.PostCodeValuesDataRequest;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.JournalEntryHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.junit.jupiter.api.Test;

public class LoanTransactionReverseReplayTest extends BaseLoanIntegrationTest {

    private static final String DATE_PATTERN = "dd MMMM yyyy";
    private static final Gson GSON = new JSON().getGson();
    private final BusinessDateHelper businessDateHelper = new BusinessDateHelper();
    private final DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder().appendPattern(DATE_PATTERN).toFormatter();

    /**
     * 1. Loan created and disbursed. // 2. Loan repayment on expected maturity date. // 3. Merchant issues refund
     * post-maturity. // 4. CBR the next morning (after COB) // 5. Loan repayment reverses triggering reverse-replay of
     * MIR and CBR. This also creates the additional repayment schedule. // 6. Charge added AFTER (NOT on the same day
     * of) CBR. // 7. When the COB runs on the charge date, accruals are created. //
     */
    @Test
    public void loanTransactionReverseReplayWithAdditionalInstallmentAndChargesTest() {
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("04 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            // Loan ExternalId
            String loanExternalIdStr = UUID.randomUUID().toString();

            // Client and Loan account creation

            final Integer clientId = clientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
            final GetLoanProductsProductIdResponse loanProductsProductResponse = createLoanProduct();

            final Long loanId = createLoanAccount(clientId, loanProductsProductResponse.getId(), loanExternalIdStr);

            final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat(DATE_PATTERN).transactionDate("03 October 2022").locale("en")
                            .transactionAmount(1000.0));

            loanTransactionHelper.makeMerchantIssuedRefund(loanExternalIdStr, new PostLoansLoanIdTransactionsRequest()
                    .dateFormat(DATE_PATTERN).transactionDate("04 October 2022").locale("en").transactionAmount(500.0));

            inlineLoanCOBHelper.executeInlineCOB(loanId);

            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("05 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            loanTransactionHelper.makeCreditBalanceRefund(loanExternalIdStr, new PostLoansLoanIdTransactionsRequest()
                    .dateFormat(DATE_PATTERN).transactionDate("05 October 2022").locale("en").transactionAmount(500.0));

            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("06 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            loanTransactionHelper.reverseLoanTransaction(loanExternalIdStr, repaymentTransaction.getResourceId(),
                    new PostLoansLoanIdTransactionsTransactionIdRequest().transactionDate("06 October 2022").locale("en")
                            .dateFormat(DATE_PATTERN).transactionAmount(0.0));

            LocalDate targetDate = LocalDate.of(2022, 10, 6);
            final String penaltyCharge1AddedDate = dateFormatter.format(targetDate);
            addCharge(loanId, true, 10.0, penaltyCharge1AddedDate);
            inlineLoanCOBHelper.executeInlineCOB(loanId);
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    /**
     * 1 create a loan account - approve and disburse // 2. make repayment (fully paid) // 3. add the charge greater
     * than the maturity date // 4. make 2nd payment with excess amount - the loan will move to overpaid state. // 5. Do
     * a CBR transaction // 6. reverse the 2nd payment // 7. check the repayment schedule due date. 8. add chargeback
     * for 1st repayment 9. check the repayment schedule due date //
     */
    @Test
    public void loanTransactionReverseReplayWithAdditionalInstallmentAndChargesScheduleDueDateTest() {
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("04 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            // Loan ExternalId
            String loanExternalIdStr = UUID.randomUUID().toString();

            // Client and Loan account creation

            final Integer clientId = clientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
            final GetLoanProductsProductIdResponse loanProductsProductResponse = createLoanProduct();

            final Long loanId = createLoanAccount(clientId, loanProductsProductResponse.getId(), loanExternalIdStr);

            // make repayment
            String loanTransactionExternalIdStr = UUID.randomUUID().toString();
            loanTransactionHelper.makeLoanRepayment(loanExternalIdStr, new PostLoansLoanIdTransactionsRequest().dateFormat(DATE_PATTERN)
                    .transactionDate("03 October 2022").locale("en").transactionAmount(1000.0).externalId(loanTransactionExternalIdStr));

            LocalDate targetDate = LocalDate.of(2022, 10, 10);
            final String penaltyCharge1AddedDate = dateFormatter.format(targetDate);
            addCharge(loanId, true, 10.0, penaltyCharge1AddedDate);
            inlineLoanCOBHelper.executeInlineCOB(loanId);

            GetLoansLoanIdResponse loansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanExternalIdStr);
            int lastPeriodIndex = loansLoanIdResponse.getRepaymentSchedule().getPeriods().size() - 1;
            assertEquals(LocalDate.of(2022, 10, 10),
                    loansLoanIdResponse.getRepaymentSchedule().getPeriods().get(lastPeriodIndex).getDueDate());

            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("06 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat(DATE_PATTERN).transactionDate("06 October 2022").locale("en")
                            .transactionAmount(500.0));

            loanTransactionHelper.makeCreditBalanceRefund(loanExternalIdStr, new PostLoansLoanIdTransactionsRequest()
                    .dateFormat(DATE_PATTERN).transactionDate("06 October 2022").locale("en").transactionAmount(490.0));

            loanTransactionHelper.reverseLoanTransaction(loanExternalIdStr, repaymentTransaction.getResourceId(),
                    new PostLoansLoanIdTransactionsTransactionIdRequest().transactionDate("06 October 2022").locale("en")
                            .dateFormat(DATE_PATTERN).transactionAmount(0.0));

            loansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanExternalIdStr);
            lastPeriodIndex = loansLoanIdResponse.getRepaymentSchedule().getPeriods().size() - 1;
            assertEquals(LocalDate.of(2022, 10, 10),
                    loansLoanIdResponse.getRepaymentSchedule().getPeriods().get(lastPeriodIndex).getDueDate());

            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("11 October 2022").dateFormat(DATE_PATTERN).locale("en"));
            loanTransactionHelper.chargebackLoanTransaction(loanExternalIdStr, loanTransactionExternalIdStr,
                    new PostLoansLoanIdTransactionsTransactionIdRequest().locale("en").transactionAmount(100.0).paymentTypeId(1L));

            loansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanExternalIdStr);
            lastPeriodIndex = loansLoanIdResponse.getRepaymentSchedule().getPeriods().size() - 1;
            assertEquals(LocalDate.of(2022, 10, 11),
                    loansLoanIdResponse.getRepaymentSchedule().getPeriods().get(lastPeriodIndex).getDueDate());
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    @Test
    public void loanTransactionReverseReplayWithChargeOffAndCBR() {
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("04 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            final Account assetAccount = accountHelper.createAssetAccount();
            final Account assetFeeAndPenaltyAccount = accountHelper.createAssetAccount();
            final Account incomeAccount = accountHelper.createIncomeAccount();
            final Account expenseAccount = accountHelper.createExpenseAccount();
            final Account overpaymentAccount = accountHelper.createLiabilityAccount();

            // Loan ExternalId
            String loanExternalIdStr = UUID.randomUUID().toString();

            // Client and Loan account creation

            final Integer clientId = clientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
            final String loanProductJSON = new LoanProductTestBuilder().withPrincipal("1000").withRepaymentTypeAsMonth()
                    .withRepaymentAfterEvery("1").withNumberOfRepayments("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("0")
                    .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualPrincipalPayment().withInterestTypeAsFlat()
                    .withAccountingRulePeriodicAccrual(new Account[] { assetAccount, incomeAccount, expenseAccount, overpaymentAccount })
                    .withDaysInMonth("30").withDaysInYear("365").withMoratorium("0", "0")
                    .withFeeAndPenaltyAssetAccount(assetFeeAndPenaltyAccount).build(null);
            final Long loanProductID = loanProductHelper.createLoanProduct(GSON.fromJson(loanProductJSON, PostLoanProductsRequest.class))
                    .getResourceId();

            final Long loanId = createLoanAccount(clientId, loanProductID, loanExternalIdStr);

            // set loan as chargeoff
            String randomText = Utils.randomStringGenerator("en", 5) + Utils.randomNumberGenerator(6)
                    + Utils.randomStringGenerator("is", 5);
            Long chargeOffReasonId = Calls.ok(FineractClientHelper.getFineractClient().codeValues
                    .createCodeValueByCodeName("ChargeOffReasons", new PostCodeValuesDataRequest().name(randomText).position(1)))
                    .getSubResourceId();
            String transactionExternalId = UUID.randomUUID().toString();
            PostLoansLoanIdTransactionsResponse chargeOffResponse = loanTransactionHelper.chargeOffLoan(loanId.longValue(),
                    new PostLoansLoanIdTransactionsRequest().transactionDate("03 October 2022").locale("en").dateFormat("dd MMMM yyyy")
                            .externalId(transactionExternalId).chargeOffReasonId(chargeOffReasonId));

            final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat(DATE_PATTERN).transactionDate("03 October 2022").locale("en")
                            .transactionAmount(1500.0));

            inlineLoanCOBHelper.executeInlineCOB(loanId);

            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("05 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            PostLoansLoanIdTransactionsResponse cbrTransactionResponse = loanTransactionHelper.makeCreditBalanceRefund(loanExternalIdStr,
                    new PostLoansLoanIdTransactionsRequest().dateFormat(DATE_PATTERN).transactionDate("05 October 2022").locale("en")
                            .transactionAmount(500.0));

            GetLoansLoanIdResponse loansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanExternalIdStr);
            int lastTransactionIndex = loansLoanIdResponse.getTransactions().size() - 1;
            assertEquals(500.0, Utils.getDoubleValue(loansLoanIdResponse.getTransactions().get(lastTransactionIndex).getAmount()));

            List<JournalEntryTransactionItem> journalEntriesForCBR = JournalEntryHelper
                    .retrieveJournalEntryByTransactionId("L" + cbrTransactionResponse.getResourceId().toString()).getPageItems();
            assertNotNull(journalEntriesForCBR);
            List<JournalEntryTransactionItem> cbrExpenseJournalEntries = journalEntriesForCBR.stream() //
                    .filter(journalEntry -> Long.valueOf(assetAccount.getAccountID().longValue()).equals(journalEntry.getGlAccountId())) //
                    .toList();

            List<JournalEntryTransactionItem> cbrAssetJournalEntries = journalEntriesForCBR.stream() //
                    .filter(journalEntry -> Long.valueOf(overpaymentAccount.getAccountID().longValue())
                            .equals(journalEntry.getGlAccountId())) //
                    .toList();

            assertEquals(1, cbrExpenseJournalEntries.size());
            assertEquals(1, cbrAssetJournalEntries.size());

            businessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date("06 October 2022").dateFormat(DATE_PATTERN).locale("en"));

            loanTransactionHelper.reverseLoanTransaction(loanExternalIdStr, repaymentTransaction.getResourceId(),
                    new PostLoansLoanIdTransactionsTransactionIdRequest().transactionDate("06 October 2022").locale("en")
                            .dateFormat(DATE_PATTERN).transactionAmount(0.0));

            // check if the original CBR got reversed
            journalEntriesForCBR = JournalEntryHelper
                    .retrieveJournalEntryByTransactionId("L" + cbrTransactionResponse.getResourceId().toString()).getPageItems();
            assertNotNull(journalEntriesForCBR);
            cbrExpenseJournalEntries = journalEntriesForCBR.stream() //
                    .filter(journalEntry -> Long.valueOf(assetAccount.getAccountID().longValue()).equals(journalEntry.getGlAccountId())) //
                    .toList();

            cbrAssetJournalEntries = journalEntriesForCBR.stream() //
                    .filter(journalEntry -> Long.valueOf(overpaymentAccount.getAccountID().longValue())
                            .equals(journalEntry.getGlAccountId())) //
                    .toList();

            assertEquals(2, cbrExpenseJournalEntries.size());
            assertEquals(2, cbrAssetJournalEntries.size());

            inlineLoanCOBHelper.executeInlineCOB(loanId);
            loansLoanIdResponse = loanTransactionHelper.getLoanDetails(loanExternalIdStr);
            lastTransactionIndex = loansLoanIdResponse.getTransactions().size() - 1;
            assertEquals(500.0, Utils.getDoubleValue(loansLoanIdResponse.getTransactions().get(lastTransactionIndex).getAmount()));

            // replayed CBR transaction
            GetLoansLoanIdTransactions newCBRTransaction = loansLoanIdResponse.getTransactions().stream()
                    .filter(transaction -> transaction.getType().getCreditBalanceRefund()).findFirst().orElse(null);

            assertNotNull(newCBRTransaction);

            Long newCBRTransactionId = newCBRTransaction.getId();

            journalEntriesForCBR = JournalEntryHelper.retrieveJournalEntryByTransactionId("L" + newCBRTransactionId).getPageItems();
            List<JournalEntryTransactionItem> journalEntriesForChargeOff = JournalEntryHelper
                    .retrieveJournalEntryByTransactionId("L" + chargeOffResponse.getResourceId().toString()).getPageItems();
            assertNotNull(journalEntriesForCBR);
            assertNotNull(journalEntriesForChargeOff);

            String expenseGlAccountCodeForChargeOff = journalEntriesForChargeOff.get(0).getGlAccountCode();
            String assetGlAccountCodeForChargeOff = journalEntriesForChargeOff.get(1).getGlAccountCode();

            cbrExpenseJournalEntries = journalEntriesForCBR.stream() //
                    .filter(journalEntry -> expenseGlAccountCodeForChargeOff.equals(journalEntry.getGlAccountCode())
                            && Long.valueOf(expenseAccount.getAccountID().longValue()).equals(journalEntry.getGlAccountId())) //
                    .toList();

            cbrAssetJournalEntries = journalEntriesForCBR.stream() //
                    .filter(journalEntry -> assetGlAccountCodeForChargeOff.equals(journalEntry.getGlAccountCode())
                            && Long.valueOf(assetAccount.getAccountID().longValue()).equals(journalEntry.getGlAccountId())) //
                    .toList();

            assertEquals(1, cbrExpenseJournalEntries.size());
            assertEquals(1, cbrAssetJournalEntries.size());
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    private GetLoanProductsProductIdResponse createLoanProduct() {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().build(null, null);
        final Long loanProductId = loanProductHelper
                .createLoanProduct(GSON.fromJson(Utils.convertToJson(loanProductMap), PostLoanProductsRequest.class)).getResourceId();
        return loanProductHelper.retrieveLoanProductById(loanProductId);
    }

    private Long createLoanAccount(final Integer clientID, final Long loanProductID, final String externalId) {

        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("1000").withLoanTermFrequency("1")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("1").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("0").withInterestTypeAsFlatBalance()
                .withAmortizationTypeAsEqualPrincipalPayments().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate("03 September 2022").withSubmittedOnDate("01 September 2022").withLoanType("individual")
                .withExternalId(externalId).build(clientID.toString(), loanProductID.toString(), null);

        final Long loanId = loanTransactionHelper.applyLoan(GSON.fromJson(loanApplicationJSON, PostLoansRequest.class)).getLoanId();
        loanTransactionHelper.approveLoan(loanId, new PostLoansLoanIdRequest().approvedLoanAmount(new BigDecimal("1000"))
                .approvedOnDate("02 September 2022").note("Approval NOTE").dateFormat(DATE_PATTERN).locale("en"));
        loanTransactionHelper.disburseLoan(loanId, new PostLoansLoanIdRequest().actualDisbursementDate("03 September 2022")
                .transactionAmount(new BigDecimal("1000")).note("DISBURSE NOTE").dateFormat(DATE_PATTERN).locale("en"));
        return loanId;
    }

}
