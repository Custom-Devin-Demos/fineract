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
package org.apache.fineract.integrationtests.investor.externalassetowner;

import static org.apache.fineract.client.models.ExternalTransferData.StatusEnum.BUYBACK;
import static org.apache.fineract.client.models.ExternalTransferData.StatusEnum.CANCELLED;
import static org.apache.fineract.client.models.ExternalTransferData.StatusEnum.PENDING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.ExternalAssetOwnerRequest;
import org.apache.fineract.client.models.ExternalOwnerTransferJournalEntryData;
import org.apache.fineract.client.models.ExternalTransferData;
import org.apache.fineract.client.models.GetFinancialActivityAccountsResponse;
import org.apache.fineract.client.models.PageExternalTransferData;
import org.apache.fineract.client.models.PostFinancialActivityAccountsRequest;
import org.apache.fineract.client.models.PostInitiateTransferResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.BaseLoanIntegrationTest;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.BusinessStepHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.ExternalAssetOwnerHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.FinancialActivityAccountHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;

public class ExternalAssetOwnerTransferCancelTest extends BaseLoanIntegrationTest {

    public String ownerExternalId;
    private Account assetAccount;
    private Account feePenaltyAccount;
    private Account transferAccount;
    private Account expenseAccount;
    private Account incomeAccount;
    private Account overpaymentAccount;
    private FinancialActivityAccountHelper financialActivityAccountHelper;
    private ExternalAssetOwnerHelper externalAssetOwnerHelper;
    private LocalDate todaysDate;
    private DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder().appendPattern("dd MMMM yyyy").toFormatter();
    private final Gson gson = new JSON().getGson();

    @BeforeEach
    public void setupInvestorBusinessStep() {
        externalAssetOwnerHelper = new ExternalAssetOwnerHelper();
        financialActivityAccountHelper = new FinancialActivityAccountHelper(requestSpec);

        todaysDate = Utils.getLocalDateOfTenant();
        new BusinessStepHelper().updateSteps("LOAN_CLOSE_OF_BUSINESS", "APPLY_CHARGE_TO_OVERDUE_LOANS", "LOAN_DELINQUENCY_CLASSIFICATION",
                "CHECK_LOAN_REPAYMENT_DUE", "CHECK_LOAN_REPAYMENT_OVERDUE", "UPDATE_LOAN_ARREARS_AGING", "ADD_PERIODIC_ACCRUAL_ENTRIES",
                "EXTERNAL_ASSET_OWNER_TRANSFER");

        assetAccount = accountHelper.createAssetAccount();
        feePenaltyAccount = accountHelper.createAssetAccount();
        transferAccount = accountHelper.createAssetAccount();
        expenseAccount = accountHelper.createExpenseAccount();
        incomeAccount = accountHelper.createIncomeAccount();
        overpaymentAccount = accountHelper.createLiabilityAccount();

        setProperFinancialActivity(transferAccount);
    }

    private void setProperFinancialActivity(Account transferAccount) {
        List<GetFinancialActivityAccountsResponse> financialMappings = financialActivityAccountHelper.getAllFinancialActivityAccounts();
        financialMappings.forEach(mapping -> financialActivityAccountHelper.deleteFinancialActivityAccount(mapping.getId()));
        financialActivityAccountHelper.createFinancialActivityAccount(new PostFinancialActivityAccountsRequest()
                .financialActivityId((long) AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue())
                .glAccountId((long) transferAccount.getAccountID()));
    }

