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

import static org.apache.fineract.client.feign.util.FeignCalls.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;
import org.apache.fineract.client.feign.services.LoanProductsApi;
import org.apache.fineract.client.models.DelinquencyBucketResponse;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.PutLoanProductsProductIdRequest;
import org.apache.fineract.client.models.PutLoanProductsProductIdResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.products.DelinquencyBucketsHelper;
import org.junit.jupiter.api.Test;

public class LoanProductWithRepaymentDueEventConfigurationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LoanProductsApi loanProductsApi = FineractFeignClientHelper.getFineractFeignClient().loanProducts();

    private final RawLoanProductApi rawLoanProductApi = FineractFeignClientHelper.getFineractFeignClient().create(RawLoanProductApi.class);

    interface RawLoanProductApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);
    }

    @Test
    public void loanProductCreationWithDueDaysConfigurationForRepaymentEventTest() {
        // Loan ExternalId
        String loanExternalIdStr = UUID.randomUUID().toString();

        // Delinquency Bucket
        final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
        final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);

        // event days configuration
        Integer dueDaysForRepaymentEvent = 1;
        Integer overDueDaysForRepaymentEvent = 2;

        // Client and Loan account creation

        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        Integer loanProductId = createLoanProductWithDueDaysForRepaymentEvent(delinquencyBucketId, dueDaysForRepaymentEvent,
                overDueDaysForRepaymentEvent);
        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = getLoanProduct(loanProductId);
        assertNotNull(getLoanProductsProductResponse);
        assertNotNull(getLoanProductsProductResponse.getDueDaysForRepaymentEvent());
        assertNotNull(getLoanProductsProductResponse.getOverDueDaysForRepaymentEvent());
        assertEquals(getLoanProductsProductResponse.getDueDaysForRepaymentEvent(), dueDaysForRepaymentEvent);
        assertEquals(getLoanProductsProductResponse.getOverDueDaysForRepaymentEvent(), overDueDaysForRepaymentEvent);
    }

    @Test
    public void loanProductUpdateWithDueDaysConfigurationForRepaymentEventTest() {
        // Loan ExternalId
        String loanExternalIdStr = UUID.randomUUID().toString();

        // Delinquency Bucket
        final Long delinquencyBucketId = DelinquencyBucketsHelper.createDefaultBucket();
        final DelinquencyBucketResponse delinquencyBucket = DelinquencyBucketsHelper.getBucket(delinquencyBucketId);

        // Client and Loan account creation

        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final GetLoanProductsProductIdResponse getLoanProductsProductResponse = createLoanProduct(delinquencyBucketId);
        assertNotNull(getLoanProductsProductResponse);

        // Modify Loan Product
        PutLoanProductsProductIdResponse loanProductModifyResponse = updateLoanProduct(getLoanProductsProductResponse.getId());
        assertNotNull(loanProductModifyResponse);

    }

    private PutLoanProductsProductIdResponse updateLoanProduct(Long id) {
        // event days configuration
        Integer dueDaysForRepaymentEvent = 1;
        Integer overDueDaysForRepaymentEvent = 2;
        final PutLoanProductsProductIdRequest requestModifyLoan = new PutLoanProductsProductIdRequest()
                .dueDaysForRepaymentEvent(dueDaysForRepaymentEvent).overDueDaysForRepaymentEvent(overDueDaysForRepaymentEvent).locale("en");
        return ok(() -> loanProductsApi.updateLoanProduct(id, requestModifyLoan));
    }

    private GetLoanProductsProductIdResponse createLoanProduct(final Long delinquencyBucketId) {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().build(null, delinquencyBucketId);
        final Integer loanProductId = submitLoanProduct(loanProductMap);
        return getLoanProduct(loanProductId);
    }

    private Integer createLoanProductWithDueDaysForRepaymentEvent(final Long delinquencyBucketId, Integer dueDaysForRepaymentEvent,
            Integer overDueDaysForRepaymentEvent) {
        final HashMap<String, Object> loanProductMap = new LoanProductTestBuilder().withDueDaysForRepaymentEvent(dueDaysForRepaymentEvent)
                .withOverDueDaysForRepaymentEvent(overDueDaysForRepaymentEvent).build(null, delinquencyBucketId);
        return submitLoanProduct(loanProductMap);
    }

    private Integer submitLoanProduct(final HashMap<String, Object> loanProductMap) {
        final JsonNode response = readBody(rawLoanProductApi.createLoanProduct(MAPPER.valueToTree(loanProductMap)));
        return response.get("resourceId").asInt();
    }

    private GetLoanProductsProductIdResponse getLoanProduct(final Integer loanProductId) {
        return ok(() -> loanProductsApi.retrieveOneLoanProduct(loanProductId.longValue()));
    }

    private static JsonNode readBody(final Response response) {
        try (Response r = response) {
            return MAPPER.readTree(Util.toString(r.body().asReader(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
