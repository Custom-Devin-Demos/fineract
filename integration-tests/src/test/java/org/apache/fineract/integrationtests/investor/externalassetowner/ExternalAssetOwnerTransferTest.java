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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import feign.Headers;
import feign.RequestLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.ExternalAssetOwnerRequest;
import org.apache.fineract.client.models.ExternalOwnerJournalEntryData;
import org.apache.fineract.client.models.ExternalOwnerTransferJournalEntryData;
import org.apache.fineract.client.models.ExternalTransferData;
import org.apache.fineract.client.models.PageExternalTransferData;
import org.apache.fineract.client.models.PostInitiateTransferResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.BaseLoanIntegrationTest;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.BusinessStepHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.ExternalAssetOwnerHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.FinancialActivityAccountHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.lang.NonNull;

@Slf4j
public class ExternalAssetOwnerTransferTest extends BaseLoanIntegrationTest {

    protected Account assetAccount;
    protected Account feePenaltyAccount;
    protected Account transferAccount;
    protected Account expenseAccount;
    protected Account incomeAccount;
    protected Account overpaymentAccount;
    protected static ExternalAssetOwnerHelper EXTERNAL_ASSET_OWNER_HELPER;
    protected LocalDate todaysDate;
    public String ownerExternalId;
    protected DateTimeFormatter dateFormatter = new DateTimeFormatterBuilder().appendPattern("dd MMMM yyyy").toFormatter();
    private final Gson gson = new JSON().getGson();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    public void setupInvestorBusinessStep() {
        EXTERNAL_ASSET_OWNER_HELPER = new ExternalAssetOwnerHelper();
        FinancialActivityAccountHelper financialActivityAccountHelper = new FinancialActivityAccountHelper(requestSpec);

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

        EXTERNAL_ASSET_OWNER_HELPER.setProperFinancialActivity(financialActivityAccountHelper, transferAccount);
    }

    protected void updateBusinessDateAndExecuteCOBJob(String date) {
        setBusinessDate(LocalDate.parse(date));
        schedulerJobHelper.executeAndAwaitJob("Loan COB");
    }