    @Test
    public void successCancelSale() {
        try {
            globalConfigurationHelper.manageConfigurations(GlobalConfigurationConstants.ENABLE_AUTO_GENERATED_EXTERNAL_ID, true);
            setInitialBusinessDate("2020-03-02");
            Integer clientID = createClient();
            Integer loanID = createLoanForClient(clientID);
            addPenaltyForLoan(loanID, "10");

            PostInitiateTransferResponse saleTransferResponse = createSaleTransfer(loanID, "2020-03-02");
            validateResponse(saleTransferResponse, loanID);
            getAndValidateExternalAssetOwnerTransferByLoan(loanID,
                    ExpectedExternalTransferData.expected(PENDING, saleTransferResponse.getResourceExternalId(), "2020-03-02", "2020-03-02",
                            "9999-12-31", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")));

            PageExternalTransferData retrieveResponse = externalAssetOwnerHelper.retrieveTransfersByLoanId(loanID.longValue());
            retrieveResponse.getContent().forEach(transfer -> getAndValidateThereIsNoJournalEntriesForTransfer(transfer.getTransferId()));

            externalAssetOwnerHelper.cancelTransferByTransferExternalId(saleTransferResponse.getResourceExternalId());

            // updateBusinessDateAndExecuteCOBJob("2020-03-03");
            getAndValidateExternalAssetOwnerTransferByLoan(loanID,
                    ExpectedExternalTransferData.expected(PENDING, saleTransferResponse.getResourceExternalId(), "2020-03-02", "2020-03-02",
                            "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")),
                    ExpectedExternalTransferData.expected(CANCELLED, saleTransferResponse.getResourceExternalId(), "2020-03-02",
                            "2020-03-02", "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")));
        } finally {
            cleanUpAndRestoreBusinessDate();
        }
    }

    private void getAndValidateThereIsNoJournalEntriesForTransfer(Long transferId) {
        ExternalOwnerTransferJournalEntryData result = externalAssetOwnerHelper.retrieveJournalEntriesOfTransfer(transferId);
        assertNull(result.getJournalEntryData());
    }

