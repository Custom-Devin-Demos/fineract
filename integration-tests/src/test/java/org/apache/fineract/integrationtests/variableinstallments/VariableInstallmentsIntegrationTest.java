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
package org.apache.fineract.integrationtests.variableinstallments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({ "rawtypes", "unchecked" })
@ExtendWith(LoanTestLifecycleExtension.class)
public class VariableInstallmentsIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(VariableInstallmentsIntegrationTest.class);
    private static final String NONE = "1";

    // Local non-deprecated request layer. The typed fineract-client-feign SDK does not expose the raw, untyped response
    // shapes (nested HashMap/ArrayList with the number typing) or the exact request payloads that the assertions in
    // this
    // test rely on, so a minimal Feign interface is used to submit/fetch the exact server payloads which are then
    // parsed
    // with the same JsonPath machinery the deprecated helpers used internally.
    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    private final VariableIntallmentsTransactionHelper transactionHelper = new VariableIntallmentsTransactionHelper();

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("GET v1/loanproducts/{loanProductId}?associations=all")
        Response loanProductDetails(@Param("loanProductId") Integer loanProductId);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("GET v1/loans/{loanId}")
        Response loan(@Param("loanId") Integer loanId);
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

    private Integer getLoanProductId(final String loanProductJSON) {
        final HashMap response = JsonPath.from(rawBody(RAW.createLoanProduct(rawJson(loanProductJSON)))).get("");
        return (Integer) response.get("resourceId");
    }

    private Map getLoanProductDetail(final Integer loanProductId) {
        return JsonPath.from(rawBody(RAW.loanProductDetails(loanProductId))).get("");
    }

    private Integer getLoanId(final String loanApplicationJSON) {
        final HashMap response = JsonPath.from(rawBody(RAW.createLoan(rawJson(loanApplicationJSON)))).get("");
        return (Integer) response.get("loanId");
    }

    private HashMap<String, Object> getStatusOfLoan(final Integer loanID) {
        final HashMap response = JsonPath.from(rawBody(RAW.loan(loanID))).get("");
        return (HashMap<String, Object>) response.get("status");
    }

    @Test
    public void testVariableLoanProductCreation() {
        final String json = decliningLoanProductWithVaribleConfig();
        final Integer loanProductID = getLoanProductId(json);
        LOG.info("------------------------------RETRIEVING CREATED LOAN PRODUCT DETAILS ---------------------------------------");
        Map loanProduct = getLoanProductDetail(loanProductID);
        Assertions.assertTrue((Boolean) loanProduct.get("allowVariableInstallments"));
        Assertions.assertEquals(Integer.valueOf(5), loanProduct.get("minimumGap"));
        Assertions.assertEquals(Integer.valueOf(90), loanProduct.get("maximumGap"));
    }

    @Test
    public void testLoanProductCreation() {
        final String josn = decliningLoanProductWithoutVaribleConfig();
        Integer loanProductID = getLoanProductId(josn);
        LOG.info("------------------------------RETRIEVING CREATED LOAN PRODUCT DETAILS ---------------------------------------");
        Map loanProduct = getLoanProductDetail(loanProductID);
        Assertions.assertTrue(!(Boolean) loanProduct.get("allowVariableInstallments"));
    }

    @Test
    public void testDeleteInstallmentsWithDecliningBalanceEqualInstallments() {
        final String loanProductJson = decliningLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);

        List<HashMap> collaterals = new ArrayList<>();
        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

        final String json = decliningApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 49 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        ArrayList toDelete = new ArrayList<>();
        toDelete.add(periods.get(1));
        String toDeletedata = createDeleteVariations(toDelete);
        HashMap modifiedReschdule = transactionHelper.validateVariations(toDeletedata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(new String[] { "20 November 2011", "20 December 2011", "20 January 2012" },
                new String[] { "34675.47", "34675.47", "36756.26" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(toDeletedata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);

    }

    private HashMap<String, String> collaterals(Long collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<String, String>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

    private void addCollaterals(List<HashMap> collaterals, Long collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private void assertAfterSubmit(ArrayList<Map> serverData, ArrayList<Map> expectedData) {
        Assertions.assertTrue(serverData.size() == expectedData.size());
        for (int i = 0; i < serverData.size(); i++) {
            Map<String, Object> serverMap = serverData.get(i);
            Map<String, Object> expectedMap = expectedData.get(i);
            Assertions.assertTrue(formatDate((ArrayList) serverMap.get("dueDate")).equals(expectedMap.get("dueDate")));
            Assertions.assertTrue(serverMap.get("totalOutstandingForPeriod").toString().equals(expectedMap.get("installmentAmount")));
        }
    }

    @Test
    public void testAddInstallmentsWithDecliningBalanceEqualInstallments() {
        // 31 October 2011 - 5000
        // Result: 20 October 2011 - 21,215.84, 31 October 2011 - 5000, 20
        // November 2011 26,477.31, 20 December 2011 26,477.31, 20 January 2012
        // 25,947.7
        final String loanProductJson = decliningLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

        final String json = decliningApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 57 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        String addVariationsjsondata = decliningCreateAddVariations();
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "31 October 2011", "20 November 2011", "20 December 2011", "20 January 2012" },
                new String[] { "21215.84", "5000.0", "26477.31", "26477.31", "25947.7" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    @Test
    public void testModifyInstallmentWithDecliningBalanceEqualInstallments() {
        // 20 October 2011 - 30000 modify
        // Result 20 October 2011 - 30000.0, 20 November 2011 - 24,966.34, 20
        // December 2011 - 24,966.34, 20 January 2012 - 24,966.33
        final String loanProductJson = decliningLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

        final String json = decliningApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 57 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        String addVariationsjsondata = decliningCreateModifiyVariations((Map) periods.get(1)); // 0th
                                                                                               // position
                                                                                               // will
                                                                                               // have
                                                                                               // disbursement
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "20 November 2011", "20 December 2011", "20 January 2012" },
                new String[] { "30000.0", "24966.34", "24966.34", "24966.33" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);

    }

    @Test
    public void testAllVariationsDecliningBalancewithEqualInstallments() {
        // Request: Delete 20 December 2011 26,262.38, Modify 20 November 2011
        // from 26,262.38 to 30000, Add 25 December 2011 5000
        // Result: 20 October 2011 - 26262.38, 20 November 2011 - 30000, 25
        // December 2011 - 5000, 20 January 2012 - 44077
        final String loanProductJson = decliningLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = decliningApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 57 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");

        String addVariationsjsondata = decliningCreateAllVariations(); // 0th
                                                                       // position
                                                                       // will
                                                                       // have
                                                                       // disbursement
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "20 November 2011", "25 December 2011", "20 January 2012" },
                new String[] { "26262.38", "30000.0", "5000.0", "44077.0" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    @Test
    public void testAllVariationsDecliningBalancewithEqualPrincipal() {
        // Request: Delete 20 December 2011 26,262.38, Modify 20 November 2011
        // from 26,262.38 to 30000, Add 25 December 2011 5000
        // Result: 20 October 2011 - 27000.0, 20 November 2011 - 31500.0, 25
        // December 2011 - 6045.16, 20 January 2012 - 40670.97
        final String loanProductJson = decliningLoanProductWithVaribleConfigwithEqualPrincipal();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = decliningApplyForLoanApplicationWithEqualPrincipal(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 109 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");

        String addVariationsjsondata = decliningCreateAllVariationsWithEqualPrincipal(); // 0th
                                                                                         // position
                                                                                         // will
                                                                                         // have
                                                                                         // disbursement
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "20 November 2011", "25 December 2011", "20 January 2012" },
                new String[] { "27000.0", "31500.0", "6045.16", "40670.97" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    @Test
    public void testModifyDatesWithDecliningBalanceEqualInstallments() {
        // Modify 20 December 2011:25000 -> 04 January 2012:20000
        // Modify 20 January 2012 -> 08 February 2012
        // Result 20 October 2011 -26262.38, 20 November 2011 - 26262.38, 04
        // January 2012 -20000, 08 February 2012 - 33242.97
        final String loanProductJson = decliningLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = decliningApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 57 ;

        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        //
        //
        String addVariationsjsondata = decliningCreateModifiyDateVariations(new String[] { "20 December 2011", "20 January 2012" },
                new String[] { "04 January 2012", "08 February 2012" }, new String[] { "20000" }); // 0th position will
                                                                                                   // have
                                                                                                   // disbursement
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "20 November 2011", "04 January 2012", "08 February 2012" },
                new String[] { "26262.38", "26262.38", "20000.0", "33242.97" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    // Interest Type is FLAT
    @Test
    public void testDeleteInstallmentsWithInterestTypeFlat() {
        final String loanProductJson = flatLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = flatApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        ArrayList toDelete = new ArrayList<>();
        toDelete.add(periods.get(1));
        String toDeletedata = createDeleteVariations(toDelete);
        HashMap modifiedReschdule = transactionHelper.validateVariations(toDeletedata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(new String[] { "20 November 2011", "20 December 2011", "20 January 2012" },
                new String[] { "36000.0", "36000.0", "36000.0" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(toDeletedata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    @Test
    public void testAddInstallmentsWithInterestTypeFlat() {
        // 31 October 2011 - 5000
        // Result: 20 October 2011 - 21600.0, 31 October 2011 - 6600.0, 20
        // November 2011 26600.0, 20 December 2011 26600.0, 20 January 2012
        // 26600.0
        final String loanProductJson = flatLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = flatApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 67 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        String addVariationsjsondata = flatCreateAddVariations();
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "31 October 2011", "20 November 2011", "20 December 2011", "20 January 2012" },
                new String[] { "21600.0", "6600.0", "26600.0", "26600.0", "26600.0" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    @Test
    public void testModifyInstallmentsWithInterestTypeisFlat() {
        // 20 October 2011 - 30000 modify
        // Result 20 October 2011 - 32000.0, 20 November 2011 - 25333.33, 20
        // December 2011 - 25333.33, 20 January 2012 - 25333.34
        final String loanProductJson = flatLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();

        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = flatApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 67 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        String addVariationsjsondata = flatCreateModifiyVariations((Map) periods.get(1)); // 0th
                                                                                          // position
                                                                                          // will
                                                                                          // have
                                                                                          // disbursement
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "20 November 2011", "20 December 2011", "20 January 2012" },
                new String[] { "32000.0", "25333.33", "25333.33", "25333.34" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    @Test
    public void testAllVariationsWithInterestTypeFlat() {
        // Request: Delete 20 December 2011 25000.0, Modify 20 November 2011
        // from 25,000 to 30000, Add 25 December 2011 5000
        // Result: 20 October 2011 - 27000.0, 20 November 2011 - 32000.0, 25
        // December 2011 - 7000.0, 20 January 2012 - 42000.0
        final String loanProductJson = flatLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = flatApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 67 ;
        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");

        String addVariationsjsondata = flatCreateAllVariations(); // 0th
                                                                  // position
                                                                  // will
                                                                  // have
                                                                  // disbursement
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "20 November 2011", "25 December 2011", "20 January 2012" },
                new String[] { "27000.0", "32000.0", "7000.0", "42000.0" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    @Test
    public void testModifyDatesWithInterestTypeFlat() {
        // Modify 20 December 2011:25000 -> 04 January 2012:20000
        // Modify 20 January 2012 -> 08 February 2012
        // Result 20 October 2011 -27306.45, 20 November 2011 - 27306.45, 04
        // January 2012 -22306.45, 08 February 2012 - 32306.46
        final String loanProductJson = flatLoanProductWithVaribleConfig();
        Integer loanProductID = getLoanProductId(loanProductJson);
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        final Long clientID = clientResponse.getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);
        List<HashMap> collaterals = new ArrayList<>();

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String json = flatApplyForLoanApplication(clientID, loanProductID, "1,00,000.00", collaterals);
        final Integer loanID = getLoanId(json);
        HashMap loanStatusHashMap = getStatusOfLoan(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);
        // Integer loanID = 67 ;

        Map list = transactionHelper.retrieveSchedule(loanID);
        Map repaymentSchedule = (Map) list.get("repaymentSchedule");
        ArrayList periods = (ArrayList) repaymentSchedule.get("periods");
        //
        //
        String addVariationsjsondata = flatCreateModifiyDateVariations(new String[] { "20 December 2011", "20 January 2012" },
                new String[] { "04 January 2012", "08 February 2012" }, new String[] { "20000" }); // 0th position will
                                                                                                   // have
                                                                                                   // disbursement
        HashMap modifiedReschdule = transactionHelper.validateVariations(addVariationsjsondata, loanID);
        ArrayList newperiods = (ArrayList) modifiedReschdule.get("periods");
        ArrayList toVerifyData = constructVerifyData(
                new String[] { "20 October 2011", "20 November 2011", "04 January 2012", "08 February 2012" },
                new String[] { "27306.45", "27306.45", "22306.45", "32306.46" });
        assertAfterSubmit(newperiods, toVerifyData);
        transactionHelper.submitVariations(addVariationsjsondata, loanID);
        list = transactionHelper.retrieveSchedule(loanID);
        repaymentSchedule = (Map) list.get("repaymentSchedule");
        periods = (ArrayList) repaymentSchedule.get("periods");
        periods.remove(0); // Repayments Schedule includes disbursement also. So
                           // remove this.
        assertAfterSubmit(periods, toVerifyData);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Loan product / application payload builders. These mirror the payloads previously produced by the deprecated
    // VariableInstallmentsDecliningBalanceHelper / VariableInstallmentsFlatHelper, built here with the non-deprecated
    // LoanProductTestBuilder / LoanApplicationTestBuilder so the exact request bodies (and therefore validation and
    // schedule results) are preserved.
    // ----------------------------------------------------------------------------------------------------------------
    private static String decliningLoanProductWithoutVaribleConfig() {
        return new LoanProductTestBuilder() //
                .withPrincipal("1,00,000.00") //
                .withNumberOfRepayments("4") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withTranches(false) //
                .withAccounting(NONE, new Account[0]).build(null);
    }

    private static String decliningLoanProductWithVaribleConfig() {
        return new LoanProductTestBuilder() //
                .withPrincipal("1,00,000.00") //
                .withNumberOfRepayments("4") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withTranches(false) //
                .withInterestCalculationPeriodTypeAsRepaymentPeriod(true)//
                .withVariableInstallmentsConfig(Boolean.TRUE, Integer.valueOf(5), Integer.valueOf(90))//
                .withAccounting(NONE, new Account[0]).build(null);
    }

    private static String decliningLoanProductWithVaribleConfigwithEqualPrincipal() {
        return new LoanProductTestBuilder() //
                .withPrincipal("1,00,000.00") //
                .withNumberOfRepayments("4") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .withTranches(false) //
                .withInterestCalculationPeriodTypeAsRepaymentPeriod(true)//
                .withVariableInstallmentsConfig(Boolean.TRUE, Integer.valueOf(5), Integer.valueOf(90))//
                .withAccounting(NONE, new Account[0]).build(null);
    }

    private static String flatLoanProductWithVaribleConfig() {
        return new LoanProductTestBuilder() //
                .withPrincipal("1,00,000.00") //
                .withNumberOfRepayments("4") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withAmortizationTypeAsEqualPrincipalPayment().withInterestTypeAsFlat() //
                .withTranches(false) //
                .withInterestCalculationPeriodTypeAsRepaymentPeriod(true)//
                .withVariableInstallmentsConfig(Boolean.TRUE, Integer.valueOf(5), Integer.valueOf(90))//
                .withAccounting(NONE, new Account[0]).build(null);
    }

    private static String decliningApplyForLoanApplication(final Long clientID, final Integer loanProductID, String principal,
            List<HashMap> collaterals) {
        return new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("4") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("4") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate("20 September 2011") //
                .withSubmittedOnDate("20 September 2011") //
                .withCollaterals(collaterals).withCharges(null).build(clientID.toString(), loanProductID.toString(), null);
    }

    private static String decliningApplyForLoanApplicationWithEqualPrincipal(final Long clientID, final Integer loanProductID,
            String principal, List<HashMap> collaterals) {
        return new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("4") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("4") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualPrincipalPayments() //
                .withInterestTypeAsDecliningBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate("20 September 2011") //
                .withSubmittedOnDate("20 September 2011") //
                .withCollaterals(collaterals).withCharges(null).build(clientID.toString(), loanProductID.toString(), null);
    }

    private static String flatApplyForLoanApplication(final Long clientID, final Integer loanProductID, String principal,
            List<HashMap> collaterals) {
        return new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("4") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("4") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualPrincipalPayments() //
                .withInterestTypeAsFlatBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate("20 September 2011") //
                .withSubmittedOnDate("20 September 2011") //
                .withCollaterals(collaterals).withCharges(null).build(clientID.toString(), loanProductID.toString(), null);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Installment variation payload builders. Declining balance products vary the "installmentAmount", flat products
    // vary the "principal"; both share the delete-by-dueDate payload.
    // ----------------------------------------------------------------------------------------------------------------
    private static String createDeleteVariations(ArrayList<Map> deletedInstallments) {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        exceptions.put("deletedinstallments", createDeletedMapFromPeriods(deletedInstallments));
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static ArrayList createDeletedMapFromPeriods(ArrayList<Map> deletedItems) {
        ArrayList toReturn = new ArrayList<>();
        for (Map map : deletedItems) {
            ArrayList dueDate = (ArrayList) map.get("dueDate");
            Map tosend = new HashMap();
            tosend.put("dueDate", formatDate(dueDate));
            toReturn.add(tosend);
        }
        return toReturn;
    }

    private static ArrayList createDeletedMapFromDate(String date) {
        ArrayList toReturn = new ArrayList<>();
        Map tosend = new HashMap();
        tosend.put("dueDate", date);
        toReturn.add(tosend);
        return toReturn;
    }

    private static String decliningCreateAddVariations() {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        ArrayList newInstallments = new ArrayList<>();
        Map tosend = new HashMap();
        tosend.put("dueDate", "31 October 2011");
        tosend.put("installmentAmount", "5000");
        newInstallments.add(tosend);
        exceptions.put("newinstallments", newInstallments);
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static String flatCreateAddVariations() {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        ArrayList newInstallments = new ArrayList<>();
        Map tosend = new HashMap();
        tosend.put("dueDate", "31 October 2011");
        tosend.put("principal", "5000");
        newInstallments.add(tosend);
        exceptions.put("newinstallments", newInstallments);
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static String decliningCreateModifiyVariations(Map firstSchedule) {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        ArrayList modified = new ArrayList<>();
        ArrayList dueDate = (ArrayList) firstSchedule.get("dueDate");
        Map tosend = new HashMap();
        tosend.put("dueDate", formatDate(dueDate));
        tosend.put("installmentAmount", 30000);
        modified.add(tosend);
        exceptions.put("modifiedinstallments", modified);
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static String flatCreateModifiyVariations(Map firstSchedule) {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        ArrayList modified = new ArrayList<>();
        ArrayList dueDate = (ArrayList) firstSchedule.get("dueDate");
        Map tosend = new HashMap();
        tosend.put("dueDate", formatDate(dueDate));
        tosend.put("principal", 30000);
        modified.add(tosend);
        exceptions.put("modifiedinstallments", modified);
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static String decliningCreateAllVariations() {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        exceptions.put("modifiedinstallments", modifyMapByAmount("20 November 2011", "installmentAmount", 30000));
        exceptions.put("newinstallments", newInstallmentsByAmount("25 December 2011", "installmentAmount", "5000"));
        exceptions.put("deletedinstallments", createDeletedMapFromDate("20 December 2011"));
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static String decliningCreateAllVariationsWithEqualPrincipal() {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        exceptions.put("modifiedinstallments", modifyMapByAmount("20 November 2011", "principal", 30000));
        exceptions.put("newinstallments", newInstallmentsByAmount("25 December 2011", "principal", "5000"));
        exceptions.put("deletedinstallments", createDeletedMapFromDate("20 December 2011"));
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static String flatCreateAllVariations() {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        exceptions.put("modifiedinstallments", modifyMapByAmount("20 November 2011", "principal", 30000));
        exceptions.put("newinstallments", newInstallmentsByAmount("25 December 2011", "principal", "5000"));
        exceptions.put("deletedinstallments", createDeletedMapFromDate("20 December 2011"));
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static ArrayList modifyMapByAmount(String date, String amountKey, int amount) {
        ArrayList toReturn = new ArrayList<>();
        Map tosend = new HashMap();
        tosend.put("dueDate", date);
        tosend.put(amountKey, amount);
        toReturn.add(tosend);
        return toReturn;
    }

    private static ArrayList newInstallmentsByAmount(String date, String amountKey, String amount) {
        ArrayList toReturn = new ArrayList<>();
        Map tosend = new HashMap();
        tosend.put("dueDate", date);
        tosend.put(amountKey, amount);
        toReturn.add(tosend);
        return toReturn;
    }

    private static String decliningCreateModifiyDateVariations(String[] date, String[] newdate, String[] installments) {
        return createModifiyDateVariations(date, newdate, installments, "installmentAmount");
    }

    private static String flatCreateModifiyDateVariations(String[] date, String[] newdate, String[] principal) {
        return createModifiyDateVariations(date, newdate, principal, "principal");
    }

    private static String createModifiyDateVariations(String[] date, String[] newdate, String[] amounts, String amountKey) {
        Map<String, Object> toReturn = new HashMap<>();
        toReturn.put("locale", "en");
        toReturn.put("dateFormat", "dd MMMM yyyy");
        Map exceptions = new HashMap<>();
        ArrayList modified = new ArrayList<>();
        for (int i = 0; i < date.length; i++) {
            Map tosend = new HashMap();
            tosend.put("dueDate", date[i]);
            tosend.put("modifiedDueDate", newdate[i]);
            if (i < amounts.length) {
                tosend.put(amountKey, amounts[i]);
            }
            modified.add(tosend);
        }
        exceptions.put("modifiedinstallments", modified);
        toReturn.put("exceptions", exceptions);
        return new Gson().toJson(toReturn);
    }

    private static String formatDate(ArrayList list) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, (int) list.get(0));
        cal.set(Calendar.MONTH, (int) list.get(1) - 1);
        cal.set(Calendar.DAY_OF_MONTH, (int) list.get(2));
        Date date = cal.getTime();
        DateFormat requiredFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.US);
        return requiredFormat.format(date);
    }

    private static ArrayList<Map> constructVerifyData(String[] dates, String[] installments) {
        ArrayList<Map> toReturn = new ArrayList<>();
        for (int i = 0; i < dates.length; i++) {
            Map<String, String> map = new HashMap<>();
            map.put("dueDate", dates[i]);
            map.put("installmentAmount", installments[i]);
            toReturn.add(map);
        }
        return toReturn;
    }
}
