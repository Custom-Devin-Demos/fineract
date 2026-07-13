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

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.client.models.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({ "rawtypes", "unchecked" })
@ExtendWith(LoanTestLifecycleExtension.class)
public class LoanFixedPrincipalPercentageAmortizationTest extends BaseLoanIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoanFixedPrincipalPercentageAmortizationTest.class);
    private static final Gson GSON = new JSON().getGson();

    private static final String ACCOUNTING_NONE = "1";

    @Test
    public void checkLoanCreateAndDisburseFlowWithFixedPrincipalPercentage() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        final Long loanProductID = createLoanProduct(ACCOUNTING_NONE);
        final Long loanID = applyForLoanApplication(clientID, loanProductID, null, null, "100000.00");
        final List<GetLoansLoanIdRepaymentPeriod> loanSchedule = loanTransactionHelper.getLoanDetails(loanID).getRepaymentSchedule()
                .getPeriods();
        verifyLoanRepaymentScheduleForEqualPrincipal(loanSchedule);
    }

    @Test
    public void checkLoanCreateAndDisburseFlowWithFixedPrincipalPercentageWithPrincipalGrace() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        final Long loanProductID = createLoanProduct(ACCOUNTING_NONE);
        final Long loanID = applyForLoanApplicationWithPrincipalGrace(clientID, loanProductID, null, null, "100000.00");
        final List<GetLoansLoanIdRepaymentPeriod> loanSchedule = loanTransactionHelper.getLoanDetails(loanID).getRepaymentSchedule()
                .getPeriods();
        verifyLoanRepaymentScheduleForEqualPrincipalWithPrincipalGrace(loanSchedule);
    }

    @Test
    public void checkLoanCreateAndDisburseFlowWithFixedPrincipalPercentageAndFlatInterest() {
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        final Long loanProductID = createLoanProductWithFlatInterest(ACCOUNTING_NONE);
        final Long loanID = applyForLoanApplicationWithFlatInterest(clientID, loanProductID, null, null, "100000.00");
        final List<GetLoansLoanIdRepaymentPeriod> loanSchedule = loanTransactionHelper.getLoanDetails(loanID).getRepaymentSchedule()
                .getPeriods();
        verifyLoanRepaymentScheduleForEqualPrincipalAndFlatInterest(loanSchedule);
    }

    private Long createLoanProduct(final String accountingRule, final Account... accounts) {
        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        LoanProductTestBuilder builder = new LoanProductTestBuilder() //
                .withPrincipal("100000.00") //
                .withNumberOfRepayments("13") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestCalculationPeriodTypeAsDays().withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() // This is required to fix the principal
                .withPrinciplePercentagePerInstallment("5.00") // This fixes the principal at a fixed value till the
                                                               // second last EMI
                .withInterestTypeAsDecliningBalance() //
                .withAccounting(accountingRule, accounts);

        final String loanProductJSON = builder.build(null);
        return loanProductHelper.createLoanProduct(GSON.fromJson(loanProductJSON, PostLoanProductsRequest.class)).getResourceId();
    }

    private Long applyForLoanApplication(final Long clientID, final Long loanProductID, List<HashMap> charges, final String savingsId,
            String principal) {

        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("13") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("13") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withAmortizationTypeAsEqualPrincipalPayments() // This is required to fix the principal
                .withPrinciplePercentagePerInstallment("5.00") // This fixes the principal at a fixed value till the
                                                               // second last EMI
                .withInterestTypeAsDecliningBalance() //
                .withInterestCalculationPeriodTypeAsDays() //
                .withExpectedDisbursementDate("20 September 2011") //
                .withSubmittedOnDate("20 September 2011") //
                .withCollaterals(collaterals).withCharges(charges).build(clientID.toString(), loanProductID.toString(), savingsId);
        return loanTransactionHelper.applyLoan(GSON.fromJson(loanApplicationJSON, PostLoansRequest.class)).getLoanId();
    }

    private void addCollaterals(List<HashMap> collaterals, Long collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private HashMap<String, String> collaterals(Long collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<String, String>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

    private Long applyForLoanApplicationWithPrincipalGrace(final Long clientID, final Long loanProductID, List<HashMap> charges,
            final String savingsId, String principal) {
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("19") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("19") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withAmortizationTypeAsEqualPrincipalPayments() // This is required to fix the principal
                .withPrinciplePercentagePerInstallment("5.00") // This fixes the principal at a fixed value till the
                                                               // second last EMI
                .withPrincipalGrace("6").withInterestTypeAsDecliningBalance() //
                .withInterestCalculationPeriodTypeAsDays() //
                .withExpectedDisbursementDate("20 September 2011") //
                .withSubmittedOnDate("20 September 2011") //
                .withCollaterals(collaterals).withCharges(charges).build(clientID.toString(), loanProductID.toString(), savingsId);
        return loanTransactionHelper.applyLoan(GSON.fromJson(loanApplicationJSON, PostLoansRequest.class)).getLoanId();
    }

    private void verifyLoanRepaymentScheduleForEqualPrincipal(final List<GetLoansLoanIdRepaymentPeriod> loanSchedule) {
        LOG.info("--------------------VERIFYING THE PRINCIPAL DUES,INTEREST DUE AND DUE DATE--------------------------");

        assertEquals(LocalDate.of(2011, 10, 20), loanSchedule.get(1).getDueDate(), "Checking for Due Date for 1st Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(1).getPrincipalOriginalDue().floatValue(),
                "Checking for Principal Due for 1st Month");
        assertEquals(Float.parseFloat("1972.60"), loanSchedule.get(1).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 1st Month");

        assertEquals(LocalDate.of(2011, 11, 20), loanSchedule.get(2).getDueDate(), "Checking for Due Date for 2nd Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(2).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 2nd Month");
        assertEquals(Float.parseFloat("1936.44"), loanSchedule.get(2).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 2nd Month");

        assertEquals(LocalDate.of(2011, 12, 20), loanSchedule.get(3).getDueDate(), "Checking for Due Date for 3rd Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(3).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 3rd Month");
        assertEquals(Float.parseFloat("1775.34"), loanSchedule.get(3).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 3rd Month");

        assertEquals(LocalDate.of(2012, 9, 20), loanSchedule.get(12).getDueDate(), "Checking for Due Date for 12th Month");
        assertEquals(Float.parseFloat("5000 "), loanSchedule.get(12).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 12th Month");
        assertEquals(Float.parseFloat("917.26"), loanSchedule.get(12).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 12th Month");

        assertEquals(LocalDate.of(2012, 10, 20), loanSchedule.get(13).getDueDate(), "Checking for Due Date for 13th Month - Last EMI");
        assertEquals(Float.parseFloat("40000"), loanSchedule.get(13).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 13th Month - Last EMI");
        assertEquals(Float.parseFloat("789.04"), loanSchedule.get(13).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 13th Month - Last EMI");

    }

    private void verifyLoanRepaymentScheduleForEqualPrincipalWithPrincipalGrace(final List<GetLoansLoanIdRepaymentPeriod> loanSchedule) {
        LOG.info("--------------------VERIFYING THE PRINCIPAL DUES,INTEREST DUE AND DUE DATE--------------------------");

        assertEquals(LocalDate.of(2011, 10, 20), loanSchedule.get(1).getDueDate(), "Checking for Due Date for 1st Month");
        assertEquals(Float.parseFloat("0"), loanSchedule.get(1).getPrincipalOriginalDue().floatValue(),
                "Checking for Principal Due for 1st Month");
        assertEquals(Float.parseFloat("1972.6"), loanSchedule.get(1).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 1st Month");

        assertEquals(LocalDate.of(2012, 3, 20), loanSchedule.get(6).getDueDate(), "Checking for Due Date for 6th Month");
        assertEquals(Float.parseFloat("0"), loanSchedule.get(6).getPrincipalDue().floatValue(), "Checking for Principal Due for 6th Month");
        assertEquals(Float.parseFloat("1906.85"), loanSchedule.get(6).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 6th Month");

        assertEquals(LocalDate.of(2012, 4, 20), loanSchedule.get(7).getDueDate(), "Checking for Due Date for 7th Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(7).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 7th Month");
        assertEquals(Float.parseFloat("2038.36"), loanSchedule.get(7).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 7th Month");

        assertEquals(LocalDate.of(2013, 3, 20), loanSchedule.get(18).getDueDate(), "Checking for Due Date for 18th Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(18).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 18th Month");
        assertEquals(Float.parseFloat("828.49"), loanSchedule.get(18).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 18th Month");

        assertEquals(LocalDate.of(2013, 4, 20), loanSchedule.get(19).getDueDate(), "Checking for Due Date for 19th Month - Last EMI");
        assertEquals(Float.parseFloat("40000"), loanSchedule.get(19).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 19th Month - Last EMI");
        assertEquals(Float.parseFloat("815.34"), loanSchedule.get(19).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 19th Month - Last EMI");

    }

    private Long createLoanProductWithFlatInterest(final String accountingRule, final Account... accounts) {
        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        LoanProductTestBuilder builder = new LoanProductTestBuilder() //
                .withPrincipal("100000.00") //
                .withNumberOfRepayments("13") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestCalculationPeriodTypeAsDays().withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() // This is required to fix the principal
                .withPrinciplePercentagePerInstallment("5.00") // This fixes the principal at a fixed value till the
                                                               // second last EMI
                .withInterestTypeAsFlat() //
                .withAccounting(accountingRule, accounts);

        final String loanProductJSON = builder.build(null);
        return loanProductHelper.createLoanProduct(GSON.fromJson(loanProductJSON, PostLoanProductsRequest.class)).getResourceId();
    }

    private Long applyForLoanApplicationWithFlatInterest(final Long clientID, final Long loanProductID, List<HashMap> charges,
            final String savingsId, String principal) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("13") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("13") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withAmortizationTypeAsEqualPrincipalPayments() // This is required to fix the principal
                .withPrinciplePercentagePerInstallment("5.00") // This fixes the principal at a fixed value till the
                                                               // second last EMI
                .withInterestTypeAsFlatBalance() //
                .withInterestCalculationPeriodTypeAsDays() //
                .withExpectedDisbursementDate("20 September 2011") //
                .withSubmittedOnDate("20 September 2011") //
                .withCharges(charges).build(clientID.toString(), loanProductID.toString(), savingsId);
        return loanTransactionHelper.applyLoan(GSON.fromJson(loanApplicationJSON, PostLoansRequest.class)).getLoanId();
    }

    private void verifyLoanRepaymentScheduleForEqualPrincipalAndFlatInterest(final List<GetLoansLoanIdRepaymentPeriod> loanSchedule) {
        LOG.info("--------------------VERIFYING THE PRINCIPAL DUES,INTEREST DUE AND DUE DATE--------------------------");

        assertEquals(LocalDate.of(2011, 10, 20), loanSchedule.get(1).getDueDate(), "Checking for Due Date for 1st Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(1).getPrincipalOriginalDue().floatValue(),
                "Checking for Principal Due for 1st Month");
        assertEquals(Float.parseFloat("2002.95"), loanSchedule.get(1).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 1st Month");

        assertEquals(LocalDate.of(2011, 11, 20), loanSchedule.get(2).getDueDate(), "Checking for Due Date for 2nd Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(2).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 2nd Month");
        assertEquals(Float.parseFloat("2002.95"), loanSchedule.get(2).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 2nd Month");

        assertEquals(LocalDate.of(2011, 12, 20), loanSchedule.get(3).getDueDate(), "Checking for Due Date for 3rd Month");
        assertEquals(Float.parseFloat("5000"), loanSchedule.get(3).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 3rd Month");
        assertEquals(Float.parseFloat("2002.95"), loanSchedule.get(3).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 3rd Month");

        assertEquals(LocalDate.of(2012, 9, 20), loanSchedule.get(12).getDueDate(), "Checking for Due Date for 12th Month");
        assertEquals(Float.parseFloat("5000 "), loanSchedule.get(12).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 12th Month");
        assertEquals(Float.parseFloat("2002.95"), loanSchedule.get(12).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 12th Month");

        assertEquals(LocalDate.of(2012, 10, 20), loanSchedule.get(13).getDueDate(), "Checking for Due Date for 13th Month - Last EMI");
        assertEquals(Float.parseFloat("40000"), loanSchedule.get(13).getPrincipalDue().floatValue(),
                "Checking for Principal Due for 13th Month - Last EMI");
        assertEquals(Float.parseFloat("2002.96"), loanSchedule.get(13).getInterestOriginalDue().floatValue(),
                "Checking for Interest Due for 13th Month - Last EMI");

    }

}
