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
package org.apache.fineract.client.feign.services;

import feign.Headers;
import feign.RequestLine;
import java.util.Map;
import org.apache.fineract.client.models.PostFixedDepositProductsResponse;

/**
 * Hand-written Feign client for creating fixed deposit products. The generated {@code PostFixedDepositProductsRequest}
 * model does not expose the full set of fields used by the integration tests (interest rate chart slabs, withhold tax
 * mapping and accounting mappings), so this client accepts a full request body map to preserve behaviour while avoiding
 * the deprecated RestAssured helpers.
 */
public interface FixedDepositProductApiFixed {

    @RequestLine("POST /v1/fixeddepositproducts")
    @Headers({ "Content-Type: application/json", "Accept: application/json" })
    PostFixedDepositProductsResponse createFixedDepositProduct(Map<String, Object> body);
}
