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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestLine;
import feign.Response;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import org.apache.fineract.client.models.GetLoanProductsProductIdResponse;
import org.apache.fineract.client.models.PutLoanProductsProductIdRequest;
import org.apache.fineract.client.models.PutLoanProductsProductIdResponse;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanProductHelper;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoanProductExternalIdTest {

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final LoanProductApi LOAN_PRODUCT_API = FineractFeignClientHelper.getFineractFeignClient().create(LoanProductApi.class);

    private LoanProductHelper loanProductHelper;

    interface LoanProductApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);
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

    private Integer createLoanProduct(String loanProductJSON) {
        return body(LOAN_PRODUCT_API.createLoanProduct(json(loanProductJSON))).get("resourceId").intValue();
    }

    @BeforeEach
    public void setup() {
        this.loanProductHelper = new LoanProductHelper();
    }

    @Test
    public void testLoanProductWithExternalId() {
        String externalId = UUID.randomUUID().toString();
        HashMap<String, Object> request = new LoanProductTestBuilder().withExternalId(externalId).build(null, null);
        Integer loanProductId = createLoanProduct(Utils.convertToJson(request));
        assertNotNull(loanProductId);

        GetLoanProductsProductIdResponse getLoanProductsProductIdResponse = loanProductHelper.retrieveLoanProductByExternalId(externalId);
        assertNotNull(getLoanProductsProductIdResponse.getId());
        assertEquals(loanProductId, getLoanProductsProductIdResponse.getId().intValue());

        final PutLoanProductsProductIdRequest requestModifyLoan = new PutLoanProductsProductIdRequest()
                .shortName(Utils.uniqueRandomStringGenerator("", 3));
        PutLoanProductsProductIdResponse putLoanProductsProductIdResponse = loanProductHelper.updateLoanProductByExternalId(externalId,
                requestModifyLoan);
        assertNotNull(putLoanProductsProductIdResponse.getResourceId());
        assertEquals(loanProductId, putLoanProductsProductIdResponse.getResourceId().intValue());
    }

    @Test
    public void testLoanProductWithInvalidExternalId() {
        String externalId = UUID.randomUUID().toString();
        HashMap<String, Object> request = new LoanProductTestBuilder().withExternalId(externalId).build(null, null);
        Integer loanProductId = createLoanProduct(Utils.convertToJson(request));
        assertNotNull(loanProductId);

        GetLoanProductsProductIdResponse getLoanProductsProductIdResponse = loanProductHelper.retrieveLoanProductByExternalId(externalId);
        assertNotNull(getLoanProductsProductIdResponse.getId());
        assertEquals(loanProductId, getLoanProductsProductIdResponse.getId().intValue());

        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> loanProductHelper.retrieveLoanProductByExternalId(externalId.substring(2)));
        assertEquals(404, exception.getResponse().code());
        assertTrue(exception.getMessage().contains("error.msg.loanproduct.id.invalid"));
    }

}
