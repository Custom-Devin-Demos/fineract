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
import feign.Param;
import feign.RequestLine;
import java.util.Map;
import org.apache.fineract.client.feign.fixeddeposit.FixedDepositAccountDataFixed;
import org.apache.fineract.client.feign.fixeddeposit.FixedDepositCommandResponseFixed;
import org.apache.fineract.client.models.PostFixedDepositAccountsResponse;

/**
 * Hand-written Feign client for the fixed deposit account endpoints whose generated counterparts cannot express the
 * full request/response used by the integration tests. The generated {@code PostFixedDepositAccountsRequest} model
 * exposes only a small subset of the supported fields, and the generated
 * {@code GetFixedDepositAccountsAccountIdResponse} does not expose the summary totals nor the pre-closure penal /
 * nominal annual interest rate fields. This client accepts a full request body map and returns minimal typed responses
 * so callers avoid the deprecated RestAssured helpers without losing behaviour.
 */
public interface FixedDepositAccountApiFixed {

    @RequestLine("POST /v1/fixeddepositaccounts")
    @Headers({ "Content-Type: application/json", "Accept: application/json" })
    PostFixedDepositAccountsResponse submitApplication(Map<String, Object> body);

    @RequestLine("GET /v1/fixeddepositaccounts/{accountId}")
    @Headers({ "Accept: application/json" })
    FixedDepositAccountDataFixed retrieveOne(@Param("accountId") Long accountId);

    @RequestLine("POST /v1/fixeddepositaccounts/{accountId}?command={command}")
    @Headers({ "Content-Type: application/json", "Accept: application/json" })
    FixedDepositCommandResponseFixed handleCommand(@Param("accountId") Long accountId, @Param("command") String command,
            Map<String, Object> body);

    @RequestLine("PUT /v1/fixeddepositaccounts/{accountId}")
    @Headers({ "Content-Type: application/json", "Accept: application/json" })
    FixedDepositCommandResponseFixed update(@Param("accountId") Long accountId, Map<String, Object> body);
}
