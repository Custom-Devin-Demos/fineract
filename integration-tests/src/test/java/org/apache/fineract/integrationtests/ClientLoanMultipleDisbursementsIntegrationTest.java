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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.client.models.GetLoansLoanIdDisbursementDetails;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdStatus;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client Loan Integration Test for checking Loan Application Repayments Schedule, loan charges, penalties, loan
 * repayments and verifying accounting transactions
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
@ExtendWith(LoanTestLifecycleExtension.class)
public class ClientLoanMultipleDisbursementsIntegrationTest extends BaseLoanIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClientLoanMultipleDisbursementsIntegrationTest.class);
    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final Gson GSON = new JSON().getGson();

    private Long createLoanProduct(final boolean multiDisburseLoan) {
        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        LoanProductTestBuilder builder = new LoanProductTestBuilder() //
                .withPrincipal("12,000.00") //
                .withNumberOfRepayments("4") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withTranches(multiDisburseLoan);
        if (multiDisburseLoan) {
            builder = builder.withInterestCalculationPeriodTypeAsRepaymentPeriod(true);
            builder = builder.withMaxTrancheCount("30");
        }
        final String loanProductJSON = builder.build(null);
        return loanProductHelper.createLoanProduct(GSON.fromJson(loanProductJSON, PostLoanProductsRequest.class)).getResourceId();
    }

    private Long applyForLoanApplicationWithTranches(final Long clientID, final Long loanProductID, final String savingsId,
            String principal, List<HashMap> tranches, String submitDate) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("4") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("4") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("0") //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate(submitDate) //
                .withTranches(tranches) //
                .withSubmittedOnDate(submitDate) //
                .build(clientID.toString(), loanProductID.toString(), savingsId);
        return loanTransactionHelper.applyLoan(GSON.fromJson(loanApplicationJSON, PostLoansRequest.class)).getLoanId();
    }

    private HashMap createTrancheDetail(final String date, final String amount) {
        HashMap detail = new HashMap();
        detail.put("expectedDisbursementDate", date);
        detail.put("principal", amount);

        return detail;
    }

    private void disburseTranche(final Long loanID, final String date) {
        loanTransactionHelper.disburseLoan(loanID,
                new PostLoansLoanIdRequest().actualDisbursementDate(date).dateFormat(DATE_FORMAT).locale("en"));
    }

    private void disburseTranche(final Long loanID, final String date, final String transactionAmount) {
        loanTransactionHelper.disburseLoan(loanID, new PostLoansLoanIdRequest().actualDisbursementDate(date).dateFormat(DATE_FORMAT)
                .locale("en").transactionAmount(BigDecimal.valueOf(Double.parseDouble(transactionAmount))));
    }

    private static int countDisbursals(final GetLoansLoanIdResponse loanDetails) {
        int disbursalCount = 0;
        for (GetLoansLoanIdRepaymentPeriod period : loanDetails.getRepaymentSchedule().getPeriods()) {
            if (period.getPeriod() == null) {
                disbursalCount += 1;
            }
        }
        return disbursalCount;
    }

    private static BigDecimal totalPrincipalDisbursed(final GetLoansLoanIdResponse loanDetails) {
        BigDecimal totalPrincipalDisbursed = BigDecimal.ZERO;
        for (GetLoansLoanIdDisbursementDetails detail : loanDetails.getDisbursementDetails()) {
            if (detail.getActualDisbursementDate() != null) {
                totalPrincipalDisbursed = totalPrincipalDisbursed.add(BigDecimal.valueOf(detail.getPrincipal().doubleValue()));
            }
        }
        return totalPrincipalDisbursed;
    }

    private static GetLoansLoanIdRepaymentPeriod lastRepaymentPeriod(final GetLoansLoanIdResponse loanDetails) {
        List<GetLoansLoanIdRepaymentPeriod> periods = loanDetails.getRepaymentSchedule().getPeriods();
        return periods.get(periods.size() - 1);
    }

    /***
     * Test case to verify repayment schedule shows all disbursals for tranche loans
     */
    @Test
    public void checkThatAllMultiDisbursalsAppearOnLoanScheduleAndOutStandingBalanceIsZeroTest() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);

        /***
         * Create loan product with allowing multiple disbursals
         */
        boolean allowMultipleDisbursals = true;
        final Long loanProductID = createLoanProduct(allowMultipleDisbursals);
        Assertions.assertNotNull(loanProductID);

        /***
         * Apply for loan application and verify loan status
         */
        final String savingsId = null;
        final String principal = "12,000.00";

        LOG.info("-----------------------------------10 Tranches--------------------------------------");
        List<HashMap> tranches = new ArrayList<>();
        tranches.add(createTrancheDetail("01 January 2021", "1"));
        tranches.add(createTrancheDetail("02 January 2021", "2"));
        tranches.add(createTrancheDetail("03 January 2021", "4"));
        tranches.add(createTrancheDetail("04 January 2021", "8"));
        tranches.add(createTrancheDetail("05 January 2021", "16"));
        tranches.add(createTrancheDetail("06 January 2021", "32"));
        tranches.add(createTrancheDetail("07 January 2021", "64"));
        tranches.add(createTrancheDetail("08 January 2021", "128"));
        tranches.add(createTrancheDetail("09 January 2021", "256"));
        tranches.add(createTrancheDetail("10 January 2021", "512"));
        String submitDate = "01 January 2021";

        final Long loanID = applyForLoanApplicationWithTranches(clientID, loanProductID, savingsId, principal, tranches, submitDate);
        Assertions.assertNotNull(loanID);
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getPendingApproval());

        LOG.info("-----------------------------------APPROVE LOAN-----------------------------------------");
        loanTransactionHelper.approveLoan(loanID,
                new PostLoansLoanIdRequest().approvedOnDate("01 January 2021").dateFormat(DATE_FORMAT).locale("en"));
        GetLoansLoanIdStatus approvedStatus = loanTransactionHelper.getLoanDetails(loanID).getStatus();
        assertFalse(approvedStatus.getPendingApproval());
        assertTrue(approvedStatus.getWaitingForDisbursal());

        LOG.info("-------------------------------DISBURSE 8 LOANS -------------------------------------------");
        disburseTranche(loanID, "12 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        disburseTranche(loanID, "12 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        disburseTranche(loanID, "12 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        disburseTranche(loanID, "13 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        disburseTranche(loanID, "14 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        disburseTranche(loanID, "14 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        disburseTranche(loanID, "15 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        disburseTranche(loanID, "15 January 2021");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());

        GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails(loanID);
        final int loanScheduleLineCount = loanDetails.getRepaymentSchedule().getPeriods().size();
        final int expectedLoanScheduleLineCount = 9;
        final int expectedDisbursals = 8;
        final BigDecimal val255 = BigDecimal.valueOf(255.0);
        final BigDecimal expectedTotalPrincipalDisbursed = val255;
        final BigDecimal expectedPrincipalDue = val255;
        final BigDecimal expectedPrincipalLoanBalanceOutstanding = BigDecimal.valueOf(0.0);

        assertEquals(expectedLoanScheduleLineCount, loanScheduleLineCount, "Checking nine lines in schedule");

        assertEquals(expectedDisbursals, countDisbursals(loanDetails), "Checking for eight disbursals");
        assertEquals(expectedTotalPrincipalDisbursed, totalPrincipalDisbursed(loanDetails), "Checking Principal Disburse is 255");

        final GetLoansLoanIdRepaymentPeriod lastPeriod = lastRepaymentPeriod(loanDetails);
        final BigDecimal principalDue = BigDecimal.valueOf(lastPeriod.getPrincipalDue().doubleValue());
        assertEquals(expectedPrincipalDue, principalDue, "Checking Principal Due is 255");

        final BigDecimal principalLoanBalanceOutstanding = BigDecimal
                .valueOf(lastPeriod.getPrincipalLoanBalanceOutstanding().doubleValue());
        assertEquals(expectedPrincipalLoanBalanceOutstanding, principalLoanBalanceOutstanding,
                "Checking Principal Loan Balance Outstanding is zero");

    }

    @Test
    public void checkThatAllMultiDisbursalsAppearOnLoanScheduleAndOutStandingBalanceIsZeroButLoanGotReopenedTest() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);

        /***
         * Create loan product with allowing multiple disbursals
         */
        boolean allowMultipleDisbursals = true;
        final Long loanProductID = createLoanProduct(allowMultipleDisbursals);
        Assertions.assertNotNull(loanProductID);

        /***
         * Apply for loan application and verify loan status
         */
        final String savingsId = null;
        final String principal = "12,000.00";

        LOG.info("-----------------------------------2 Tranches--------------------------------------");
        List<HashMap> tranches = new ArrayList<>();
        tranches.add(createTrancheDetail("01 January 2021", "1"));
        tranches.add(createTrancheDetail("02 January 2021", "2"));
        String submitDate = "01 January 2021";

        final Long loanID = applyForLoanApplicationWithTranches(clientID, loanProductID, savingsId, principal, tranches, submitDate);
        Assertions.assertNotNull(loanID);
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getPendingApproval());

        LOG.info("-----------------------------------APPROVE LOAN-----------------------------------------");
        loanTransactionHelper.approveLoan(loanID,
                new PostLoansLoanIdRequest().approvedOnDate("01 January 2021").dateFormat(DATE_FORMAT).locale("en"));
        GetLoansLoanIdStatus approvedStatus = loanTransactionHelper.getLoanDetails(loanID).getStatus();
        assertFalse(approvedStatus.getPendingApproval());
        assertTrue(approvedStatus.getWaitingForDisbursal());

        LOG.info(
                "-------------------------------DISBURSE 1, repay fully, disburse again LOANS -------------------------------------------");
        disburseTranche(loanID, "12 January 2021", "1");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        loanTransactionHelper.makeLoanRepayment(loanID, "repayment", "13 January 2021", 1.0);
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getClosed());
        disburseTranche(loanID, "14 January 2021", "2");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());

        GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails(loanID);
        final int loanScheduleLineCount = loanDetails.getRepaymentSchedule().getPeriods().size();
        final int expectedLoanScheduleLineCount = 3;
        final int expectedDisbursals = 2;
        final BigDecimal expectedTotalPrincipalDisbursed = BigDecimal.valueOf(3.0);
        final BigDecimal expectedPrincipalDue = BigDecimal.valueOf(3.0);
        final BigDecimal expectedPrincipalPaid = BigDecimal.valueOf(1.0);
        final BigDecimal expectedPrincipalOutstanding = BigDecimal.valueOf(2.0);
        final BigDecimal expectedPrincipalLoanBalanceOutstanding = BigDecimal.valueOf(0.0);

        assertEquals(expectedLoanScheduleLineCount, loanScheduleLineCount, "Checking 3 lines in schedule");

        assertEquals(expectedDisbursals, countDisbursals(loanDetails), "Checking for 2 disbursals");
        assertEquals(expectedTotalPrincipalDisbursed, totalPrincipalDisbursed(loanDetails), "Checking Principal Disburse is 3");

        final GetLoansLoanIdRepaymentPeriod lastPeriod = lastRepaymentPeriod(loanDetails);
        final BigDecimal principalDue = BigDecimal.valueOf(lastPeriod.getPrincipalDue().doubleValue());
        assertEquals(expectedPrincipalDue, principalDue, "Checking Principal Due is 3");
        final BigDecimal principalPaid = BigDecimal.valueOf(lastPeriod.getPrincipalPaid().doubleValue());
        assertEquals(expectedPrincipalPaid, principalPaid, "Checking Principal Paid is 1");
        final BigDecimal principalOutstanding = BigDecimal.valueOf(lastPeriod.getPrincipalOutstanding().doubleValue());
        assertEquals(expectedPrincipalOutstanding, principalOutstanding, "Checking Principal Due is 2");

        final BigDecimal principalLoanBalanceOutstanding = BigDecimal
                .valueOf(lastPeriod.getPrincipalLoanBalanceOutstanding().doubleValue());
        assertEquals(expectedPrincipalLoanBalanceOutstanding, principalLoanBalanceOutstanding,
                "Checking Principal Loan Balance Outstanding is zero");

    }

    @Test
    public void checkThatAllMultiDisbursalsAppearOnLoanScheduleAndOutStandingBalanceIsZeroButLoanGotReopenedFromOverPaidTest() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);

        /***
         * Create loan product with allowing multiple disbursals
         */
        boolean allowMultipleDisbursals = true;
        final Long loanProductID = createLoanProduct(allowMultipleDisbursals);
        Assertions.assertNotNull(loanProductID);

        /***
         * Apply for loan application and verify loan status
         */
        final String savingsId = null;
        final String principal = "12,000.00";

        LOG.info("-----------------------------------2 Tranches--------------------------------------");
        List<HashMap> tranches = new ArrayList<>();
        tranches.add(createTrancheDetail("01 January 2021", "1"));
        tranches.add(createTrancheDetail("02 January 2021", "2"));
        String submitDate = "01 January 2021";

        final Long loanID = applyForLoanApplicationWithTranches(clientID, loanProductID, savingsId, principal, tranches, submitDate);
        Assertions.assertNotNull(loanID);
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getPendingApproval());

        LOG.info("-----------------------------------APPROVE LOAN-----------------------------------------");
        loanTransactionHelper.approveLoan(loanID,
                new PostLoansLoanIdRequest().approvedOnDate("01 January 2021").dateFormat(DATE_FORMAT).locale("en"));
        GetLoansLoanIdStatus approvedStatus = loanTransactionHelper.getLoanDetails(loanID).getStatus();
        assertFalse(approvedStatus.getPendingApproval());
        assertTrue(approvedStatus.getWaitingForDisbursal());

        LOG.info(
                "-------------------------------DISBURSE 1, repay fully, disburse again LOANS -------------------------------------------");
        disburseTranche(loanID, "12 January 2021", "1");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());
        loanTransactionHelper.makeLoanRepayment(loanID, "repayment", "13 January 2021", 2.0);
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getOverpaid());
        disburseTranche(loanID, "14 January 2021", "2");
        assertTrue(loanTransactionHelper.getLoanDetails(loanID).getStatus().getActive());

        GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails(loanID);
        final int loanScheduleLineCount = loanDetails.getRepaymentSchedule().getPeriods().size();
        final int expectedLoanScheduleLineCount = 3;
        final int expectedDisbursals = 2;
        final BigDecimal expectedTotalPrincipalDisbursed = BigDecimal.valueOf(3.0);
        final BigDecimal expectedPrincipalDue = BigDecimal.valueOf(3.0);
        final BigDecimal expectedPrincipalPaid = BigDecimal.valueOf(2.0);
        final BigDecimal expectedPrincipalOutstanding = BigDecimal.valueOf(1.0);
        final BigDecimal expectedPrincipalLoanBalanceOutstanding = BigDecimal.valueOf(0.0);

        assertEquals(expectedLoanScheduleLineCount, loanScheduleLineCount, "Checking nine lines in schedule");

        assertEquals(expectedDisbursals, countDisbursals(loanDetails), "Checking for 2 disbursals");
        assertEquals(expectedTotalPrincipalDisbursed, totalPrincipalDisbursed(loanDetails), "Checking Principal Disburse is 3");

        final GetLoansLoanIdRepaymentPeriod lastPeriod = lastRepaymentPeriod(loanDetails);
        final BigDecimal principalDue = BigDecimal.valueOf(lastPeriod.getPrincipalDue().doubleValue());
        assertEquals(expectedPrincipalDue, principalDue, "Checking Principal Due is 3");
        final BigDecimal principalPaid = BigDecimal.valueOf(lastPeriod.getPrincipalPaid().doubleValue());
        assertEquals(expectedPrincipalPaid, principalPaid, "Checking Principal Paid is 1");
        final BigDecimal principalOutstanding = BigDecimal.valueOf(lastPeriod.getPrincipalOutstanding().doubleValue());
        assertEquals(expectedPrincipalOutstanding, principalOutstanding, "Checking Principal Due is 2");

        final BigDecimal principalLoanBalanceOutstanding = BigDecimal
                .valueOf(lastPeriod.getPrincipalLoanBalanceOutstanding().doubleValue());
        assertEquals(expectedPrincipalLoanBalanceOutstanding, principalLoanBalanceOutstanding,
                "Checking Principal Loan Balance Outstanding is zero");

    }

}