    protected void setBusinessDate(LocalDate date) {
        BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                .date(dateFormatter.format(date)).dateFormat("dd MMMM yyyy").locale("en"));
    }

    protected PostInitiateTransferResponse createSaleTransfer(Integer loanID, String settlementDate) {
        String transferExternalId = UUID.randomUUID().toString();
        String transferExternalGroupId = UUID.randomUUID().toString();
        ownerExternalId = UUID.randomUUID().toString();
        return createSaleTransfer(loanID, settlementDate, transferExternalId, transferExternalGroupId, ownerExternalId, "1.0");
    }

    protected PostInitiateTransferResponse createSaleTransfer(Integer loanID, String settlementDate, String transferExternalId,
            String transferExternalGroupId, String ownerExternalId, String purchasePriceRatio) {
        PostInitiateTransferResponse saleResponse = EXTERNAL_ASSET_OWNER_HELPER.initiateTransferByLoanId(loanID.longValue(), "sale",
                new ExternalAssetOwnerRequest().settlementDate(settlementDate).dateFormat("yyyy-MM-dd").locale("en")
                        .transferExternalId(transferExternalId).transferExternalGroupId(transferExternalGroupId)
                        .ownerExternalId(ownerExternalId).purchasePriceRatio(purchasePriceRatio));
        assertEquals(transferExternalId, saleResponse.getResourceExternalId());
        return saleResponse;
    }

    protected PostInitiateTransferResponse createBuybackTransfer(Integer loanID, String settlementDate) {
        String transferExternalId = UUID.randomUUID().toString();
        return createBuybackTransfer(loanID, settlementDate, transferExternalId);
    }

    protected PostInitiateTransferResponse createBuybackTransfer(Integer loanID, String settlementDate, String transferExternalId) {
        PostInitiateTransferResponse saleResponse = EXTERNAL_ASSET_OWNER_HELPER.initiateTransferByLoanId(loanID.longValue(), "buyback",
                new ExternalAssetOwnerRequest().settlementDate(settlementDate).dateFormat("yyyy-MM-dd").locale("en")
                        .transferExternalId(transferExternalId));
        assertEquals(transferExternalId, saleResponse.getResourceExternalId());
        return saleResponse;
    }

    protected void addPenaltyForLoan(Integer loanID, String amount) {
        // Add Charge Penalty
        Long penalty1LoanChargeId = addCharge(loanID.longValue(), true, Double.parseDouble(amount), "02 March 2020");
        assertNotNull(penalty1LoanChargeId);
    }

    protected void setInitialBusinessDate(LocalDate date) {
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                new PutGlobalConfigurationsRequest().enabled(true));
        setBusinessDate(date);
    }

    protected void cleanUpAndRestoreBusinessDate() {
        setBusinessDate(todaysDate);
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                new PutGlobalConfigurationsRequest().enabled(false));
        globalConfigurationHelper.manageConfigurations(GlobalConfigurationConstants.ENABLE_AUTO_GENERATED_EXTERNAL_ID, false);
    }

    @NonNull
    protected Integer createClient() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        Assertions.assertNotNull(clientID);
        return clientID.intValue();
    }

    @NonNull
    protected Integer createLoanForClient(Integer clientID, String transactionDate) {
        Long overdueFeeChargeId = createOverdueFeeCharge("1");
        Assertions.assertNotNull(overdueFeeChargeId);

        Integer loanProductID = createLoanProduct(overdueFeeChargeId.toString());
        Assertions.assertNotNull(loanProductID);

        Integer loanID = applyForLoanApplication(clientID.toString(), loanProductID.toString(), transactionDate);
        Assertions.assertNotNull(loanID);

        HashMap<String, Object> loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        loanTransactionHelper.approveLoan(loanID.longValue(),
                new PostLoansLoanIdRequest().approvedOnDate(transactionDate).dateFormat("dd MMMM yyyy").locale("en").note("Approval NOTE"));
        loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);

        loanTransactionHelper.disburseLoan(loanID.longValue(), new PostLoansLoanIdRequest().actualDisbursementDate(transactionDate)
                .dateFormat("dd MMMM yyyy").locale("en").note("DISBURSE NOTE"));
        loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);
        return loanID;
    }

    protected Long createOverdueFeeCharge(String penaltyPercentageAmount) {
        // Loan overdue-installment penalty, percentage of amount and interest
        ChargeRequest chargeRequest = new ChargeRequest().active(true).amount(Double.parseDouble(penaltyPercentageAmount))
                .chargeAppliesTo(1).currencyCode("USD").locale("en").monthDayFormat("dd MMM")
                .name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6)).penalty(true).chargePaymentMode(0).chargeTimeType(9)
                .chargeCalculationType(3);
        return chargesHelper.createCharges(chargeRequest).getResourceId();
    }

    protected Integer createLoanProduct(final String chargeId) {

        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal("15000.00").withNumberOfRepayments("4")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("1")
                .withAccountingRulePeriodicAccrual(new Account[] { assetAccount, expenseAccount, incomeAccount, overpaymentAccount })
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestTypeAsDecliningBalance()
                .withFeeAndPenaltyAssetAccount(feePenaltyAccount).build(chargeId);
        PostLoanProductsRequest request = gson.fromJson(loanProductJSON, PostLoanProductsRequest.class);
        return loanTransactionHelper.createLoanProduct(request).getResourceId().intValue();
    }

    protected Integer applyForLoanApplication(final String clientID, final String loanProductID, final String date) {
        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("15000.00").withLoanTermFrequency("4")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("4").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("2").withAmortizationTypeAsEqualInstallments()
                .withInterestTypeAsDecliningBalance().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate(date).withSubmittedOnDate(date).build(clientID, loanProductID, null);
        return submitLoanApplicationWithCollateral(clientID, loanApplicationJSON);
    }

    /**
     * Submits a loan application whose payload carries collateral. The generated PostLoansRequest model has no
     * collateral field, so the collateral-bearing body is posted as a raw JSON tree through a minimal fixed Feign API.
     */
    protected static Integer submitLoanApplicationWithCollateral(final String clientID, final String loanApplicationJSON) {
        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(Long.valueOf(clientID), collateralId);
        final ObjectNode body;
        try {
            body = (ObjectNode) OBJECT_MAPPER.readTree(loanApplicationJSON);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to parse loan application payload", e);
        }
        final ObjectNode collateral = OBJECT_MAPPER.createObjectNode();
        collateral.put("clientCollateralId", clientCollateralId.toString());
        collateral.put("quantity", "1");
        body.putArray("collateral").add(collateral);
        return FineractFeignClientHelper.getFineractFeignClient().create(LoanApplicationSubmitApi.class).submitLoanApplication(body)
                .getLoanId().intValue();
    }

    /**
     * Minimal fixed Feign API for submitting a loan application whose JSON body includes collateral (unsupported by the
     * generated PostLoansRequest model).
     */
    public interface LoanApplicationSubmitApi {

        @RequestLine("POST /v1/loans")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostLoansResponse submitLoanApplication(JsonNode body);
    }

    protected void getAndValidateExternalAssetOwnerTransferByLoan(Integer loanID, ExpectedExternalTransferData... expectedItems) {
        PageExternalTransferData retrieveResponse = EXTERNAL_ASSET_OWNER_HELPER.retrieveTransfersByLoanId(loanID.longValue());
        assertEquals(expectedItems.length, retrieveResponse.getNumberOfElements());
        validateExternalAssetOwnerTransfer(retrieveResponse, expectedItems);
    }

    protected void validateExternalAssetOwnerTransfer(PageExternalTransferData response, ExpectedExternalTransferData... expectedItems) {
        for (ExpectedExternalTransferData expected : expectedItems) {
            assertNotNull(response.getContent());
            Optional<ExternalTransferData> first = response.getContent().stream()
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
            if (expected.subStatus != null) {
                assertEquals(expected.subStatus, etd.getSubStatus());
            }
        }
    }

    protected void getAndValidateThereIsActiveMapping(Integer loanID) {
        ExternalTransferData activeTransfer = EXTERNAL_ASSET_OWNER_HELPER.retrieveActiveTransferByLoanId((long) loanID);
        assertNotNull(activeTransfer);
        ExternalTransferData retrieveResponse = EXTERNAL_ASSET_OWNER_HELPER.retrieveTransfersByLoanId(loanID.longValue()).getContent()
                .stream().filter(transfer -> ExternalTransferData.StatusEnum.ACTIVE.equals(transfer.getStatus())).findFirst().get();
        assertEquals(retrieveResponse.getTransferId(), activeTransfer.getTransferId());
    }

    protected void getAndValidateThereIsNoActiveMapping(Long loanId) {
        ExternalTransferData activeTransfer = EXTERNAL_ASSET_OWNER_HELPER.retrieveActiveTransferByLoanId(loanId);
        assertNull(activeTransfer);
    }

    protected void getAndValidateThereIsNoActiveMapping(String transferExternalId) {
        ExternalTransferData activeTransfer = EXTERNAL_ASSET_OWNER_HELPER.retrieveActiveTransferByTransferExternalId(transferExternalId);
        assertNull(activeTransfer);
    }

    protected void validateResponse(PostInitiateTransferResponse transferResponse, Integer loanID) {
        assertNotNull(transferResponse);
        assertNotNull(transferResponse.getResourceId());
        assertNotNull(transferResponse.getResourceExternalId());
        assertNotNull(transferResponse.getSubResourceId());
        assertEquals((long) loanID, transferResponse.getSubResourceId());
        assertNotNull(transferResponse.getSubResourceExternalId());
        assertNull(transferResponse.getChanges());
    }

    protected void getAndValidateOwnerJournalEntries(String ownerExternalId, ExpectedJournalEntryData... expectedItems) {
        ExternalOwnerJournalEntryData result = EXTERNAL_ASSET_OWNER_HELPER.retrieveJournalEntriesOfOwner(ownerExternalId);
        assertNotNull(result);
        assertEquals(expectedItems.length, result.getJournalEntryData().getTotalElements());
        int i = 0;
        assertEquals(ownerExternalId, result.getOwnerData().getExternalId());
        for (ExpectedJournalEntryData expectedJournalEntryData : expectedItems) {
            assertTrue(expectedJournalEntryData.amount.compareTo(result.getJournalEntryData().getContent().get(i).getAmount()) == 0);
            assertEquals(expectedJournalEntryData.entryTypeId, result.getJournalEntryData().getContent().get(i).getEntryType().getId());
            assertEquals(expectedJournalEntryData.glAccountId, result.getJournalEntryData().getContent().get(i).getGlAccountId());
            assertEquals(expectedJournalEntryData.transactionDate, result.getJournalEntryData().getContent().get(i).getTransactionDate());
            assertEquals(expectedJournalEntryData.submittedOnDate, result.getJournalEntryData().getContent().get(i).getSubmittedOnDate());
            i++;
        }
    }

    protected void getAndValidateThereIsJournalEntriesForTransfer(Long transferId, ExpectedJournalEntryData... expectedItems) {
        ExternalOwnerTransferJournalEntryData result = EXTERNAL_ASSET_OWNER_HELPER.retrieveJournalEntriesOfTransfer(transferId);
        assertNotNull(result);
        long totalElements = result.getJournalEntryData().getTotalElements();
        assertEquals(expectedItems.length, totalElements);
        int i = 0;
        assertEquals(transferId, result.getTransferData().getTransferId());
        for (ExpectedJournalEntryData expectedJournalEntryData : expectedItems) {
            assertTrue(expectedJournalEntryData.amount.compareTo(result.getJournalEntryData().getContent().get(i).getAmount()) == 0);
            assertEquals(expectedJournalEntryData.entryTypeId, result.getJournalEntryData().getContent().get(i).getEntryType().getId());
            assertEquals(expectedJournalEntryData.glAccountId, result.getJournalEntryData().getContent().get(i).getGlAccountId());
            assertEquals(expectedJournalEntryData.transactionDate, result.getJournalEntryData().getContent().get(i).getTransactionDate());
            assertEquals(expectedJournalEntryData.submittedOnDate, result.getJournalEntryData().getContent().get(i).getSubmittedOnDate());
            i++;
        }
    }

    protected void getAndValidateThereIsNoJournalEntriesForTransfer(Long transferId) {
        ExternalOwnerTransferJournalEntryData result = EXTERNAL_ASSET_OWNER_HELPER.retrieveJournalEntriesOfTransfer(transferId);
        assertNull(result.getJournalEntryData());
    }

    @RequiredArgsConstructor
    public static class ExpectedExternalTransferData {

        private final ExternalTransferData.StatusEnum status;

        private final String transferExternalId;

        private final String settlementDate;

        private final String effectiveFrom;
        private final String effectiveTo;
        private final ExternalTransferData.SubStatusEnum subStatus;
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
            return new ExpectedExternalTransferData(status, transferExternalId, settlementDate, effectiveFrom, effectiveTo, null,
                    detailsExpected, totalOutstanding, totalPrincipalOutstanding, totalInterestOutstanding, totalPenaltyOutstanding,
                    totalFeeOutstanding, totalOverpaid);
        }

        static ExpectedExternalTransferData expected(ExternalTransferData.StatusEnum status, String transferExternalId,
                String settlementDate, String effectiveFrom, String effectiveTo) {
            return new ExpectedExternalTransferData(status, transferExternalId, settlementDate, effectiveFrom, effectiveTo, null, false,
                    null, null, null, null, null, null);
        }

        static ExpectedExternalTransferData expected(ExternalTransferData.StatusEnum status, String transferExternalId,
                String settlementDate, String effectiveFrom, String effectiveTo, ExternalTransferData.SubStatusEnum subStatus) {
            return new ExpectedExternalTransferData(status, transferExternalId, settlementDate, effectiveFrom, effectiveTo, subStatus,
                    false, null, null, null, null, null, null);
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
