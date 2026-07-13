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
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.client.models.PaymentTypeCreateRequest;
import org.apache.fineract.client.models.PaymentTypeData;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LoanTestLifecycleExtension.class)
public class RepaymentWithPostDatedChecksTest {

    private final SimpleDateFormat dateFormatterStandard = new SimpleDateFormat("dd MMMM yyyy", Locale.US);

    // Typed Feign layer used to submit/fetch the exact request and response payloads that the assertions in this test
    // rely on, so the deprecated RestAssured-based helpers can be replaced while preserving every payload, response
    // shape, status code and assertion. Bodies are parsed with the same JsonPath machinery the helpers used internally.
    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("GET v1/loans/{loanId}")
        Response loan(@Param("loanId") Integer loanId);

        @RequestLine("GET v1/loans/{loanId}/transactions/template?command=disburse")
        Response repaymentsTemplate(@Param("loanId") Integer loanId);

        @RequestLine("GET v1/loans/{loanId}/postdatedchecks/{installmentId}")
        Response postDatedCheck(@Param("loanId") Integer loanId, @Param("installmentId") Integer installmentId);

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("POST v1/loans/{loanId}?command={command}")
        Response loanCommand(@Param("loanId") Integer loanId, @Param("command") String command, JsonNode body);

        @RequestLine("POST v1/loans/{loanId}/transactions?command={command}")
        Response loanTransactionCommand(@Param("loanId") Integer loanId, @Param("command") String command, JsonNode body);

        @RequestLine("POST v1/collateral-management")
        Response createCollateralProduct(JsonNode body);

