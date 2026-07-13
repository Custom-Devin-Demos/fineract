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
package org.apache.fineract.integrationtests.common.system;

import static org.apache.fineract.integrationtests.common.Utils.initializeDefaultRequestSpecification;
import static org.apache.fineract.integrationtests.common.Utils.initializeDefaultResponseSpecification;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.FeignException;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.ObjectMapperFactory;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.feign.util.FeignCalls;
import org.apache.fineract.client.models.GetDataTablesResponse;
import org.apache.fineract.client.models.PagedLocalRequestAdvancedQueryData;
import org.apache.fineract.client.models.PostDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PostDataTablesRequest;
import org.apache.fineract.client.models.PostDataTablesResponse;
import org.apache.fineract.client.models.PutDataTablesAppTableIdDatatableIdResponse;
import org.apache.fineract.client.models.PutDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PutDataTablesRequest;
import org.apache.fineract.client.models.PutDataTablesResponse;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatatableHelper {

    private static final Gson GSON = new JSON().getGson();

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.getShared();

    private static final Logger LOG = LoggerFactory.getLogger(DatatableHelper.class);
    private final RequestSpecification requestSpec;
    private final ResponseSpecification responseSpec;

    private static final String DATATABLE_URL = "/fineract-provider/api/v1/datatables";

    public DatatableHelper() {
        this(initializeDefaultRequestSpecification(), initializeDefaultResponseSpecification());
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public DatatableHelper(final RequestSpecification requestSpec, final ResponseSpecification responseSpec) {
        this.requestSpec = requestSpec;
        this.responseSpec = responseSpec;
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public <T> T createDatatable(final String json, final String jsonAttributeToGetBack) {
        return Utils.performServerPost(this.requestSpec, this.responseSpec, DATATABLE_URL + "?" + Utils.TENANT_IDENTIFIER, json,
                jsonAttributeToGetBack);
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public String createDatatable(final String apptableName, final boolean multiRow) {
        return Utils.performServerPost(this.requestSpec, this.responseSpec, DATATABLE_URL + "?" + Utils.TENANT_IDENTIFIER,
                getTestDatatableAsJSON(apptableName, multiRow), "resourceIdentifier");
    }

    public PostDataTablesResponse createDatatable(PostDataTablesRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().dataTables.createDatatable(request));
    }

    public PostDataTablesResponse createDatatableFromJson(final String json) {
        final Object response = FeignCalls.ok(() -> feignClient().dataTablesFixed().createDatatable(toJsonNode(json)));
        return GSON.fromJson(GSON.toJson(response), PostDataTablesResponse.class);
    }

    public <T> T createDatatableFromJson(final String json, final String jsonAttributeToGetBack) {
        try {
            final Object response = FeignCalls.execute(() -> feignClient().dataTablesFixed().createDatatable(toJsonNode(json)));
            return JsonPath.from(GSON.toJson(response)).get(jsonAttributeToGetBack);
        } catch (FeignException e) {
            return JsonPath.from(e.responseBodyAsString()).get(jsonAttributeToGetBack);
        }
    }

    public void verifyDatatableCreated(final String datatableName) {
        final GetDataTablesResponse response = getDataTableDetails(datatableName);
        assertEquals(datatableName, response.getRegisteredTableName(), "ERROR IN CREATING THE DATATABLE");
    }

    public PutDataTablesResponse updateDatatableFromJson(final String dataTableName, final String json) {
        return FeignCalls.ok(() -> feignClient().dataTablesFixed().updateDatatable(dataTableName, toJsonNode(json)));
    }

    public <T> T createEntry(final String datatableName, final Integer apptableId, final String json) {
        final PostDataTablesAppTableIdResponse response = FeignCalls
                .ok(() -> feignClient().dataTables().createDatatableEntry(datatableName, apptableId.longValue(), toJsonNode(json)));
        return JsonPath.from(GSON.toJson(response)).get("");
    }

    public CallFailedRuntimeException createEntryExpectingError(final String datatableName, final Integer apptableId, final String json) {
        return FeignCalls
                .fail(() -> feignClient().dataTables().createDatatableEntry(datatableName, apptableId.longValue(), toJsonNode(json)));
    }

    public PostDataTablesAppTableIdResponse addEntry(final String datatableName, final Integer apptableId, final String json) {
        return FeignCalls
                .ok(() -> feignClient().dataTables().createDatatableEntry(datatableName, apptableId.longValue(), toJsonNode(json)));
    }

    public <T> T readEntry(final String datatableName, final Integer apptableId, final boolean genericResultSet,
            final Integer datatableResourceId) {
        final Object response;
        if (datatableResourceId == null) {
            response = FeignCalls
                    .ok(() -> feignClient().dataTablesFixed().getDatatableEntries(datatableName, apptableId.longValue(), genericResultSet));
        } else {
            response = FeignCalls.ok(() -> feignClient().dataTables().getDatatableManyEntry(datatableName, apptableId.longValue(),
                    datatableResourceId.longValue(), null, genericResultSet));
        }
        return JsonPath.from(GSON.toJson(response)).get("");
    }

    public <T> T updateEntry(final String datatableName, final Integer apptableId, final String json) {
        final PutDataTablesAppTableIdResponse response = FeignCalls
                .ok(() -> feignClient().dataTables().updateDatatableEntryOnetoOne(datatableName, apptableId.longValue(), toJsonNode(json)));
        return JsonPath.from(GSON.toJson(response)).get("");
    }

    public PutDataTablesAppTableIdResponse updateEntryOneToOne(final String datatableName, final Integer apptableId, final String json) {
        return FeignCalls
                .ok(() -> feignClient().dataTables().updateDatatableEntryOnetoOne(datatableName, apptableId.longValue(), toJsonNode(json)));
    }

    public <T> T updateEntry(final String datatableName, final Integer apptableId, final Integer entryId, final String json) {
        final Object response = FeignCalls.ok(() -> feignClient().dataTablesFixed().updateDatatableEntryOneToMany(datatableName,
                apptableId.longValue(), entryId.longValue(), toJsonNode(json)));
        return JsonPath.from(GSON.toJson(response)).get("");
    }

    public PutDataTablesAppTableIdDatatableIdResponse updateEntryOneToMany(final String datatableName, final Integer apptableId,
            final Integer entryId, final String json) {
        return FeignCalls.ok(() -> feignClient().dataTables().updateDatatableEntryOneToMany(datatableName, apptableId.longValue(),
                entryId.longValue(), toJsonNode(json)));
    }

    public Object deleteEntries(final String datatableName, final Integer apptableId, final String jsonAttributeToGetBack) {
        final Object response = FeignCalls
                .ok(() -> feignClient().dataTablesFixed().deleteDatatableEntries(datatableName, apptableId.longValue()));
        return JsonPath.from(GSON.toJson(response)).get(jsonAttributeToGetBack);
    }

    public String deleteDatatableByName(final String datatableName) {
        return FeignCalls.ok(() -> feignClient().dataTables().deleteDatatable(datatableName)).getResourceIdentifier();
    }

    private static FineractFeignClient feignClient() {
        return FineractFeignClientHelper.getFineractFeignClient();
    }

    private static JsonNode toJsonNode(final String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON payload: " + json, e);
        }
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static void verifyDatatableCreatedOnServer(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final String generatedDatatableName) {
        LOG.info("------------------------------CHECK DATATABLE DETAILS------------------------------------\n");
        final String responseRegisteredTableName = Utils.performServerGet(requestSpec, responseSpec,
                DATATABLE_URL + "/" + generatedDatatableName + "?" + Utils.TENANT_IDENTIFIER, "registeredTableName");
        assertEquals(generatedDatatableName, responseRegisteredTableName, "ERROR IN CREATING THE DATATABLE");
    }

    public GetDataTablesResponse getDataTableDetails(final String dataTableName) {
        return Calls.ok(FineractClientHelper.getFineractClient().dataTables.getDatatable(dataTableName));
    }

    public String runDatatableQuery(final String datatableName, final String columnFilter, final String valueFilter,
            final String resultColumns) {
        return FeignCalls.ok(() -> feignClient().dataTables().queryValues(datatableName, columnFilter, valueFilter, resultColumns));
    }

    public Map<String, Object> queryDatatable(String dataTableName, PagedLocalRequestAdvancedQueryData request) {
        final String response = FeignCalls.ok(() -> feignClient().dataTables().advancedQuery(dataTableName, request));
        return JsonPath.from(response).get("");
    }

    public PutDataTablesResponse updateDatatable(String dataTableName, PutDataTablesRequest request) {
        return Calls.ok(FineractClientHelper.getFineractClient().dataTables.updateDatatable(dataTableName, request));
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public String deleteDatatable(final String datatableName) {
        return Utils.performServerDelete(this.requestSpec, this.responseSpec,
                DATATABLE_URL + "/" + datatableName + "?" + Utils.TENANT_IDENTIFIER, "resourceIdentifier");
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public <T> T createDatatableEntry(final String datatableName, final Integer apptableId, final boolean genericResultSet,
            final String json) {
        return Utils.performServerPost(this.requestSpec, this.responseSpec, DATATABLE_URL + "/" + datatableName + "/" + apptableId
                + "?genericResultSet=" + genericResultSet + "&" + Utils.TENANT_IDENTIFIER, json, "");
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public <T> T readDatatableEntry(final String datatableName, final Integer resourceId, final boolean genericResultset,
            final Integer datatableResourceId, final String jsonAttributeToGetBack) {
        if (datatableResourceId == null) {
            return Utils.performServerGet(this.requestSpec, this.responseSpec, DATATABLE_URL + "/" + datatableName + "/" + resourceId
                    + "?genericResultSet=" + genericResultset + "&" + Utils.TENANT_IDENTIFIER, jsonAttributeToGetBack);
        } else {
            return Utils
                    .performServerGet(
                            this.requestSpec, this.responseSpec, DATATABLE_URL + "/" + datatableName + "/" + resourceId + "/"
                                    + datatableResourceId + "?genericResultSet=" + genericResultset + "&" + Utils.TENANT_IDENTIFIER,
                            jsonAttributeToGetBack);
        }
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public Object deleteDatatableEntries(final String datatableName, final Integer apptableId, String jsonAttributeToGetBack) {
        final String deleteEntryUrl = DATATABLE_URL + "/" + datatableName + "/" + apptableId + "?genericResultSet=true" + "&"
                + Utils.TENANT_IDENTIFIER;
        return Utils.performServerDelete(this.requestSpec, this.responseSpec, deleteEntryUrl, jsonAttributeToGetBack);
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static String getTestDatatableAsJSON(final String apptableName, final boolean multiRow) {
        final HashMap<String, Object> map = new HashMap<>();
        final List<HashMap<String, Object>> datatableColumnsList = new ArrayList<>();
        map.put("datatableName", Utils.uniqueRandomStringGenerator(apptableName + "_", 5));
        map.put("apptableName", apptableName);
        map.put("entitySubType", "PERSON");
        map.put("multiRow", multiRow);
        addDatatableColumn(datatableColumnsList, "Spouse Name", "String", true, 25, null);
        addDatatableColumn(datatableColumnsList, "Number of Dependents", "Number", true, null, null);
        addDatatableColumn(datatableColumnsList, "Time of Visit", "DateTime", false, null, null);
        addDatatableColumn(datatableColumnsList, "Date of Approval", "Date", false, null, null);
        map.put("columns", datatableColumnsList);
        String requestJsonString = new Gson().toJson(map);
        LOG.info("map : {}", requestJsonString);
        return requestJsonString;
    }

    public static HashMap<String, Object> addColumn(List<HashMap<String, Object>> datatableColumnsList, String columnName,
            String columnType, boolean isMandatory, Integer length, String codeName) {
        final HashMap<String, Object> datatableColumnMap = new HashMap<>();

        datatableColumnMap.put("name", columnName);
        if (columnType != null) {
            datatableColumnMap.put("type", columnType);
        }
        datatableColumnMap.put("mandatory", isMandatory);
        if (length != null) {
            datatableColumnMap.put("length", length);
        }
        if (codeName != null) {
            datatableColumnMap.put("code", codeName);
        }

        datatableColumnsList.add(datatableColumnMap);
        return datatableColumnMap;
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static HashMap<String, Object> addDatatableColumn(List<HashMap<String, Object>> datatableColumnsList, String columnName,
            String columnType, boolean isMandatory, Integer length, String codeName) {
        return addColumn(datatableColumnsList, columnName, columnType, isMandatory, length, codeName);
    }

    public static List<HashMap<String, Object>> addDatatableColumnWithUniqueAndIndex(List<HashMap<String, Object>> datatableColumnsList,
            String columnName, String columnType, boolean isMandatory, Integer length, String codeName, boolean isUnique,
            boolean isIndexed) {

        final HashMap<String, Object> datatableColumnMap = new HashMap<>();

        datatableColumnMap.put("name", columnName);
        datatableColumnMap.put("type", columnType);
        datatableColumnMap.put("mandatory", isMandatory);
        if (length != null) {
            datatableColumnMap.put("length", length);
        }
        if (codeName != null) {
            datatableColumnMap.put("code", codeName);
        }
        datatableColumnMap.put("unique", isUnique);
        datatableColumnMap.put("indexed", isIndexed);
        datatableColumnsList.add(datatableColumnMap);
        return datatableColumnsList;
    }
}
