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
package org.apache.fineract.integrationtests.loan.penalty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.client.models.GetLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.client.models.LoanProductChargeData;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.BaseLoanIntegrationTest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoanLateFeeChargeTest extends BaseLoanIntegrationTest {

    private static final String APPLY_PENALTY_JOB = "Apply penalty to overdue loans";

    @BeforeEach
    public void before() {
        PutGlobalConfigurationsRequest request = new PutGlobalConfigurationsRequest().value(0L).enabled(true);
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.PENALTY_WAIT_PERIOD, request);
    }

    @AfterEach
    public void after() {
        // go back to defaults
        PutGlobalConfigurationsRequest request = new PutGlobalConfigurationsRequest().value(2L).enabled(true);
        globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.PENALTY_WAIT_PERIOD, request);
    }

    @Test
    public void test_LateFeeFlatCharge_AppliedByPenaltyJob_OnOverdueInstallment() {
        AtomicReference<Long> aLoanId = new AtomicReference<>();
        double lateFeeAmount = 10.0;

        runAt("01 January 2023", () -> {
            Long clientId = clientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
            Long chargeId = createLateFeeFlatCharge(lateFeeAmount, null, 0);
            Long loanProductId = createNoInterestProductWithCharge(chargeId);
            Long loanId = applyApproveAndDisburse(clientId, loanProductId, 1000.0);
            aLoanId.set(loanId);

            verifyRepaymentSchedule(loanId, //
                    installment(1000.0, null, "01 January 2023"), //
                    installment(333.33, 0.0, 0.0, 0.0, 333.33, false, "05 January 2023"), //
                    installment(333.33, 0.0, 0.0, 0.0, 333.33, false, "09 January 2023"), //
                    installment(333.34, 0.0, 0.0, 0.0, 333.34, false, "13 January 2023") //
            );
        });

        runAt("04 January 2023", () -> {
            Long loanId = aLoanId.get();
            schedulerJobHelper.executeAndAwaitJob(APPLY_PENALTY_JOB);

            // nothing is overdue yet
            assertTrue(getLateFeeLoanCharges(loanId).isEmpty());
            verifyRepaymentSchedule(loanId, //
                    installment(1000.0, null, "01 January 2023"), //
                    installment(333.33, 0.0, 0.0, 0.0, 333.33, false, "05 January 2023"), //
                    installment(333.33, 0.0, 0.0, 0.0, 333.33, false, "09 January 2023"), //
                    installment(333.34, 0.0, 0.0, 0.0, 333.34, false, "13 January 2023") //
            );
        });

        runAt("07 January 2023", () -> {
            Long loanId = aLoanId.get();
            schedulerJobHelper.executeAndAwaitJob(APPLY_PENALTY_JOB);

            List<GetLoansLoanIdChargesChargeIdResponse> lateFees = getLateFeeLoanCharges(loanId);
            assertEquals(1, lateFees.size());
            GetLoansLoanIdChargesChargeIdResponse lateFee = lateFees.get(0);
            assertEquals(ChargesHelper.CHARGE_LATE_FEE.intValue(), lateFee.getChargeTimeType().getId().intValue());
            assertEquals(Boolean.TRUE, lateFee.getPenalty());
            assertEquals(lateFeeAmount, lateFee.getAmount().doubleValue());

            verifyRepaymentSchedule(loanId, //
                    installment(1000.0, null, "01 January 2023"), //
                    installment(333.33, 0.0, 0.0, lateFeeAmount, 343.33, false, "05 January 2023"), //
                    installment(333.33, 0.0, 0.0, 0.0, 333.33, false, "09 January 2023"), //
                    installment(333.34, 0.0, 0.0, 0.0, 333.34, false, "13 January 2023") //
            );

            // re-running the job must not duplicate the late fee
            schedulerJobHelper.executeAndAwaitJob(APPLY_PENALTY_JOB);
            assertEquals(1, getLateFeeLoanCharges(loanId).size());
        });
    }

    @Test
    public void test_LateFeePercentageCharge_AppliedByPenaltyJob_OnOverdueInstallment() {
        AtomicReference<Long> aLoanId = new AtomicReference<>();

        runAt("01 January 2023", () -> {
            Long clientId = clientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
            // 1% of (amount + interest) of the overdue installment; no interest on the product -> 1% of principal
            Long chargeId = createLateFeePercentageCharge(1.0, ChargesHelper.CHARGE_FEE_FREQUENCY_DAYS, 1);
            Long loanProductId = createNoInterestProductWithCharge(chargeId);
            Long loanId = applyApproveAndDisburse(clientId, loanProductId, 1000.0);
            aLoanId.set(loanId);
        });

        runAt("06 January 2023", () -> {
            Long loanId = aLoanId.get();
            schedulerJobHelper.executeAndAwaitJob(APPLY_PENALTY_JOB);

            List<GetLoansLoanIdChargesChargeIdResponse> lateFees = getLateFeeLoanCharges(loanId);
            assertEquals(1, lateFees.size());
            GetLoansLoanIdChargesChargeIdResponse lateFee = lateFees.get(0);
            assertEquals(ChargesHelper.CHARGE_LATE_FEE.intValue(), lateFee.getChargeTimeType().getId().intValue());
            assertEquals(Boolean.TRUE, lateFee.getPenalty());
            assertEquals(3.33, lateFee.getAmount().doubleValue());

            verifyRepaymentSchedule(loanId, //
                    installment(1000.0, null, "01 January 2023"), //
                    installment(333.33, 0.0, 0.0, 3.33, 336.66, false, "05 January 2023"), //
                    installment(333.33, 0.0, 0.0, 0.0, 333.33, false, "09 January 2023"), //
                    installment(333.34, 0.0, 0.0, 0.0, 333.34, false, "13 January 2023") //
            );
        });
    }

    @Test
    public void test_LateFeeCharge_ExposedAsChargeTimeType17() {
        runAt("01 January 2023", () -> {
            Long chargeId = createLateFeeFlatCharge(10.0, null, 0);
            HashMap charge = ChargesHelper.getChargeById(requestSpec, responseSpec, chargeId.intValue());
            HashMap chargeTimeType = (HashMap) charge.get("chargeTimeType");
            assertEquals(ChargesHelper.CHARGE_LATE_FEE, ((Number) chargeTimeType.get("id")).intValue());
            assertEquals("chargeTimeType.lateFee", chargeTimeType.get("code"));
        });
    }

    private Long createNoInterestProductWithCharge(Long chargeId) {
        int numberOfRepayments = 3;
        int repaymentEvery = 4;
        PostLoanProductsRequest product = createOnePeriod30DaysLongNoInterestPeriodicAccrualProduct() //
                .graceOnArrearsAgeing(0) //
                .numberOfRepayments(numberOfRepayments) //
                .repaymentEvery(repaymentEvery) //
                .installmentAmountInMultiplesOf(null) //
                .repaymentFrequencyType(RepaymentFrequencyType.DAYS.longValue()) //
                .charges(List.of(new LoanProductChargeData().id(chargeId)));
        PostLoanProductsResponse loanProductResponse = loanProductHelper.createLoanProduct(product);
        return loanProductResponse.getResourceId();
    }

    private Long applyApproveAndDisburse(Long clientId, Long loanProductId, double amount) {
        int numberOfRepayments = 3;
        int repaymentEvery = 4;
        PostLoansRequest applicationRequest = applyLoanRequest(clientId, loanProductId, "01 January 2023", amount, numberOfRepayments) //
                .repaymentEvery(repaymentEvery) //
                .loanTermFrequency(numberOfRepayments * repaymentEvery) //
                .repaymentFrequencyType(RepaymentFrequencyType.DAYS) //
                .loanTermFrequencyType(RepaymentFrequencyType.DAYS);

        PostLoansResponse postLoansResponse = loanTransactionHelper.applyLoan(applicationRequest);
        PostLoansLoanIdResponse approvedLoanResult = loanTransactionHelper.approveLoan(postLoansResponse.getResourceId(),
                approveLoanRequest(amount, "01 January 2023"));
        Long loanId = approvedLoanResult.getLoanId();
        disburseLoan(loanId, BigDecimal.valueOf(amount), "01 January 2023");
        return loanId;
    }
}