        @RequestLine("POST v1/clients/{clientId}/collaterals")
        Response createClientCollateral(@Param("clientId") String clientId, JsonNode body);
    }

    private static String rawBody(Response response) {
        try (Response r = response) {
            return Util.toString(r.body().asReader(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode rawJson(String json) {
        try {
            return RAW_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T extract(String body, String path) {
        return JsonPath.from(body).get(path);
    }

    @Test
    public void testRepaymentWithPostDatedChecks() {
        Calendar meetingCalendar = Calendar.getInstance();
        meetingCalendar.set(2012, 3, 4);

        final String disbursalDate = this.dateFormatterStandard.format(meetingCalendar.getTime());

        final Integer clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        Assertions.assertNotNull(clientID);
        ClientHelper.verifyClientCreatedOnServer(clientID.longValue());

        final Integer loanProductID = createLoanProduct(new LoanProductTestBuilder().build(null));
        Assertions.assertNotNull(loanProductID, "Could not create Loan Product");

        final Integer loanID = applyForLoanApplication(clientID, loanProductID, "8000");
        Assertions.assertNotNull(loanID, "Could not create Loan Account");

        HashMap loanStatusHashMap = getStatusOfLoan(loanID);

        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        // Test for loan account is created, can be approved
        approveLoan(disbursalDate, loanID);
        loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);

        // Get repayments Template for Repayment
        ArrayList<HashMap> installmentData = getRepayments(loanID);
        Assertions.assertNotNull(installmentData, "Empty Installment Data Template");

        // Get repayments for Disburse
        installmentData = getRepayments(loanID);
        Assertions.assertNotNull(installmentData, "Empty Installment Data");
        List<HashMap> postDatedChecks = new ArrayList<>();
        Gson gson = new Gson();

        DateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.US);
        dateFormat.setTimeZone(Utils.getTimeZoneOfTenant());

        // Get the first installment date
        ArrayList installmentDate = (ArrayList) installmentData.get(0).get("date");
        Assertions.assertNotNull(installmentDate);
        Assertions.assertEquals(3, installmentDate.size());
        Calendar calendar = Calendar.getInstance();
        calendar.set((Integer) installmentDate.get(0), (Integer) installmentDate.get(1) - 1, (Integer) installmentDate.get(2));
        final String LOAN_REPAYMENT_DATE = dateFormat.format(calendar.getTime());
        Float firstInstallmentAmount = (Float) installmentData.get(0).get("amount");

        for (int i = 0; i < installmentData.size(); i++) {
            String result = gson.toJson(installmentData.get(i));
            JsonObject reportObject = JsonParser.parseString(result).getAsJsonObject();
            final Integer installmentId = reportObject.get("installmentId").getAsInt();
            final BigDecimal amount = reportObject.get("amount").getAsBigDecimal();
            postDatedChecks.add(postDatedCheck(installmentId, amount));
        }

        Assertions.assertNotNull(postDatedChecks);

        // Test for loan account approved can be disbursed
        disburseLoanWithPostDatedChecks(disbursalDate, loanID, BigDecimal.valueOf(8000), postDatedChecks);
        loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);

        // Create payment type PDC - Post Dated Checks
        String name = "PDC";
        String description = PaymentTypeHelper.randomNameGenerator("PDC", 15);
        Boolean isCashPayment = false;
        Long position = 1L;

        var paymentTypesResponse = PaymentTypeHelper.createPaymentType(
                new PaymentTypeCreateRequest().name(name).description(description).isCashPayment(isCashPayment).position(position));
        Long paymentTypeId = paymentTypesResponse.getResourceId();
        Assertions.assertNotNull(paymentTypeId);
        PaymentTypeHelper.verifyPaymentTypeCreatedOnServer(paymentTypeId);
        PaymentTypeData paymentTypeResponse = PaymentTypeHelper.retrieveById(paymentTypeId);
        Assertions.assertEquals(name, paymentTypeResponse.getName());

        // Repay for the installment 1 using post dated check
        HashMap postDatedCheck = getPostDatedCheck(loanID, Integer.valueOf(1));
        Assertions.assertNotNull(postDatedCheck);
        Assertions.assertNotNull(Float.valueOf(String.valueOf(postDatedCheck.get("amount"))));

        makeRepaymentWithPDC(LOAN_REPAYMENT_DATE, firstInstallmentAmount, loanID, paymentTypeId);
    }

    private Integer applyForLoanApplication(final Integer clientID, final Integer loanProductID, final String proposedAmount) {
        List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = createClientCollateral(clientID.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String loanApplication = new LoanApplicationTestBuilder().withPrincipal(proposedAmount).withLoanTermFrequency("5")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("5").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("2").withExpectedDisbursementDate("04 April 2012")
                .withCollaterals(collaterals).withSubmittedOnDate("02 April 2012")
                .build(clientID.toString(), loanProductID.toString(), null);
        return getLoanId(loanApplication);
    }

    private void addCollaterals(List<HashMap> collaterals, Integer collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private HashMap<String, String> collaterals(Integer collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<String, String>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

    private HashMap<String, String> postDatedCheck(final Integer installmentId, final BigDecimal amount) {
        HashMap<String, String> map = new HashMap<String, String>();
        map.put("installmentId", installmentId.toString());
        map.put("name", "AMANA BANK");
        map.put("amount", amount.toString());
        map.put("accountNo", "900400500621");
        map.put("checkNo", Utils.uniqueRandomNumberGenerator(9).toString());

        return map;
    }

    // ---- Local adapters preserving the exact payloads/response shapes of the removed deprecated helpers -------------

    private static HashMap getStatusOfLoan(final Integer loanID) {
        return extract(rawBody(RAW.loan(loanID)), "status");
    }

    private static Integer createLoanProduct(final String loanProductJSON) {
        return extract(rawBody(RAW.createLoanProduct(rawJson(loanProductJSON))), "resourceId");
    }

    private static Integer getLoanId(final String loanApplicationJSON) {
        return extract(rawBody(RAW.createLoan(rawJson(loanApplicationJSON))), "loanId");
    }

    private static void approveLoan(final String approvalDate, final Integer loanID) {
        final Map<String, Object> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("approvedOnDate", approvalDate);
        map.put("note", "Approval NOTE");
        rawBody(RAW.loanCommand(loanID, "approve", rawJson(new Gson().toJson(map))));
    }

    private static ArrayList<HashMap> getRepayments(final Integer loanID) {
        return extract(rawBody(RAW.repaymentsTemplate(loanID)), "loanRepaymentScheduleInstallments");
    }

    private static void disburseLoanWithPostDatedChecks(final String date, final Integer loanId, final BigDecimal transactionAmount,
            final List<HashMap> postDatedChecks) {
        final Map<String, Object> map = new HashMap<>();
        map.put("locale", "en");
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("actualDisbursementDate", date);
        map.put("note", "DISBURSE NOTE");
        map.put("transactionAmount", transactionAmount.toString());
        map.put("postDatedChecks", postDatedChecks);
        rawBody(RAW.loanCommand(loanId, "disburse", rawJson(new Gson().toJson(map))));
    }

    private static HashMap getPostDatedCheck(final Integer loanId, final Integer installmentId) {
        return extract(rawBody(RAW.postDatedCheck(loanId, installmentId)), "");
    }

    private static void makeRepaymentWithPDC(final String date, final Float amountToBePaid, final Integer loanID, final Long paymentType) {
        final Map<String, Object> map = new HashMap<>();
        map.put("locale", "en");
        map.put("paymentTypeId", paymentType.toString());
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("transactionDate", date);
        map.put("transactionAmount", amountToBePaid.toString());
        map.put("note", "Repayment Made!!!");
        rawBody(RAW.loanTransactionCommand(loanID, "repayment", rawJson(new Gson().toJson(map))));
    }

    private static Integer createCollateralProduct() {
        final Map<String, String> map = new HashMap<>();
        map.put("name", Utils.randomStringGenerator("COLLATERAL_PRODUCT", 5));
        map.put("currency", "USD");
        map.put("unitType", "acre");
        map.put("quality", "agriculture");
        map.put("pctToBase", BigDecimal.valueOf(40).toString());
        map.put("basePrice", BigDecimal.valueOf(100000000).toString());
        map.put("locale", "en");
        return extract(rawBody(RAW.createCollateralProduct(rawJson(new Gson().toJson(map)))), "resourceId");
    }

    private static Integer createClientCollateral(final String clientId, final Integer collateralId) {
        final Map<String, String> map = new HashMap<>();
        map.put("collateralId", collateralId.toString());
        map.put("quantity", BigDecimal.valueOf(100).toString());
        map.put("locale", "en");
        return extract(rawBody(RAW.createClientCollateral(clientId, rawJson(new Gson().toJson(map)))), "resourceId");
    }

}
