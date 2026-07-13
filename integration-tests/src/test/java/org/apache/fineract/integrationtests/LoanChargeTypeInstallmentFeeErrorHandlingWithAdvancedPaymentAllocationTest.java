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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.UUID;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class LoanChargeTypeInstallmentFeeErrorHandlingWithAdvancedPaymentAllocationTest extends BaseLoanIntegrationTest {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder().appendPattern(DATE_FORMAT).toFormatter();

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("POST v1/loans/{loanId}/charges")
        Response addLoanCharge(@Param("loanId") Integer loanId, JsonNode body);
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

    @Disabled
    @Test
    public void addingLoanChargeTypeInstallmentFeeForAdvancedPaymentAllocationGivesErrorTest() {
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
            // apply Installment fee
            Long installmentFeeCharge = chargesHelper.createCharges(new ChargeRequest().active(true).amount(50.0).chargeAppliesTo(1)
                    .chargeCalculationType(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT).currencyCode("USD").locale("en")
                    .monthDayFormat("dd MMM").name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6))
                    .chargeTimeType(ChargesHelper.CHARGE_INSTALLMENT_FEE).chargePaymentMode(0).penalty(false)).getResourceId();

            JsonNode addChargeRequest = RAW_MAPPER.createObjectNode().put("locale", "en").put("dateFormat", DATE_FORMAT).put("amount", "50")
                    .put("chargeId", installmentFeeCharge);
            JsonNode errorResponse = body(RAW.addLoanCharge(loanId, addChargeRequest));
            JsonNode loanChargeErrorData = errorResponse.get("errors");
            assertNotNull(loanChargeErrorData);

            assertEquals(
                    "Charge with identifier %d cannot be applied: Installment fee charges are not supported for Advanced payment allocation strategy"
                            .formatted(installmentFeeCharge),
                    loanChargeErrorData.get(0).get("defaultUserMessage").asText());
            assertEquals("error.msg.charge.cannot.be.applied.toloan",
                    loanChargeErrorData.get(0).get("userMessageGlobalisationCode").asText());

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
}
