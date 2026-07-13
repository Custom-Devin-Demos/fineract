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

import com.fasterxml.jackson.databind.JsonNode;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.GetChargesResponse;
import org.apache.fineract.client.models.PostChargesResponse;
import org.apache.fineract.client.models.PostTaxesComponentsRequest;
import org.apache.fineract.client.models.PostTaxesComponentsResponse;
import org.apache.fineract.client.models.PostTaxesGroupRequest;
import org.apache.fineract.client.models.PostTaxesGroupResponse;
import org.apache.fineract.client.models.PostTaxesGroupTaxComponents;
import org.apache.fineract.client.models.PutChargesChargeIdRequest;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.TaxComponentHelper;
import org.apache.fineract.integrationtests.common.TaxGroupHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargePaymentMode;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ChargesTest {

    private static final Integer CHARGE_APPLIES_TO_LOAN = 1;
    private static final Integer CHARGE_APPLIES_TO_SAVINGS = 2;
    private static final Double DEFAULT_AMOUNT = 100.0;
    private static final Double MODIFIED_AMOUNT = 200.0;
    private static final String CURRENCY_CODE = "USD";
    private static final String LOCALE = "en";
    private static final String MONTH_DAY_FORMAT = "dd MMM";
    private static final String FEE_ON_MONTH_DAY = "04 March";

    private final ChargesHelper chargesHelper = new ChargesHelper();
    private final FineractFeignClient fineractFeignClient = FineractFeignClientHelper.getFineractFeignClient();

    @Test
    public void testChargesForLoans() {

        // Retrieving all Charges
        final JsonNode allChargesData = raw().retrieveAllCharges();
        Assertions.assertNotNull(allChargesData);

        // Testing Creation, Updation and Deletion of Disbursement Charge
        final Long disbursementChargeId = chargesHelper.createCharges(loanDisbursementCharge()).getResourceId();
        Assertions.assertNotNull(disbursementChargeId);

        // Updating Charge Amount
        PutChargesChargeIdRequest changes = chargesHelper.updateCharges(disbursementChargeId, modifyChargeAmount()).getChanges();

        GetChargesResponse chargeDataAfterChanges = chargesHelper.retrieveCharge(disbursementChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(disbursementChargeId, modifyChargeAsPercentageAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(disbursementChargeId);
        assertChargePaymentMode(changes, chargeDataAfterChanges);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(disbursementChargeId, modifyChargeAsPercentageLoanAmountWithInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(disbursementChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(disbursementChargeId, modifyChargeAsPercentageInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(disbursementChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        Long chargeIdAfterDeletion = chargesHelper.deleteCharge(disbursementChargeId).getResourceId();
        Assertions.assertEquals(disbursementChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Specified due date Charge
        final Long specifiedDueDateChargeId = chargesHelper.createCharges(loanSpecifiedDueDateCharge()).getResourceId();
        Assertions.assertNotNull(specifiedDueDateChargeId);

        // Updating Charge Amount
        changes = chargesHelper.updateCharges(specifiedDueDateChargeId, modifyChargeAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(specifiedDueDateChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(specifiedDueDateChargeId, modifyChargeAsPercentageAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(specifiedDueDateChargeId);
        assertChargePaymentMode(changes, chargeDataAfterChanges);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(specifiedDueDateChargeId, modifyChargeAsPercentageLoanAmountWithInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(specifiedDueDateChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(specifiedDueDateChargeId, modifyChargeAsPercentageInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(specifiedDueDateChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(specifiedDueDateChargeId).getResourceId();
        Assertions.assertEquals(specifiedDueDateChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Installment Fee Charge
        final Long installmentFeeChargeId = chargesHelper.createCharges(loanInstallmentFeeCharge()).getResourceId();

        // Updating Charge Amount
        changes = chargesHelper.updateCharges(installmentFeeChargeId, modifyChargeAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(installmentFeeChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(installmentFeeChargeId, modifyChargeAsPercentageAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(installmentFeeChargeId);
        assertChargePaymentMode(changes, chargeDataAfterChanges);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(installmentFeeChargeId, modifyChargeAsPercentageLoanAmountWithInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(installmentFeeChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(installmentFeeChargeId, modifyChargeAsPercentageInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(installmentFeeChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(installmentFeeChargeId).getResourceId();
        Assertions.assertEquals(installmentFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Overdue Installment Fee
        // Charge
        final Long overdueFeeChargeId = chargesHelper.createCharges(loanOverdueFeeCharge()).getResourceId();
        Assertions.assertNotNull(overdueFeeChargeId);

        // Updating Charge Amount
        changes = chargesHelper.updateCharges(overdueFeeChargeId, modifyChargeAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(overdueFeeChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(overdueFeeChargeId, modifyChargeAsPercentageAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(overdueFeeChargeId);
        assertChargePaymentMode(changes, chargeDataAfterChanges);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(overdueFeeChargeId, modifyChargeAsPercentageLoanAmountWithInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(overdueFeeChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(overdueFeeChargeId, modifyChargeAsPercentageInterest()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(overdueFeeChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        changes = chargesHelper.updateCharges(overdueFeeChargeId, modifyChargeFeeFrequencyAsYears()).getChanges();

        final JsonNode overdueChargeAfterChanges = raw().retrieveCharge(overdueFeeChargeId);
        assertFeeFrequency(changes, overdueChargeAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(overdueFeeChargeId).getResourceId();
        Assertions.assertEquals(overdueFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");
    }

    @Test
    public void testChargesForSavings() {

        // Testing Creation, Updation and Deletion of Specified due date Charge
        final Long specifiedDueDateChargeId = chargesHelper.createCharges(savingsSpecifiedDueDateCharge()).getResourceId();
        Assertions.assertNotNull(specifiedDueDateChargeId);

        // Updating Charge Amount
        PutChargesChargeIdRequest changes = chargesHelper.updateCharges(specifiedDueDateChargeId, modifyChargeAmount()).getChanges();

        GetChargesResponse chargeDataAfterChanges = chargesHelper.retrieveCharge(specifiedDueDateChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        Long chargeIdAfterDeletion = chargesHelper.deleteCharge(specifiedDueDateChargeId).getResourceId();
        Assertions.assertEquals(specifiedDueDateChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Savings Activation Charge
        final Long savingsActivationChargeId = chargesHelper.createCharges(savingsActivationFeeCharge()).getResourceId();
        Assertions.assertNotNull(savingsActivationChargeId);

        // Updating Charge Amount
        changes = chargesHelper.updateCharges(savingsActivationChargeId, modifyChargeAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(savingsActivationChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(savingsActivationChargeId).getResourceId();
        Assertions.assertEquals(savingsActivationChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Withdrawal Fee
        final Long withdrawalFeeChargeId = chargesHelper.createCharges(savingsWithdrawalFeeCharge()).getResourceId();
        Assertions.assertNotNull(withdrawalFeeChargeId);

        // Updating Charge-Calculation-Type to Withdrawal-Fee
        changes = chargesHelper.updateCharges(withdrawalFeeChargeId, modifyWithdrawalFeeSavingsCharge()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(withdrawalFeeChargeId);
        assertChargeCalculationType(changes, chargeDataAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(withdrawalFeeChargeId).getResourceId();
        Assertions.assertEquals(withdrawalFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Annual Fee
        final Long annualFeeChargeId = chargesHelper.createCharges(savingsAnnualFeeCharge()).getResourceId();
        Assertions.assertNotNull(annualFeeChargeId);

        // Updating Charge Amount
        changes = chargesHelper.updateCharges(annualFeeChargeId, modifyChargeAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(annualFeeChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(annualFeeChargeId).getResourceId();
        Assertions.assertEquals(annualFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Monthly Fee
        final Long monthlyFeeChargeId = chargesHelper.createCharges(savingsMonthlyFeeCharge()).getResourceId();
        Assertions.assertNotNull(monthlyFeeChargeId);

        // Updating Charge Amount
        changes = chargesHelper.updateCharges(monthlyFeeChargeId, modifyChargeAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(monthlyFeeChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(monthlyFeeChargeId).getResourceId();
        Assertions.assertEquals(monthlyFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");

        // Testing Creation, Updation and Deletion of Charge for Overdraft Fee
        final Long overdraftFeeChargeId = chargesHelper.createCharges(savingsOverdraftFeeCharge()).getResourceId();
        Assertions.assertNotNull(overdraftFeeChargeId);

        // Updating Charge Amount
        changes = chargesHelper.updateCharges(overdraftFeeChargeId, modifyChargeAmount()).getChanges();

        chargeDataAfterChanges = chargesHelper.retrieveCharge(overdraftFeeChargeId);
        assertAmount(changes, chargeDataAfterChanges);

        chargeIdAfterDeletion = chargesHelper.deleteCharge(overdraftFeeChargeId).getResourceId();
        Assertions.assertEquals(overdraftFeeChargeId, chargeIdAfterDeletion, "Verifying Charge ID after deletion");
    }

    @Test
    public void testChargeUsingPercentageCalculationWithMinAndMaxGapValues() {
        final BigDecimal minCapVal = BigDecimal.valueOf(23);
        final BigDecimal maxCapVal = BigDecimal.valueOf(45);

        final PostChargesResponse feeCharge = chargesHelper.createCharges(
                new ChargeRequest().penalty(false).amount(9.0).chargeCalculationType(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue())
                        .chargeTimeType(ChargeTimeType.DISBURSEMENT.getValue()).chargePaymentMode(ChargePaymentMode.REGULAR.getValue())
                        .currencyCode("USD").name(Utils.randomStringGenerator("FEE_" + Calendar.getInstance().getTimeInMillis(), 5))
                        .chargeAppliesTo(1).locale("en").active(true).minCap(minCapVal).maxCap(maxCapVal));

        Assertions.assertNotNull(feeCharge);
        final Long chargeId = feeCharge.getResourceId();
        Assertions.assertNotNull(chargeId);

        final GetChargesResponse chargeResponseData = chargesHelper.retrieveCharge(chargeId);
        Assertions.assertNotNull(chargeResponseData);
        Assertions.assertEquals(minCapVal.stripTrailingZeros(), chargeResponseData.getMinCap().stripTrailingZeros());
        Assertions.assertEquals(maxCapVal.stripTrailingZeros(), chargeResponseData.getMaxCap().stripTrailingZeros());
    }

    @Test
    public void testChargeCreationWithTaxGroup() {
        final PostTaxesComponentsRequest taxComponentRequest = new PostTaxesComponentsRequest()
                .name(Utils.randomStringGenerator("TAX_COM_", 4)).percentage(12.0f).startDate("01 January 2023").dateFormat("dd MMMM yyyy")
                .locale("en");

        final PostTaxesComponentsResponse taxComponentRespose = TaxComponentHelper.createTaxComponent(taxComponentRequest);
        Assertions.assertNotNull(taxComponentRequest);

        final Set<PostTaxesGroupTaxComponents> taxComponentsSet = new HashSet<>();
        taxComponentsSet
                .add(new PostTaxesGroupTaxComponents().taxComponentId(taxComponentRespose.getResourceId()).startDate("01 January 2023"));
        final PostTaxesGroupRequest taxGroupRequest = new PostTaxesGroupRequest().name(Utils.randomStringGenerator("TAX_GRP_", 4))
                .taxComponents(taxComponentsSet).dateFormat("dd MMMM yyyy").locale("en");
        final PostTaxesGroupResponse taxGroupResponse = TaxGroupHelper.createTaxGroup(taxGroupRequest);
        Assertions.assertNotNull(taxGroupResponse);

        final PostChargesResponse feeCharge = chargesHelper.createCharges(
                new ChargeRequest().penalty(false).amount(9.0).chargeCalculationType(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue())
                        .chargeTimeType(ChargeTimeType.DISBURSEMENT.getValue()).chargePaymentMode(ChargePaymentMode.REGULAR.getValue())
                        .currencyCode("USD").name(Utils.randomStringGenerator("FEE_" + Calendar.getInstance().getTimeInMillis(), 5))
                        .chargeAppliesTo(1).locale("en").active(true).taxGroupId(taxGroupResponse.getResourceId()));

        Assertions.assertNotNull(feeCharge);
        final Long chargeId = feeCharge.getResourceId();
        Assertions.assertNotNull(chargeId);

        final GetChargesResponse chargeResponseData = chargesHelper.retrieveCharge(chargeId);
        Assertions.assertNotNull(chargeResponseData);
        Assertions.assertNotNull(chargeResponseData.getTaxGroup());
        Assertions.assertEquals(chargeResponseData.getTaxGroup().getId(), taxGroupResponse.getResourceId());
    }

    private void assertAmount(final PutChargesChargeIdRequest changes, final GetChargesResponse chargeDataAfterChanges) {
        Assertions.assertEquals(changes.getAmount().doubleValue(), chargeDataAfterChanges.getAmount().doubleValue(),
                "Verifying Charge after Modification");
    }

    private void assertChargePaymentMode(final PutChargesChargeIdRequest changes, final GetChargesResponse chargeDataAfterChanges) {
        Assertions.assertEquals(changes.getChargePaymentMode().longValue(),
                chargeDataAfterChanges.getChargePaymentMode().getId().longValue(), "Verifying Charge after Modification");
    }

    private void assertChargeCalculationType(final PutChargesChargeIdRequest changes, final GetChargesResponse chargeDataAfterChanges) {
        Assertions.assertEquals(changes.getChargeCalculationType().longValue(),
                chargeDataAfterChanges.getChargeCalculationType().getId().longValue(), "Verifying Charge after Modification");
    }

    private void assertFeeFrequency(final PutChargesChargeIdRequest changes, final JsonNode chargeDataAfterChanges) {
        Assertions.assertEquals(Long.parseLong(changes.getFeeFrequency()), chargeDataAfterChanges.get("feeFrequency").get("id").asLong(),
                "Verifying Charge after Modification");
    }

    private ChargeRequest loanDefaults() {
        return new ChargeRequest().active(true).amount(DEFAULT_AMOUNT).chargeAppliesTo(CHARGE_APPLIES_TO_LOAN)
                .chargeCalculationType(ChargeCalculationType.FLAT.getValue()).currencyCode(CURRENCY_CODE).locale(LOCALE)
                .monthDayFormat(MONTH_DAY_FORMAT).name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6));
    }

    private ChargeRequest savingsDefaults() {
        return new ChargeRequest().active(true).amount(DEFAULT_AMOUNT).chargeAppliesTo(CHARGE_APPLIES_TO_SAVINGS)
                .chargeCalculationType(ChargeCalculationType.FLAT.getValue()).currencyCode(CURRENCY_CODE).locale(LOCALE)
                .monthDayFormat(MONTH_DAY_FORMAT).name(Utils.uniqueRandomStringGenerator("Charge_Savings_", 6));
    }

    private ChargeRequest loanDisbursementCharge() {
        return loanDefaults().chargeTimeType(ChargeTimeType.DISBURSEMENT.getValue())
                .chargePaymentMode(ChargePaymentMode.REGULAR.getValue());
    }

    private ChargeRequest loanSpecifiedDueDateCharge() {
        return loanDefaults().chargeTimeType(ChargeTimeType.SPECIFIED_DUE_DATE.getValue())
                .chargePaymentMode(ChargePaymentMode.REGULAR.getValue()).penalty(true);
    }

    private ChargeRequest loanInstallmentFeeCharge() {
        return loanDefaults().chargeTimeType(ChargeTimeType.INSTALMENT_FEE.getValue())
                .chargePaymentMode(ChargePaymentMode.REGULAR.getValue()).penalty(true);
    }

    private ChargeRequest loanOverdueFeeCharge() {
        return loanDefaults().penalty(true).chargePaymentMode(ChargePaymentMode.REGULAR.getValue())
                .chargeTimeType(ChargeTimeType.OVERDUE_INSTALLMENT.getValue())
                .feeFrequency(String.valueOf(ChargesHelper.CHARGE_FEE_FREQUENCY_MONTHS)).feeOnMonthDay(FEE_ON_MONTH_DAY).feeInterval("2");
    }

    private ChargeRequest savingsSpecifiedDueDateCharge() {
        return savingsDefaults().chargeTimeType(ChargeTimeType.SPECIFIED_DUE_DATE.getValue()).feeInterval("2");
    }

    private ChargeRequest savingsActivationFeeCharge() {
        return savingsDefaults().chargeTimeType(ChargeTimeType.SAVINGS_ACTIVATION.getValue());
    }

    private ChargeRequest savingsWithdrawalFeeCharge() {
        return savingsDefaults().chargeTimeType(ChargeTimeType.WITHDRAWAL_FEE.getValue());
    }

    private ChargeRequest savingsAnnualFeeCharge() {
        return savingsDefaults().feeOnMonthDay(FEE_ON_MONTH_DAY).chargeTimeType(ChargeTimeType.ANNUAL_FEE.getValue());
    }

    private ChargeRequest savingsMonthlyFeeCharge() {
        return savingsDefaults().feeOnMonthDay(FEE_ON_MONTH_DAY).chargeTimeType(ChargeTimeType.MONTHLY_FEE.getValue()).feeInterval("2");
    }

    private ChargeRequest savingsOverdraftFeeCharge() {
        return savingsDefaults().chargeTimeType(ChargeTimeType.OVERDRAFT_FEE.getValue());
    }

    private ChargeRequest modifyChargeAmount() {
        return new ChargeRequest().locale(LOCALE).amount(MODIFIED_AMOUNT);
    }

    private ChargeRequest modifyChargeAsPercentageAmount() {
        return new ChargeRequest().locale(LOCALE).chargeCalculationType(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue())
                .chargePaymentMode(ChargePaymentMode.ACCOUNT_TRANSFER.getValue());
    }

    private ChargeRequest modifyChargeAsPercentageLoanAmountWithInterest() {
        return new ChargeRequest().locale(LOCALE).chargeCalculationType(ChargeCalculationType.PERCENT_OF_AMOUNT_AND_INTEREST.getValue())
                .chargePaymentMode(ChargePaymentMode.ACCOUNT_TRANSFER.getValue());
    }

    private ChargeRequest modifyChargeAsPercentageInterest() {
        return new ChargeRequest().locale(LOCALE).chargeCalculationType(ChargeCalculationType.PERCENT_OF_INTEREST.getValue())
                .chargePaymentMode(ChargePaymentMode.ACCOUNT_TRANSFER.getValue());
    }

    private ChargeRequest modifyWithdrawalFeeSavingsCharge() {
        return new ChargeRequest().locale(LOCALE).chargeCalculationType(ChargeCalculationType.PERCENT_OF_AMOUNT.getValue());
    }

    private ChargeRequest modifyChargeFeeFrequencyAsYears() {
        return new ChargeRequest().locale(LOCALE).feeFrequency(String.valueOf(ChargesHelper.CHARGE_FEE_FREQUENCY_YEARS)).feeInterval("2");
    }

    private ChargesRawApi raw() {
        return this.fineractFeignClient.create(ChargesRawApi.class);
    }

    /**
     * Raw JsonNode views of the charge endpoints, used through fineract-client-feign for assertions whose values are
     * not exposed by the generated typed responses: the list endpoint's {@code ChargeData} model cannot deserialize
     * {@code feeOnMonthDay} (spec declares an object, the server returns a string), and {@code GetChargesResponse} does
     * not expose {@code feeFrequency}.
     */
    interface ChargesRawApi {

        @RequestLine("GET /v1/charges")
        @Headers("Accept: application/json")
        JsonNode retrieveAllCharges();

        @RequestLine("GET /v1/charges/{chargeId}")
        @Headers("Accept: application/json")
        JsonNode retrieveCharge(@Param("chargeId") Long chargeId);
    }
}
