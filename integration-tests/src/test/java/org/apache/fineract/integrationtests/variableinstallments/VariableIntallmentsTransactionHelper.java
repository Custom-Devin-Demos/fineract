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
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import io.restassured.path.json.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class VariableIntallmentsTransactionHelper {

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();

    private final ScheduleApi api;

    interface ScheduleApi {

        @RequestLine("GET v1/loans/{loanId}?associations=repaymentSchedule&exclude=guarantors")
        Response retrieveSchedule(@Param("loanId") Integer loanId);

        @RequestLine("POST v1/loans/{loanId}/schedule?command=calculateLoanSchedule")
        Response calculateLoanSchedule(@Param("loanId") Integer loanId, JsonNode body);

        @RequestLine("POST v1/loans/{loanId}/schedule?command=addVariations")
        Response addVariations(@Param("loanId") Integer loanId, JsonNode body);
    }

    public VariableIntallmentsTransactionHelper() {
        this.api = FineractFeignClientHelper.getFineractFeignClient().create(ScheduleApi.class);
    }

    public Map retrieveSchedule(Integer loanId) {
        return JsonPath.from(rawBody(this.api.retrieveSchedule(loanId))).get("");
    }

    public HashMap validateVariations(final String exceptions, Integer loanId) {
        return JsonPath.from(rawBody(this.api.calculateLoanSchedule(loanId, rawJson(exceptions)))).get("");
    }

    public HashMap submitVariations(final String exceptions, Integer loanId) {
        return JsonPath.from(rawBody(this.api.addVariations(loanId, rawJson(exceptions)))).get("");
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
}
