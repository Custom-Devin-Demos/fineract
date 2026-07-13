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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import org.apache.fineract.client.models.DelinquencyBucketResponse;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.PutLoanProductsProductIdRequest;
import org.apache.fineract.client.models.PutLoanProductsProductIdResponse;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanProductHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.junit.jupiter.api.Test;

public class LoanProductWithDownPaymentConfigurationTest {

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    private final LoanProductHelper loanProductHelper = new LoanProductHelper();

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);
    }

    private static String rawBody(Response response) {
        try (Response r = response) {
            return Util.toString(r.body().asReader(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode toJsonNode(String json) {
        try {
            return RAW_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Integer createLoanProductId(final String loanProductJSON) {
        return toJsonNode(rawBody(RAW.createLoanProduct(toJsonNode(loanProductJSON)))).get("resourceId").asInt();
    }

    private ArrayList<HashMap<String, Object>> getLoanProductError(final String loanProductJSON) {
        final JsonNode errors = toJsonNode(rawBody(RAW.createLoanProduct(toJsonNode(loanProductJSON)))).get(CommonConstants.RESPONSE_ERROR);
        return RAW_MAPPER.convertValue(errors, new TypeReference<ArrayList<HashMap<String, Object>>>() {});
    }

    @Test
    public void loanProductCreationWithDownPaymentConfigurationTest() {
        // Delinquency Bucket
        final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
        final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);

        // down-payment configuration
        Boolean enableDownPayment = true;
        BigDecimal disbursedAmountPercentageForDownPayment = BigDecimal.valueOf(25);
        Boolean enableAutoRepaymentForDownPayment = false;
        // Loan Product creation with down-payment configuration
        Integer loanProductId = createLoanProductWithDownPaymentConfiguration(delinquencyBucketId, enableDownPayment, "25",
                enableAutoRepaymentForDownPayment);

        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = loanProductHelper
                .retrieveLoanProductById(loanProductId.longValue());
        assertNotNull(getLoanProductsProductResponse);
        assertEquals(enableDownPayment, getLoanProductsProductResponse.getEnableDownPayment());
        assertEquals(0, getLoanProductsProductResponse.getDisbursedAmountPercentageForDownPayment()
                .compareTo(disbursedAmountPercentageForDownPayment));
        assertEquals(enableAutoRepaymentForDownPayment, getLoanProductsProductResponse.getEnableAutoRepaymentForDownPayment());
    }

    @Test
    public void loanProductUpdateWithEnableDownPaymentConfigurationTest() {
        // Delinquency Bucket
        final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
        final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);
        // Loan Product without enable down payment configuration
        GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(delinquencyBucketId);
        assertNotNull(getLoanProductsProductResponse);
        assertEquals(false, getLoanProductsProductResponse.getEnableDownPayment());

        // Modify Loan Product to update enable down payment configuration
        PutLoanProductsProductIdResponse loanProductModifyResponse = updateLoanProduct(getLoanProductsProductResponse.getId());
        assertNotNull(loanProductModifyResponse);

        getLoanProductsProductResponse = loanProductHelper.retrieveLoanProductById(loanProductModifyResponse.getResourceId());
        assertNotNull(getLoanProductsProductResponse);
        assertEquals(true, getLoanProductsProductResponse.getEnableDownPayment());

    }

    @Test
    public void loanProductEnableDownPaymentConfigurationValidationTests() {
        // Delinquency Bucket
        final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
        final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);

        // down-payment configuration
        Boolean enableDownPayment = true;

        // Loan Product with enable down payment and with disbursed amount percentage as zero
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().withEnableDownPayment(enableDownPayment, "0", false)
                .build(null, delinquencyBucketId);

        ArrayList<HashMap<String, Object>> loanProductErrorData = getLoanProductError(Utils.convertToJson(loanProductMap));
        assertNotNull(loanProductErrorData);
        assertEquals("validation.msg.loanproduct.disbursedAmountPercentageForDownPayment.is.less.than.min",
                loanProductErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        // Loan Product with enable down payment and with disbursed amount percentage as greater than 100
        final HashMap<String, Object> loanProductMap_1 = new LoanProductTestBuilder().withEnableDownPayment(enableDownPayment, "101", false)
                .build(null, delinquencyBucketId);

        loanProductErrorData = getLoanProductError(Utils.convertToJson(loanProductMap_1));
        assertNotNull(loanProductErrorData);
        assertEquals("validation.msg.loanproduct.disbursedAmountPercentageForDownPayment.is.greater.than.max",
                loanProductErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        // Loan Product with enable down payment and with disbursed amount percentage precision greater than 6
        final HashMap<String, Object> loanProductMap_2 = new LoanProductTestBuilder()
                .withEnableDownPayment(enableDownPayment, "12.55555555", false).build(null, delinquencyBucketId);

        loanProductErrorData = getLoanProductError(Utils.convertToJson(loanProductMap_2));
        assertNotNull(loanProductErrorData);
        assertEquals("validation.msg.loanproduct.disbursedAmountPercentageForDownPayment.scale.is.greater.than.6",
                loanProductErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        // Loan Product with disable down payment and with disbursed amount percentage
        final HashMap<String, Object> loanProductMap_3 = new LoanProductTestBuilder().withEnableDownPayment(false, "12.5", false)
                .build(null, delinquencyBucketId);

        loanProductErrorData = getLoanProductError(Utils.convertToJson(loanProductMap_3));
        assertNotNull(loanProductErrorData);
        assertEquals("validation.msg.loanproduct.disbursedAmountPercentageForDownPayment.supported.only.for.enable.down.payment.true",
                loanProductErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        // Loan Product with enable down payment and without disbursed amount percentage
        final HashMap<String, Object> loanProductMap_4 = new LoanProductTestBuilder().withEnableDownPayment(enableDownPayment, null, false)
                .build(null, delinquencyBucketId);

        loanProductErrorData = getLoanProductError(Utils.convertToJson(loanProductMap_4));
        assertNotNull(loanProductErrorData);
        assertEquals("validation.msg.loanproduct.disbursedAmountPercentageForDownPayment.required.for.enable.down.payment.true",
                loanProductErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        // Loan Product with disable down payment and enable auto repayment for down payment
        final HashMap<String, Object> loanProductMap_5 = new LoanProductTestBuilder().withEnableDownPayment(false, null, true).build(null,
                delinquencyBucketId);

        loanProductErrorData = getLoanProductError(Utils.convertToJson(loanProductMap_5));
        assertNotNull(loanProductErrorData);
        assertEquals("validation.msg.loanproduct.enableAutoRepaymentForDownPayment.supported.only.for.enable.down.payment.true",
                loanProductErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));
    }

    private PutLoanProductsProductIdResponse updateLoanProduct(Long id) {
        // down-payment configuration
        Boolean enableDownPayment = true;
        BigDecimal disbursedAmountPercentageForDownPayment = BigDecimal.valueOf(25.0);
        final PutLoanProductsProductIdRequest requestModifyLoan = new PutLoanProductsProductIdRequest().enableDownPayment(enableDownPayment)
                .disbursedAmountPercentageForDownPayment(disbursedAmountPercentageForDownPayment).locale("en");
        return loanProductHelper.updateLoanProductById(id, requestModifyLoan);
    }

    private GetLoanProductsProductIdResponse createLoanProduct(final Long delinquencyBucketId) {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().build(null, delinquencyBucketId);
        final Integer loanProductId = createLoanProductId(Utils.convertToJson(loanProductMap));
        return loanProductHelper.retrieveLoanProductById(loanProductId.longValue());
    }

    private Integer createLoanProductWithDownPaymentConfiguration(final Long delinquencyBucketId, Boolean enableDownPayment,
            String disbursedAmountPercentageForDownPayment, Boolean enableAutoRepaymentForDownPayment) {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder()
                .withEnableDownPayment(enableDownPayment, disbursedAmountPercentageForDownPayment, enableAutoRepaymentForDownPayment)
                .build(null, delinquencyBucketId);
        return createLoanProductId(Utils.convertToJson(loanProductMap));
    }
}