    @Test
    public void saleAndBuybackOnTheSameDay() {
        try {
            globalConfigurationHelper.manageConfigurations(GlobalConfigurationConstants.ENABLE_AUTO_GENERATED_EXTERNAL_ID, true);
            setInitialBusinessDate("2020-03-02");
            Integer clientID = createClient();
            Integer loanID = createLoanForClient(clientID);

            PostInitiateTransferResponse saleTransferResponse = createSaleTransfer(loanID, "2020-03-02");
            validateResponse(saleTransferResponse, loanID);
            PostInitiateTransferResponse buybackTransferResponse = createBuybackTransfer(loanID, "2020-03-02");
            validateResponse(buybackTransferResponse, loanID);

            getAndValidateExternalAssetOwnerTransferByLoan(loanID,
                    ExpectedExternalTransferData.expected(PENDING, saleTransferResponse.getResourceExternalId(), "2020-03-02", "2020-03-02",
                            "9999-12-31", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")),
                    ExpectedExternalTransferData.expected(BUYBACK, buybackTransferResponse.getResourceExternalId(), "2020-03-02",
                            "2020-03-02", "9999-12-31", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")));
            getAndValidateThereIsNoActiveMapping(saleTransferResponse.getResourceExternalId());
            getAndValidateThereIsNoActiveMapping(buybackTransferResponse.getResourceExternalId());

            externalAssetOwnerHelper.cancelTransferByTransferExternalIdError(saleTransferResponse.getResourceExternalId());

            externalAssetOwnerHelper.cancelTransferByTransferExternalId(buybackTransferResponse.getResourceExternalId());

            getAndValidateExternalAssetOwnerTransferByLoan(loanID,
                    ExpectedExternalTransferData.expected(PENDING, saleTransferResponse.getResourceExternalId(), "2020-03-02", "2020-03-02",
                            "9999-12-31", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")),
                    ExpectedExternalTransferData.expected(BUYBACK, buybackTransferResponse.getResourceExternalId(), "2020-03-02",
                            "2020-03-02", "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")),
                    ExpectedExternalTransferData.expected(CANCELLED, buybackTransferResponse.getResourceExternalId(), "2020-03-02",
                            "2020-03-02", "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")));

            externalAssetOwnerHelper.cancelTransferByTransferExternalId(saleTransferResponse.getResourceExternalId());

            getAndValidateExternalAssetOwnerTransferByLoan(loanID,
                    ExpectedExternalTransferData.expected(PENDING, saleTransferResponse.getResourceExternalId(), "2020-03-02", "2020-03-02",
                            "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")),
                    ExpectedExternalTransferData.expected(BUYBACK, buybackTransferResponse.getResourceExternalId(), "2020-03-02",
                            "2020-03-02", "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")),
                    ExpectedExternalTransferData.expected(CANCELLED, buybackTransferResponse.getResourceExternalId(), "2020-03-02",
                            "2020-03-02", "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")),
                    ExpectedExternalTransferData.expected(CANCELLED, saleTransferResponse.getResourceExternalId(), "2020-03-02",
                            "2020-03-02", "2020-03-02", false, new BigDecimal("15767.420000"), new BigDecimal("15000.000000"),
                            new BigDecimal("757.420000"), new BigDecimal("10.000000"), new BigDecimal("0.000000"),
                            new BigDecimal("0.000000")));
            getAndValidateThereIsNoActiveMapping((long) loanID);
        } finally {
            cleanUpAndRestoreBusinessDate();
        }
    }

    private PostInitiateTransferResponse createSaleTransfer(Integer loanID, String settlementDate) {
        String transferExternalId = UUID.randomUUID().toString();
        ownerExternalId = UUID.randomUUID().toString();
        return createSaleTransfer(loanID, settlementDate, transferExternalId, ownerExternalId, "1.0");
    }

    private PostInitiateTransferResponse createSaleTransfer(Integer loanID, String settlementDate, String transferExternalId,
            String ownerExternalId, String purchasePriceRatio) {
        PostInitiateTransferResponse saleResponse = externalAssetOwnerHelper.initiateTransferByLoanId(loanID.longValue(), "sale",
                new ExternalAssetOwnerRequest().settlementDate(settlementDate).dateFormat("yyyy-MM-dd").locale("en")
                        .transferExternalId(transferExternalId).ownerExternalId(ownerExternalId).purchasePriceRatio(purchasePriceRatio));
        assertEquals(transferExternalId, saleResponse.getResourceExternalId());
        return saleResponse;
    }

    private PostInitiateTransferResponse createBuybackTransfer(Integer loanID, String settlementDate) {
        String transferExternalId = UUID.randomUUID().toString();
        PostInitiateTransferResponse saleResponse = externalAssetOwnerHelper.initiateTransferByLoanId(loanID.longValue(), "buyback",
                new ExternalAssetOwnerRequest().settlementDate(settlementDate).dateFormat("yyyy-MM-dd").locale("en")
                        .transferExternalId(transferExternalId));
        assertEquals(transferExternalId, saleResponse.getResourceExternalId());
        return saleResponse;
    }

    private void addPenaltyForLoan(Integer loanID, String amount) {
        // Add Charge Penalty
        Long penalty1LoanChargeId = addCharge(loanID.longValue(), true, Double.parseDouble(amount), "02 March 2020");
        assertNotNull(penalty1LoanChargeId);
    }

    private void setInitialBusinessDate(String date) {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                new PutGlobalConfigurationsRequest().enabled(true));
        setBusinessDate(LocalDate.parse(date));
    }

    private void setBusinessDate(LocalDate date) {
        BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                .date(dateFormatter.format(date)).dateFormat("dd MMMM yyyy").locale("en"));
    }

    private void cleanUpAndRestoreBusinessDate() {
        setBusinessDate(todaysDate);
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                new PutGlobalConfigurationsRequest().enabled(false));
    }

    @NonNull
    private Integer createClient() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        Assertions.assertNotNull(clientID);
        return clientID.intValue();
    }

    @NonNull
    private Integer createLoanForClient(Integer clientID) {
        Long overdueFeeChargeId = createOverdueFeeCharge("1");
        Assertions.assertNotNull(overdueFeeChargeId);

        Integer loanProductID = createLoanProduct(overdueFeeChargeId.toString());
        Assertions.assertNotNull(loanProductID);

        Integer loanID = applyForLoanApplication(clientID.toString(), loanProductID.toString(), "1 March 2020");
        Assertions.assertNotNull(loanID);

        HashMap<String, Object> loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        loanTransactionHelper.approveLoan(loanID.longValue(),
                new PostLoansLoanIdRequest().approvedOnDate("01 March 2020").dateFormat("dd MMMM yyyy").locale("en").note("Approval NOTE"));
        loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);

        loanTransactionHelper.disburseLoan(loanID.longValue(), new PostLoansLoanIdRequest().actualDisbursementDate("02 March 2020")
                .dateFormat("dd MMMM yyyy").locale("en").note("DISBURSE NOTE"));
        loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);
        return loanID;
    }

    private Long createOverdueFeeCharge(String penaltyPercentageAmount) {
        // Loan overdue-installment penalty, percentage of amount and interest
        ChargeRequest chargeRequest = new ChargeRequest().active(true).amount(Double.parseDouble(penaltyPercentageAmount))
                .chargeAppliesTo(1).currencyCode("USD").locale("en").monthDayFormat("dd MMM")
                .name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6)).penalty(true).chargePaymentMode(0).chargeTimeType(9)
                .chargeCalculationType(3);
        return chargesHelper.createCharges(chargeRequest).getResourceId();
    }

    private Integer createLoanProduct(final String chargeId) {

        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal("15000.00").withNumberOfRepayments("4")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("1")
                .withAccountingRulePeriodicAccrual(new Account[] { assetAccount, expenseAccount, incomeAccount, overpaymentAccount })
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestTypeAsDecliningBalance()
                .withFeeAndPenaltyAssetAccount(feePenaltyAccount).build(chargeId);
        PostLoanProductsRequest request = gson.fromJson(loanProductJSON, PostLoanProductsRequest.class);
        return loanTransactionHelper.createLoanProduct(request).getResourceId().intValue();
    }

    private Integer applyForLoanApplication(final String clientID, final String loanProductID, final String date) {
        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("15000.00").withLoanTermFrequency("4")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("4").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("2").withAmortizationTypeAsEqualInstallments()
                .withInterestTypeAsDecliningBalance().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate(date).withSubmittedOnDate(date).build(clientID, loanProductID, null);
        PostLoansRequest request = gson.fromJson(loanApplicationJSON, PostLoansRequest.class);
        return loanTransactionHelper.applyLoan(request).getLoanId().intValue();
    }

    private void getAndValidateExternalAssetOwnerTransferByLoan(Integer loanID, ExpectedExternalTransferData... expectedItems) {
        PageExternalTransferData retrieveResponse = externalAssetOwnerHelper.retrieveTransfersByLoanId(loanID.longValue());
        assertEquals(expectedItems.length, retrieveResponse.getNumberOfElements());

        for (ExpectedExternalTransferData expected : expectedItems) {
            assertNotNull(retrieveResponse.getContent());
            Optional<ExternalTransferData> first = retrieveResponse.getContent().stream()
                    .filter(e -> Objects.equals(e.getTransferExternalId(), expected.transferExternalId)
                            && Objects.equals(e.getStatus(), expected.status))
                    .findFirst();
            assertTrue(first.isPresent());
            ExternalTransferData etd = first.get();
            assertEquals(expected.transferExternalId, etd.getTransferExternalId());
            assertEquals(expected.status, etd.getStatus());
            assertEquals(LocalDate.parse(expected.settlementDate), etd.getSettlementDate());
            assertEquals(LocalDate.parse(expected.effectiveFrom), etd.getEffectiveFrom());
            assertEquals(LocalDate.parse(expected.effectiveTo), etd.getEffectiveTo());
            if (!expected.detailsExpected) {
                assertNull(etd.getDetails());
            } else {
                assertNotNull(etd.getDetails());
                assertEquals(expected.totalOutstanding, etd.getDetails().getTotalOutstanding());
                assertEquals(expected.totalPrincipalOutstanding, etd.getDetails().getTotalPrincipalOutstanding());
                assertEquals(expected.totalInterestOutstanding, etd.getDetails().getTotalInterestOutstanding());
                assertEquals(expected.totalPenaltyOutstanding, etd.getDetails().getTotalPenaltyChargesOutstanding());
                assertEquals(expected.totalFeeOutstanding, etd.getDetails().getTotalFeeChargesOutstanding());
                assertEquals(expected.totalOverpaid, etd.getDetails().getTotalOverpaid());
            }
        }
    }

    private void getAndValidateThereIsNoActiveMapping(Long loanId) {
        ExternalTransferData activeTransfer = externalAssetOwnerHelper.retrieveActiveTransferByLoanId(loanId);
        assertNull(activeTransfer);
    }

    private void getAndValidateThereIsNoActiveMapping(String transferExternalId) {
        ExternalTransferData activeTransfer = externalAssetOwnerHelper.retrieveActiveTransferByTransferExternalId(transferExternalId);
        assertNull(activeTransfer);
    }

    private void validateResponse(PostInitiateTransferResponse transferResponse, Integer loanID) {
        assertNotNull(transferResponse);
        assertNotNull(transferResponse.getResourceId());
        assertNotNull(transferResponse.getResourceExternalId());
        assertNotNull(transferResponse.getSubResourceId());
        assertEquals((long) loanID, transferResponse.getSubResourceId());
        assertNotNull(transferResponse.getSubResourceExternalId());
        assertNull(transferResponse.getChanges());
    }

    @RequiredArgsConstructor
    public static class ExpectedExternalTransferData {

        private final ExternalTransferData.StatusEnum status;

        private final String transferExternalId;

        private final String settlementDate;

        private final String effectiveFrom;
        private final String effectiveTo;
        private final boolean detailsExpected;
        private final BigDecimal totalOutstanding;
        private final BigDecimal totalPrincipalOutstanding;
        private final BigDecimal totalInterestOutstanding;
        private final BigDecimal totalPenaltyOutstanding;
        private final BigDecimal totalFeeOutstanding;
        private final BigDecimal totalOverpaid;

        static ExpectedExternalTransferData expected(ExternalTransferData.StatusEnum status, String transferExternalId,
                String settlementDate, String effectiveFrom, String effectiveTo, boolean detailsExpected, BigDecimal totalOutstanding,
                BigDecimal totalPrincipalOutstanding, BigDecimal totalInterestOutstanding, BigDecimal totalPenaltyOutstanding,
                BigDecimal totalFeeOutstanding, BigDecimal totalOverpaid) {
            return new ExpectedExternalTransferData(status, transferExternalId, settlementDate, effectiveFrom, effectiveTo, detailsExpected,
                    totalOutstanding, totalPrincipalOutstanding, totalInterestOutstanding, totalPenaltyOutstanding, totalFeeOutstanding,
                    totalOverpaid);
        }

    }

    @RequiredArgsConstructor
    public static class ExpectedJournalEntryData {

        private final Long glAccountId;
        private final Long entryTypeId;
        private final BigDecimal amount;
        private final LocalDate transactionDate;
        private final LocalDate submittedOnDate;

        static ExpectedJournalEntryData expected(Long glAccountId, Long entryTypeId, BigDecimal amount, LocalDate transactionDate,
                LocalDate submittedOnDate) {
            return new ExpectedJournalEntryData(glAccountId, entryTypeId, amount, transactionDate, submittedOnDate);
        }

    }
}
