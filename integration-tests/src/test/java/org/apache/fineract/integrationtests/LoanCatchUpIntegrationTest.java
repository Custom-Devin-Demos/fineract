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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.batch.domain.BatchRequest;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.common.BatchHelper;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanCOBCatchUpHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(1)
public class LoanCatchUpIntegrationTest extends BaseLoanIntegrationTest {

    private static final String REPAYMENT_LOAN_PERMISSION = "REPAYMENT_LOAN";
    private static final String READ_LOAN_PERMISSION = "READ_LOAN";
    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.ENGLISH);

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    private LoanCOBCatchUpHelper loanCOBCatchUpHelper;

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("POST v1/loans/{loanId}?command={command}")
        Response loanCommand(@Param("loanId") Integer loanId, @Param("command") String command, JsonNode body);

        @RequestLine("POST v1/internal/loans/{loanId}/place-lock/{lockOwner}")
        Response placeSoftLock(@Param("loanId") Integer loanId, @Param("lockOwner") String lockOwner, JsonNode body);
    }

    interface BatchApi {

        @RequestLine("POST v1/batches?enclosingTransaction=false")
        Response postBatchWithoutEnclosingTransaction(JsonNode body);
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
        loanCOBCatchUpHelper = new LoanCOBCatchUpHelper();
    }

    @Test
    public void testCatchUpInLockedInstance() {
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(DATE_FORMATTER.format(LocalDate.of(2020, 3, 2))).dateFormat(DATE_FORMAT).locale("en"));
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.PENALTY_WAIT_PERIOD,
                    new PutGlobalConfigurationsRequest().value(0L));

            final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
            Assertions.assertNotNull(clientID);

            Long overdueFeeChargeId = chargesHelper
                    .createCharges(new ChargeRequest().active(true).amount(1.0).chargeAppliesTo(1)
                            .chargeCalculationType(ChargesHelper.CHARGE_CALCULATION_TYPE_PERCENTAGE_AMOUNT_AND_INTEREST).currencyCode("USD")
                            .locale("en").monthDayFormat("dd MMM").name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6))
                            .chargeTimeType(ChargesHelper.CHARGE_OVERDUE_INSTALLMENT_FEE).chargePaymentMode(0).penalty(true))
                    .getResourceId();
            Assertions.assertNotNull(overdueFeeChargeId);

            final Integer loanProductID = createLoanProduct(overdueFeeChargeId.toString());
            Assertions.assertNotNull(loanProductID);
            final Integer loanID = applyForLoanApplication(clientID.toString(), loanProductID.toString(), null, "1 March 2020");

            Assertions.assertNotNull(loanID);

            LoanStatusChecker.verifyLoanIsPending(LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID));

            loanTransactionHelper.approveLoan(loanID.longValue(),
                    new PostLoansLoanIdRequest().approvedOnDate("01 March 2020").dateFormat(DATE_FORMAT).locale("en"));
            LoanStatusChecker.verifyLoanIsApproved(LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID));

            BigDecimal netDisbursalAmount = loanTransactionHelper.getLoanDetails(loanID.longValue()).getNetDisbursalAmount();
            ObjectNode disburseBody = RAW_MAPPER.createObjectNode();
            disburseBody.put("locale", "en");
            disburseBody.put("dateFormat", DATE_FORMAT);
            disburseBody.put("actualDisbursementDate", "02 March 2020");
            disburseBody.put("netDisbursalAmount", netDisbursalAmount);
            disburseBody.put("note", "DISBURSE NOTE");
            discard(RAW.loanCommand(loanID, "disburse", disburseBody));
            LoanStatusChecker.verifyLoanIsActive(LoanStatusChecker.getStatusOfLoan(requestSpec, responseSpec, loanID));

            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.COB_DATE)
                    .date(DATE_FORMATTER.format(LocalDate.of(2020, 3, 2))).dateFormat(DATE_FORMAT).locale("en"));
            ObjectNode lockBody = RAW_MAPPER.createObjectNode();
            lockBody.put("error", "Sample error");
            discard(RAW.placeSoftLock(loanID, "LOAN_INLINE_COB_PROCESSING", lockBody));

            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(DATE_FORMATTER.format(LocalDate.of(2020, 3, 5))).dateFormat(DATE_FORMAT).locale("en"));

            loanCOBCatchUpHelper.executeLoanCOBCatchUp();

            Utils.conditionalSleepWithMaxWait(30, 5, () -> loanCOBCatchUpHelper.isLoanCOBCatchUpRunning());

            GetLoansLoanIdResponse loan = loanTransactionHelper.getLoanDetails(loanID.longValue());
            Assertions.assertEquals(LocalDate.of(2020, 3, 4), loan.getLastClosedBusinessDate());

            final FineractFeignClient simpleUserClient = createSimpleUserWithoutBypassPermission();

            final BatchRequest br1 = BatchHelper.repayLoanRequestWithGivenLoanId(4730L, loanID, "10", LocalDate.of(2020, 3, 5));

            final List<BatchRequest> batchRequests = new ArrayList<>();

            batchRequests.add(br1);

            final JsonNode response = body(
                    simpleUserClient.create(BatchApi.class).postBatchWithoutEnclosingTransaction(json(new Gson().toJson(batchRequests))));
            Assertions.assertEquals(HttpStatus.SC_OK, response.get(0).get("statusCode").asInt(), "Verify Status Code 200 for Repayment");

            loan = loanTransactionHelper.getLoanDetails(loanID.longValue());
            Assertions.assertEquals(LocalDate.of(2020, 3, 4), loan.getLastClosedBusinessDate());
        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.PENALTY_WAIT_PERIOD,
                    new PutGlobalConfigurationsRequest().value(2L));
        }
    }

    private FineractFeignClient createSimpleUserWithoutBypassPermission() {
        String username = Utils.uniqueRandomStringGenerator("NotificationUser", 4);
        String password = "QwE!5rTy#9uP0";
        Long roleId = RolesHelper.createRole();
        Map<String, Boolean> permissionMap = new HashMap<>();
        permissionMap.put(REPAYMENT_LOAN_PERMISSION, true);
        permissionMap.put(READ_LOAN_PERMISSION, true);
        permissionMap.put("READ_RESCHEDULELOAN", true);
        permissionMap.put("CREATE_RESCHEDULELOAN", true);
        permissionMap.put("REJECT_RESCHEDULELOAN", true);
        permissionMap.put("APPROVE_RESCHEDULELOAN", true);
        RolesHelper.addPermissionsToRole(roleId, permissionMap);
        GetOfficesResponse headOffice = OfficeHelper.getHeadOffice();
        PostUsersRequest createUserRequest = new PostUsersRequest().username(username).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).email("whatever@mifos.org").password(password).repeatPassword(password)
                .sendPasswordToEmail(false).roles(List.of(roleId)).officeId(headOffice.getId());
        Assertions.assertNotNull(UserHelper.createUser(createUserRequest).getResourceId());
        return FineractFeignClientHelper.createNewFineractFeignClient(username, password);
    }

    private Integer createLoanProduct(final String chargeId) {
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal("15,000.00").withNumberOfRepayments("4")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("1")
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestTypeAsDecliningBalance()
                .withLoanScheduleType(LoanScheduleType.CUMULATIVE).build(chargeId);
        return body(RAW.createLoanProduct(json(loanProductJSON))).get("resourceId").intValue();
    }

    private Integer applyForLoanApplication(final String clientID, final String loanProductID, final String savingsID, final String date) {

        List<HashMap> collaterals = new ArrayList<>();
        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(Long.parseLong(clientID), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId.intValue(), BigDecimal.valueOf(1));

        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("15,000.00").withLoanTermFrequency("4")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("4").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("2").withAmortizationTypeAsEqualInstallments()
                .withInterestTypeAsDecliningBalance().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate(date).withSubmittedOnDate(date).withCollaterals(collaterals)
                .build(clientID, loanProductID, savingsID);
        return body(RAW.createLoan(json(loanApplicationJSON))).get("loanId").intValue();
    }

    private void addCollaterals(List<HashMap> collaterals, Integer collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private HashMap<String, String> collaterals(Integer collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

}
