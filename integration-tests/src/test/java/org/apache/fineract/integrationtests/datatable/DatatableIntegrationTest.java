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
package org.apache.fineract.integrationtests.datatable;

import static org.apache.fineract.integrationtests.common.system.DatatableHelper.addColumn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.client.models.GetCodeValuesDataResponse;
import org.apache.fineract.client.models.GetCodesResponse;
import org.apache.fineract.client.models.GetDataTablesResponse;
import org.apache.fineract.client.models.PostCodeValuesDataRequest;
import org.apache.fineract.client.models.PostCodesRequest;
import org.apache.fineract.client.models.PostDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PostDataTablesResponse;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PutDataTablesAppTableIdDatatableIdResponse;
import org.apache.fineract.client.models.PutDataTablesAppTableIdResponse;
import org.apache.fineract.client.models.PutDataTablesResponse;
import org.apache.fineract.client.models.ResultsetColumnHeaderData;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.apache.fineract.integrationtests.common.system.DatatableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(LoanTestLifecycleExtension.class)
public class DatatableIntegrationTest extends IntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(DatatableIntegrationTest.class);

    private static final String CLIENT_APP_TABLE_NAME = "m_client";
    private static final String CLIENT_PERSON_SUBTYPE_NAME = "Person";
    private static final String LOAN_APP_TABLE_NAME = "m_loan";

    private static final Float LP_PRINCIPAL = 10000.0f;
    private static final String LP_REPAYMENTS = "5";
    private static final String LP_REPAYMENT_PERIOD = "2";
    private static final String LP_INTEREST_RATE = "1";
    private static final String EXPECTED_DISBURSAL_DATE = "14 March 2011";
    private static final String LOAN_APPLICATION_SUBMISSION_DATE = "13 March 2011";
    private static final String LOAN_TERM_FREQUENCY = "10";
    private static final String INDIVIDUAL_LOAN = "individual";
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    public static final String MINIMUM_OPENING_BALANCE = "1000.0";
    public static final String DEPOSIT_AMOUNT = "7000";

    private static final Gson SDK_GSON = new JSON().getGson();

    private DatatableHelper datatableHelper;
    private CodeHelper codeHelper;

    @BeforeEach
    public void setup() {
        this.datatableHelper = new DatatableHelper();
        this.codeHelper = new CodeHelper();
    }

    @Test
    public void validateCreateReadDeleteDatatable() throws ParseException {
        // Fetch / Create tst code
        String tst_tst_tst = "TST_TST_TST".toLowerCase();
        GetCodesResponse existingCode = this.codeHelper.retrieveCodes().stream().filter(code -> tst_tst_tst.equals(code.getName()))
                .findFirst().orElse(null);

        Integer createdCodeId = existingCode == null ? null : existingCode.getId().intValue();
        Integer createdCodeValueId;
        Integer createdCodeValueIdSecond;
        if (createdCodeId == null) {
            createdCodeId = this.codeHelper.createCode(new PostCodesRequest().name(tst_tst_tst)).getResourceId().intValue();

            createdCodeValueId = this.codeHelper
                    .createCodeValue(createdCodeId.longValue(),
                            new PostCodeValuesDataRequest().name(Utils.randomStringGenerator("cv_", 8)).position(1))
                    .getSubResourceId().intValue();
            createdCodeValueIdSecond = this.codeHelper
                    .createCodeValue(createdCodeId.longValue(),
                            new PostCodeValuesDataRequest().name(Utils.randomStringGenerator("cv_", 8)).position(2))
                    .getSubResourceId().intValue();
        } else {
            List<GetCodeValuesDataResponse> codeValuesForCode = this.codeHelper.getCodeValuesForCode(createdCodeId.longValue());
            createdCodeValueId = codeValuesForCode.get(0).getId().intValue();
            createdCodeValueIdSecond = codeValuesForCode.get(1).getId().intValue();
        }

        // creating datatable for client entity
        final HashMap<String, Object> columnMap = new HashMap<>();
        final List<HashMap<String, Object>> datatableColumnsList = new ArrayList<>();
        columnMap.put("datatableName", Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5).toLowerCase().toLowerCase());
        columnMap.put("entitySubType", "PERSON");
        columnMap.put("multiRow", false);
        String itsABoolean = "itsaboolean";
        String itsADate = "itsadate";
        String itsADatetime = "itsadatetime";
        String itsADecimal = "itsadecimal";
        String itsADropdown = "itsadropdown";
        String itsANumber = "itsanumber";
        String itsAString = "itsastring";
        String itsAText = "itsatext";
        String itsAJson = "itsajson";
        String tst_tst_tst_cd_itsADropdown = tst_tst_tst + "_cd_itsadropdown";
        String dateFormat = "dateFormat";

        addColumn(datatableColumnsList, itsABoolean, "Boolean", false, null, null);
        addColumn(datatableColumnsList, itsADate, "Date", true, null, null);
        addColumn(datatableColumnsList, itsADatetime, "Datetime", true, null, null);
        addColumn(datatableColumnsList, itsADecimal, "Decimal", true, null, null);
        addColumn(datatableColumnsList, itsADropdown, "Dropdown", false, null, tst_tst_tst);
        addColumn(datatableColumnsList, itsANumber, "Number", true, null, null);
        addColumn(datatableColumnsList, itsAString, "String", true, 10, null);
        columnMap.put("columns", datatableColumnsList);

        // try to create datatable without apptable
        columnMap.put("apptableName", null);
        String errorRequestJsonString = new Gson().toJson(columnMap);
        HashMap<String, Object> errorResponse = this.datatableHelper.createDatatableFromJson(errorRequestJsonString, "");
        assertEquals("validation.msg.validation.errors.exist", ((Map) errorResponse).get("userMessageGlobalisationCode"));
        List errors = (List) ((Map) errorResponse).get("errors");
        assertEquals(2, errors.size());
        assertEquals("validation.msg.datatable.apptableName.cannot.be.blank", ((Map) errors.get(0)).get("userMessageGlobalisationCode"));
        assertEquals("validation.msg.datatable.apptableName.is.not.one.of.expected.enumerations",
                ((Map) errors.get(1)).get("userMessageGlobalisationCode"));

        // set valid apptable name
        columnMap.put("apptableName", CLIENT_APP_TABLE_NAME);

        // try to create datatable with invalid column type
        HashMap<String, Object> textColumn = addColumn(datatableColumnsList, itsAText, "Invalid", true, null, null);
        errorRequestJsonString = new Gson().toJson(columnMap);
        errorResponse = this.datatableHelper.createDatatableFromJson(errorRequestJsonString, "");
        assertEquals("validation.msg.validation.errors.exist", ((Map) errorResponse).get("userMessageGlobalisationCode"));
        errors = (List) ((Map) errorResponse).get("errors");
        assertEquals(1, errors.size());
        Map error = (Map) errors.get(0);
        assertEquals("validation.msg.datatable.type.is.not.one.of.expected.enumerations", error.get("userMessageGlobalisationCode"));
        assertTrue(((String) error.get("defaultUserMessage"))
                .contains("string, number, boolean, decimal, date, datetime, text, json, dropdown"));

        // set valid type
        textColumn.put("type", "Text");
        // add json type
        addColumn(datatableColumnsList, itsAJson, "Json", false, null, null);

        String datatabelRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("map : {}", datatabelRequestJsonString);

        HashMap<String, Object> datatableResponse = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString, "");
        String datatableName = (String) datatableResponse.get("resourceIdentifier");
        this.datatableHelper.verifyDatatableCreated(datatableName);

        // try to create with the same name
        errorResponse = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString, "");
        assertEquals("validation.msg.validation.errors.exist", ((Map) errorResponse).get("userMessageGlobalisationCode"));

        // creating client with datatables
        final Integer clientID = ClientHelper.addClientAsPerson("1", ClientHelper.LEGALFORM_ID_PERSON, UUID.randomUUID().toString())
                .getClientId().intValue();

        // creating new client datatable entry
        final boolean genericResultSet = true;

        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put(itsABoolean, Utils.randomNumberGenerator(1) % 2 == 0);
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADatetime, Utils.randomDateTimeGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(tst_tst_tst_cd_itsADropdown, createdCodeValueId);
        datatableEntryMap.put(itsANumber, Utils.randomNumberGenerator(5));
        datatableEntryMap.put(itsAString, Utils.randomStringGenerator("", 8));
        datatableEntryMap.put(itsAText, Utils.randomStringGenerator("", 1000));
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        String json = "{\"testparam\": \"testvalue\"}";
        // add invalid json
        datatableEntryMap.put(itsAJson, '{' + json);

        String datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        this.datatableHelper.createEntryExpectingError(datatableName, clientID, datatabelEntryRequestJsonString);

        // add valid json
        datatableEntryMap.put(itsAJson, json);

        datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        HashMap<String, Object> datatableEntryResponse = this.datatableHelper.createEntry(datatableName, clientID,
                datatabelEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.get("resourceId"), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        final HashMap<String, Object> items = this.datatableHelper.readEntry(datatableName, clientID, genericResultSet,
                (Integer) datatableEntryResponse.get("resourceId"));
        assertNotNull(items);

        List columnHeaders = (List) items.get("columnHeaders");
        List columnData = (List) items.get("data");
        assertEquals(1, columnData.size());

        Map data = (Map) columnData.get(0);

        assertEquals("client_id", ((Map) columnHeaders.get(0)).get("columnName"));
        assertEquals(clientID, ((List) data.get("row")).get(0));

        assertEquals(itsABoolean, ((Map) columnHeaders.get(1)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsABoolean), ((List) data.get("row")).get(1));

        assertEquals(itsADate, ((Map) columnHeaders.get(2)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADate), Utils.arrayDateToString((List) ((List) data.get("row")).get(2)));

        assertEquals(itsADatetime, ((Map) columnHeaders.get(3)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADatetime), Utils.arrayDateTimeToString(toIntegerList(((List) data.get("row")).get(3))));

        assertEquals(itsADecimal, ((Map) columnHeaders.get(4)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADecimal), ((List) data.get("row")).get(4));

        assertEquals(tst_tst_tst_cd_itsADropdown, ((Map) columnHeaders.get(5)).get("columnName"));
        assertEquals(datatableEntryMap.get(tst_tst_tst_cd_itsADropdown), ((List) data.get("row")).get(5));

        assertEquals(itsANumber, ((Map) columnHeaders.get(6)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsANumber), ((List) data.get("row")).get(6));

        assertEquals(itsAString, ((Map) columnHeaders.get(7)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsAString), ((List) data.get("row")).get(7));

        assertEquals(itsAText, ((Map) columnHeaders.get(8)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsAText), ((List) data.get("row")).get(8));

        assertEquals(itsAJson, ((Map) columnHeaders.get(9)).get("columnName"));
        Object jsonResponse = ((List) data.get("row")).get(9);
        assertEquals(datatableEntryMap.get(itsAJson), jsonResponse instanceof Map ? ((Map) jsonResponse).get("value") : jsonResponse);

        // Read the Datatable entry generated with genericResultSet in false
        List<HashMap<String, Object>> datatableEntryResponseNoGenericResult = this.datatableHelper.readEntry(datatableName, clientID,
                !genericResultSet, (Integer) datatableEntryResponse.get("resourceId"));
        assertNotNull(datatableEntryResponseNoGenericResult, "ERROR IN GETTING THE DATE VALUE FROM DATATABLE RECORD");
        assertEquals(1, datatableEntryResponseNoGenericResult.size());

        HashMap<String, Object> responseMap = datatableEntryResponseNoGenericResult.get(0);
        assertEquals(clientID, responseMap.get("client_id"));
        assertEquals(datatableEntryMap.get(itsABoolean), Boolean.valueOf((String) responseMap.get(itsABoolean)));
        assertEquals(datatableEntryMap.get(itsADate), Utils.arrayDateToString((List) responseMap.get(itsADate)));
        assertEquals(datatableEntryMap.get(itsADecimal), responseMap.get(itsADecimal));
        assertEquals(datatableEntryMap.get(itsADatetime), Utils.arrayDateTimeToString(toIntegerList(responseMap.get(itsADatetime))));
        assertEquals(datatableEntryMap.get(tst_tst_tst_cd_itsADropdown), responseMap.get(tst_tst_tst_cd_itsADropdown));
        assertEquals(datatableEntryMap.get(itsANumber), responseMap.get(itsANumber));
        assertEquals(datatableEntryMap.get(itsAString), responseMap.get(itsAString));
        assertEquals(datatableEntryMap.get(itsAText), responseMap.get(itsAText));
        assertEquals(datatableEntryMap.get(itsAJson), responseMap.get(itsAJson));

        // Update datatable entry
        Boolean previousBoolean = (Boolean) datatableEntryMap.get(itsABoolean);
        datatableEntryMap.put(itsABoolean, !previousBoolean);
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADatetime, Utils.randomDateTimeGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(tst_tst_tst_cd_itsADropdown, null);
        datatableEntryMap.put(itsANumber, Utils.randomNumberGenerator(5));
        datatableEntryMap.put(itsAString, Utils.randomStringGenerator("", 8));
        datatableEntryMap.put(itsAText, Utils.randomStringGenerator("", 1000));

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        HashMap<String, Object> updatedDatatableEntryResponse = this.datatableHelper.updateEntry(datatableName, clientID,
                datatabelEntryRequestJsonString);

        assertEquals(clientID, updatedDatatableEntryResponse.get("clientId"));

        assertEquals(datatableEntryMap.get(itsABoolean), ((Map) updatedDatatableEntryResponse.get("changes")).get(itsABoolean));
        assertEquals(datatableEntryMap.get(itsADate),
                Utils.arrayDateToString((List) ((Map) updatedDatatableEntryResponse.get("changes")).get(itsADate)));
        assertEquals(datatableEntryMap.get(itsADecimal), ((Map) updatedDatatableEntryResponse.get("changes")).get(itsADecimal));
        assertEquals(datatableEntryMap.get(itsADatetime),
                Utils.arrayDateTimeToString(toIntegerList(((Map) updatedDatatableEntryResponse.get("changes")).get(itsADatetime))));
        assertEquals(datatableEntryMap.get(tst_tst_tst_cd_itsADropdown),
                ((Map) updatedDatatableEntryResponse.get("changes")).get(tst_tst_tst_cd_itsADropdown));
        assertEquals(datatableEntryMap.get(itsANumber), ((Map) updatedDatatableEntryResponse.get("changes")).get(itsANumber));
        assertEquals(datatableEntryMap.get(itsAString), ((Map) updatedDatatableEntryResponse.get("changes")).get(itsAString));
        assertEquals(datatableEntryMap.get(itsAText), ((Map) updatedDatatableEntryResponse.get("changes")).get(itsAText));

        List<String> columnsToValidate = List.of(itsABoolean, itsADate, itsADatetime, itsAString, itsAText, itsADecimal,
                tst_tst_tst_cd_itsADropdown);
        for (String column : columnsToValidate) {
            String valueFilter = column.equals(tst_tst_tst_cd_itsADropdown) ? createdCodeValueId.toString()
                    : datatableEntryMap.get(column).toString();
            String rows = Calls.ok(fineractClient().dataTables.queryValues(datatableName, column, valueFilter, column));
            JsonArray jsonArray = JsonParser.parseString(rows).getAsJsonArray();
            if (itsADatetime.equals(column)) {
                DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                DateFormat df2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date parsedRequest = df1.parse(datatableEntryMap.get(column).toString());
                Date parsedResponse = df2.parse(jsonArray.get(0).getAsJsonObject().get(column).getAsString());
                assertFalse(parsedRequest.after(parsedResponse));
                assertFalse(parsedRequest.before(parsedResponse));
            } else if (itsADecimal.equals(column)) {
                assertEquals(0, new BigDecimal(datatableEntryMap.get(column).toString())
                        .compareTo(new BigDecimal(jsonArray.get(0).getAsJsonObject().get(column).getAsString())));
            } else if (tst_tst_tst_cd_itsADropdown.equals(column)) {
                assertEquals(createdCodeValueId.toString(), jsonArray.get(0).getAsJsonObject().get(column).getAsString());
            } else {
                assertEquals(datatableEntryMap.get(column).toString(), jsonArray.get(0).getAsJsonObject().get(column).getAsString());
            }
        }

        // deleting datatable entries
        Integer appTableId = (Integer) this.datatableHelper.deleteEntries(datatableName, clientID, "clientId");
        assertEquals(clientID, appTableId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // deleting the datatable
        String deletedDataTableName = this.datatableHelper.deleteDatatableByName(datatableName);
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");

        GetDataTablesResponse dataTable = datatableHelper.getDataTableDetails(datatableName);
        assertNull(dataTable);
    }

    @Test
    public void validateCreateReadDeleteDatatableWithCaseSensitive() throws ParseException {
        // creating datatable for client entity
        final HashMap<String, Object> columnMap = new HashMap<>();
        final List<HashMap<String, Object>> datatableColumnsList = new ArrayList<>();
        columnMap.put("datatableName", Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5));
        columnMap.put("apptableName", CLIENT_APP_TABLE_NAME);
        columnMap.put("entitySubType", "PERSON");
        columnMap.put("multiRow", false);
        String itsADate = "itsADate";
        String itsADecimal = "itsADecimal";
        String itsAString = "itsAString";
        String dateFormat = "dateFormat";

        addColumn(datatableColumnsList, itsADate, "Date", true, null, null);
        addColumn(datatableColumnsList, itsADecimal, "Decimal", true, null, null);
        addColumn(datatableColumnsList, itsAString, "String", true, 10, null);
        columnMap.put("columns", datatableColumnsList);
        String datatabelRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("map : {}", datatabelRequestJsonString);

        HashMap<String, Object> datatableResponse = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString, "");
        String datatableName = (String) datatableResponse.get("resourceIdentifier");
        this.datatableHelper.verifyDatatableCreated(datatableName);

        // creating client with datatables
        final Integer clientID = ClientHelper.addClientAsPerson("1", ClientHelper.LEGALFORM_ID_PERSON, UUID.randomUUID().toString())
                .getClientId().intValue();

        // creating new client datatable entry
        final boolean genericResultSet = true;

        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(itsAString, Utils.randomStringGenerator("", 8));
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        String datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        HashMap<String, Object> datatableEntryResponse = this.datatableHelper.createEntry(datatableName, clientID,
                datatabelEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.get("resourceId"), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        final HashMap<String, Object> items = this.datatableHelper.readEntry(datatableName, clientID, genericResultSet,
                (Integer) datatableEntryResponse.get("resourceId"));
        assertNotNull(items);
        assertEquals(1, ((List) items.get("data")).size());

        assertEquals("client_id", ((Map) ((List) items.get("columnHeaders")).get(0)).get("columnName"));
        assertEquals(clientID, ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(0));

        assertEquals(itsADate, ((Map) ((List) items.get("columnHeaders")).get(1)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADate),
                Utils.arrayDateToString((List) ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(1)));

        assertEquals(itsADecimal, ((Map) ((List) items.get("columnHeaders")).get(2)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsADecimal), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(2));

        assertEquals(itsAString, ((Map) ((List) items.get("columnHeaders")).get(3)).get("columnName"));
        assertEquals(datatableEntryMap.get(itsAString), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(3));

        // Update datatable entry
        final String randomValue = Utils.randomStringGenerator("", 8);
        datatableEntryMap.put(itsADate, Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put(itsADecimal, Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(itsAString, randomValue);

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put(dateFormat, "yyyy-MM-dd");

        datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        HashMap<String, Object> updatedDatatableEntryResponse = this.datatableHelper.updateEntry(datatableName, clientID,
                datatabelEntryRequestJsonString);

        assertEquals(clientID, updatedDatatableEntryResponse.get("clientId"));

        assertEquals(datatableEntryMap.get(itsADate),
                Utils.arrayDateToString((List) ((Map) updatedDatatableEntryResponse.get("changes")).get(itsADate)));
        assertEquals(datatableEntryMap.get(itsADecimal), ((Map) updatedDatatableEntryResponse.get("changes")).get(itsADecimal));
        assertEquals(datatableEntryMap.get(itsAString), ((Map) updatedDatatableEntryResponse.get("changes")).get(itsAString));

        // Read the datatable with a query
        LOG.info("query in {} for value : {}", itsAString, randomValue);
        final String queryResult = this.datatableHelper.runDatatableQuery(datatableName, itsAString, randomValue, "client_id,itsADecimal");
        assertNotNull(queryResult);
        LOG.info("query result : {}", queryResult);

        // deleting datatable entries
        Integer appTableId = (Integer) this.datatableHelper.deleteEntries(datatableName, clientID, "clientId");
        assertEquals(clientID, appTableId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // deleting the datatable
        String deletedDataTableName = this.datatableHelper.deleteDatatableByName(datatableName);
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");
    }

    @Test
    public void validateInsertNullValues() {
        // creating datatable for client entity
        final HashMap<String, Object> columnMap = new HashMap<>();
        final List<HashMap<String, Object>> datatableColumnsList = new ArrayList<>();
        columnMap.put("datatableName", Utils.uniqueRandomStringGenerator(LOAN_APP_TABLE_NAME + "_", 5));
        columnMap.put("apptableName", LOAN_APP_TABLE_NAME);
        columnMap.put("entitySubType", "");
        columnMap.put("multiRow", true);
        addColumn(datatableColumnsList, "itsABoolean", "Boolean", false, null, null);
        addColumn(datatableColumnsList, "itsADate", "Date", false, null, null);
        addColumn(datatableColumnsList, "itsADatetime", "Datetime", false, null, null);
        addColumn(datatableColumnsList, "itsADecimal", "Decimal", false, null, null);
        addColumn(datatableColumnsList, "itsADropdown", "Dropdown", false, null, "TST_TST_TST");
        addColumn(datatableColumnsList, "itsANumber", "Number", false, null, null);
        addColumn(datatableColumnsList, "itsAString", "String", false, 10, null);
        addColumn(datatableColumnsList, "itsAText", "Text", false, null, null);
        columnMap.put("columns", datatableColumnsList);
        String datatabelRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("map : {}", datatabelRequestJsonString);

        HashMap<String, Object> datatableResponse = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString, "");
        String datatableName = (String) datatableResponse.get("resourceIdentifier");
        this.datatableHelper.verifyDatatableCreated(datatableName);

        // try to create with the same name
        HashMap<String, Object> response = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString, "");
        assertEquals("validation.msg.validation.errors.exist", ((Map) response).get("userMessageGlobalisationCode"));

        // creating client with datatables
        final Integer clientID = ClientHelper.addClientAsPerson("1", ClientHelper.LEGALFORM_ID_PERSON, UUID.randomUUID().toString())
                .getClientId().intValue();
        final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingEnabled();
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        // creating new client datatable entry
        final boolean genericResultSet = true;

        HashMap<String, Object> firstEntryMap = new HashMap<>();
        firstEntryMap.put("itsABoolean", null);
        firstEntryMap.put("itsADate", null);
        firstEntryMap.put("itsADatetime", null);
        firstEntryMap.put("itsADecimal", null);
        firstEntryMap.put("TST_TST_TST_cd_itsADropdown", null);
        firstEntryMap.put("itsANumber", null);
        firstEntryMap.put("itsAString", null);
        firstEntryMap.put("itsAText", null);

        firstEntryMap.put("locale", "en");
        firstEntryMap.put("dateFormat", "yyyy-MM-dd");

        String firstEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(firstEntryMap);
        LOG.info("map : {}", firstEntryRequestJsonString);

        HashMap<String, Object> firstEntryResponse = this.datatableHelper.createEntry(datatableName, loanID, firstEntryRequestJsonString);
        assertNotNull(firstEntryResponse.get("resourceId"), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        HashMap<String, Object> secondEntryMap = new HashMap<>();
        secondEntryMap.put("itsABoolean", "");
        secondEntryMap.put("itsADate", "");
        secondEntryMap.put("itsADatetime", "");
        secondEntryMap.put("itsADecimal", "");
        secondEntryMap.put("TST_TST_TST_cd_itsADropdown", "");
        secondEntryMap.put("itsANumber", "");
        secondEntryMap.put("itsAString", "");
        secondEntryMap.put("itsAText", "");

        secondEntryMap.put("locale", "en");
        secondEntryMap.put("dateFormat", "yyyy-MM-dd");

        String secondEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(secondEntryMap);
        HashMap<String, Object> secondEntryResponse = this.datatableHelper.createEntry(datatableName, loanID, secondEntryRequestJsonString);
        assertNotNull(secondEntryResponse.get("resourceId"), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        HashMap<String, Object> items = this.datatableHelper.readEntry(datatableName, loanID, genericResultSet, null);
        assertNotNull(items);
        assertEquals(2, ((List) items.get("data")).size());

        List headers = (List) items.get("columnHeaders");
        List firstEntryValues = (List) ((Map) ((List) items.get("data")).get(0)).get("row");
        assertEquals("id", ((Map) headers.get(0)).get("columnName"));
        assertEquals(1, firstEntryValues.get(0));
        assertEquals("loan_id", ((Map) headers.get(1)).get("columnName"));
        assertEquals(loanID, firstEntryValues.get(1));
        assertEquals("itsABoolean", ((Map) headers.get(2)).get("columnName"));
        assertNull(firstEntryValues.get(2));
        assertEquals("itsADate", ((Map) headers.get(3)).get("columnName"));
        assertNull(firstEntryValues.get(3));
        assertEquals("itsADatetime", ((Map) headers.get(4)).get("columnName"));
        assertNull(firstEntryValues.get(4));
        assertEquals("itsADecimal", ((Map) headers.get(5)).get("columnName"));
        assertNull(firstEntryValues.get(5));
        assertEquals("TST_TST_TST_cd_itsADropdown", ((Map) headers.get(6)).get("columnName"));
        assertNull(firstEntryValues.get(6));
        assertEquals("itsANumber", ((Map) headers.get(7)).get("columnName"));
        assertNull(firstEntryValues.get(7));
        assertEquals("itsAString", ((Map) headers.get(8)).get("columnName"));
        assertNull(firstEntryValues.get(8));
        assertEquals("itsAText", ((Map) headers.get(9)).get("columnName"));
        assertNull(firstEntryValues.get(9));

        List secondEntryValues = (List) ((Map) ((List) items.get("data")).get(1)).get("row");
        assertEquals(2, secondEntryValues.get(0));
        assertEquals(loanID, secondEntryValues.get(1));
        assertNull(secondEntryValues.get(2));
        assertNull(secondEntryValues.get(3));
        assertNull(secondEntryValues.get(4));
        assertNull(secondEntryValues.get(5));
        assertNull(secondEntryValues.get(6));
        assertNull(secondEntryValues.get(7));
        assertNull(secondEntryValues.get(8));
        assertNull(secondEntryValues.get(9));

        PutDataTablesAppTableIdDatatableIdResponse updatedDatatableEntryResponse = this.datatableHelper.updateEntryOneToMany(datatableName,
                loanID, 1, secondEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(0, updatedDatatableEntryResponse.getChanges().size());
    }

    @Test
    public void validateCreateAndEditDatatable() {
        // Creating client
        final Integer clientId = ClientHelper.addClientAsPerson("1", ClientHelper.LEGALFORM_ID_PERSON, UUID.randomUUID().toString())
                .getClientId().intValue();
        final Integer randomNumber = Utils.randomNumberGenerator(3);

        // Creating datatable for Client Person
        final String datatableName = Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5);
        final boolean genericResultSet = true;

        HashMap<String, Object> columnMap = new HashMap<>();
        ArrayList<HashMap<String, Object>> datatableColumnsList = new ArrayList<>();
        columnMap.put("datatableName", datatableName);
        columnMap.put("apptableName", CLIENT_APP_TABLE_NAME);
        columnMap.put("entitySubType", CLIENT_PERSON_SUBTYPE_NAME);
        columnMap.put("multiRow", false);
        addColumn(datatableColumnsList, "itsANumber", "Number", false, null, null);
        addColumn(datatableColumnsList, "itsAString", "String", false, 10, null);
        columnMap.put("columns", datatableColumnsList);
        String datatabelRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("map : {}", datatabelRequestJsonString);

        PostDataTablesResponse datatableCreateResponse = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString);
        assertEquals(datatableName, datatableCreateResponse.getResourceIdentifier());
        this.datatableHelper.verifyDatatableCreated(datatableName);

        // Insert first values
        final String randomString = Utils.randomStringGenerator("Q", 8);
        HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put("itsANumber", randomNumber);
        datatableEntryMap.put("itsAString", randomString);

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        String datatableEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(datatableEntryMap);
        PostDataTablesAppTableIdResponse datatableEntryResponse = this.datatableHelper.addEntry(datatableName, clientId,
                datatableEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.getResourceId(), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        HashMap<String, Object> items = this.datatableHelper.readEntry(datatableName, clientId, genericResultSet, null);
        assertNotNull(items);
        List data = (List) items.get("data");
        assertEquals(1, data.size());
        List records = (List) ((Map) data.get(0)).get("row");
        LOG.info("Record created at {}", records.get(3));
        LOG.info("Record updated at {}", records.get(4));

        assertEquals(clientId, records.get(0));
        assertEquals(randomString, records.get(2));

        // Update DataTable
        columnMap = new HashMap<>();
        columnMap.put("apptableName", CLIENT_APP_TABLE_NAME);
        columnMap.put("entitySubType", CLIENT_PERSON_SUBTYPE_NAME);
        datatableColumnsList = new ArrayList<>();
        addColumn(datatableColumnsList, "itsAText", "Text", false, null, null);
        columnMap.put("addColumns", datatableColumnsList);
        datatabelRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("map to update : {}", datatabelRequestJsonString);
        PutDataTablesResponse datatableUpdateResponse = this.datatableHelper.updateDatatableFromJson(datatableName,
                datatabelRequestJsonString);
        assertNotNull(datatableUpdateResponse);
        assertEquals(datatableName, datatableUpdateResponse.getResourceIdentifier());

        // Update DataTable Entry after Update DataTable schema
        datatableEntryMap = new HashMap<>();
        final String textValue = Utils.randomStringGenerator(randomString, 120);
        datatableEntryMap.put("itsAText", textValue);
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        datatableEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(datatableEntryMap);
        LOG.info("map to update : {}", datatableEntryRequestJsonString);
        PutDataTablesAppTableIdResponse updatedDatatableEntryResponse = this.datatableHelper.updateEntryOneToOne(datatableName, clientId,
                datatableEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(1, updatedDatatableEntryResponse.getChanges().size());

        // Read the Datatable entry generated with genericResultSet in true (default)
        items = this.datatableHelper.readEntry(datatableName, clientId, genericResultSet, null);
        assertNotNull(items);
        data = (List) items.get("data");
        assertEquals(1, data.size());

        records = (List) ((Map) data.get(0)).get("row");
        LOG.info("Record created at {}", records.get(3));
        LOG.info("Record updated at {}", records.get(4));

        assertEquals(clientId, records.get(0));
        assertEquals(randomString, records.get(2));
        assertEquals(textValue, records.get(5));

        Integer resourceId = (Integer) this.datatableHelper.deleteEntries(datatableName, clientId, "resourceId");
        assertEquals(clientId, resourceId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // Update - update, delete DataTable columns
        columnMap = new HashMap<>();
        columnMap.put("apptableName", CLIENT_APP_TABLE_NAME);
        columnMap.put("entitySubType", CLIENT_PERSON_SUBTYPE_NAME);
        List<Map<String, Object>> dropColumnsList = Collections.singletonList(Collections.singletonMap("name", "itsANumber"));
        columnMap.put("dropColumns", dropColumnsList);
        ArrayList<HashMap<String, Object>> changeColumnsList = new ArrayList<>();
        addColumn(changeColumnsList, "itsAString", null, false, 100, null);
        columnMap.put("changeColumns", changeColumnsList);
        datatabelRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("map to update : {}", datatabelRequestJsonString);
        datatableUpdateResponse = this.datatableHelper.updateDatatableFromJson(datatableName, datatabelRequestJsonString);
        assertNotNull(datatableUpdateResponse);
        assertEquals(datatableName, datatableUpdateResponse.getResourceIdentifier());

        GetDataTablesResponse dataTable = datatableHelper.getDataTableDetails(datatableName);
        List<ResultsetColumnHeaderData> columnHeaders = dataTable.getColumnHeaderData();
        assertEquals(5, columnHeaders.size());
        ResultsetColumnHeaderData stringColumn = columnHeaders.get(1);
        assertEquals("itsAString", stringColumn.getColumnName());
        assertEquals(100, stringColumn.getColumnLength());
    }

    @Test
    public void validateReadDatatableMultirow() {
        // Fetch / Create TST code
        String tst_tst_tst = "tst_tst_tst";
        GetCodesResponse existingCode = this.codeHelper.retrieveCodes().stream().filter(code -> tst_tst_tst.equals(code.getName()))
                .findFirst().orElse(null);

        Integer createdCodeId = existingCode == null ? null : existingCode.getId().intValue();
        Integer createdCodeValueId;
        Integer createdCodeValueIdSecond;
        if (createdCodeId == null) {
            createdCodeId = this.codeHelper.createCode(new PostCodesRequest().name(tst_tst_tst)).getResourceId().intValue();

            createdCodeValueId = this.codeHelper
                    .createCodeValue(createdCodeId.longValue(),
                            new PostCodeValuesDataRequest().name(Utils.randomStringGenerator("cv_", 8)).position(1))
                    .getSubResourceId().intValue();
            createdCodeValueIdSecond = this.codeHelper
                    .createCodeValue(createdCodeId.longValue(),
                            new PostCodeValuesDataRequest().name(Utils.randomStringGenerator("cv_", 8)).position(2))
                    .getSubResourceId().intValue();
        } else {
            List<GetCodeValuesDataResponse> codeValuesForCode = this.codeHelper.getCodeValuesForCode(createdCodeId.longValue());
            createdCodeValueId = codeValuesForCode.get(0).getId().intValue();
            createdCodeValueIdSecond = codeValuesForCode.get(1).getId().intValue();
        }

        // creating datatable for client entity
        final HashMap<String, Object> columnMap = new HashMap<>();
        final List<HashMap<String, Object>> datatableColumnsList = new ArrayList<>();
        columnMap.put("datatableName", Utils.uniqueRandomStringGenerator(LOAN_APP_TABLE_NAME + "_", 5));
        columnMap.put("apptableName", LOAN_APP_TABLE_NAME);
        columnMap.put("entitySubType", "");
        columnMap.put("multiRow", true);
        addColumn(datatableColumnsList, "itsABoolean", "Boolean", false, null, null);
        addColumn(datatableColumnsList, "itsADate", "Date", false, null, null);
        addColumn(datatableColumnsList, "itsADatetime", "Datetime", false, null, null);
        addColumn(datatableColumnsList, "itsADecimal", "Decimal", false, null, null);
        addColumn(datatableColumnsList, "itsADropdown", "Dropdown", false, null, tst_tst_tst);
        addColumn(datatableColumnsList, "itsANumber", "Number", false, null, null);
        addColumn(datatableColumnsList, "itsAString", "String", false, 10, null);
        addColumn(datatableColumnsList, "itsAText", "Text", false, null, null);
        columnMap.put("columns", datatableColumnsList);
        String datatabelRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("map : {}", datatabelRequestJsonString);

        HashMap<String, Object> datatableResponse = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString, "");
        String datatableName = (String) datatableResponse.get("resourceIdentifier");
        this.datatableHelper.verifyDatatableCreated(datatableName);

        // try to create with the same name
        HashMap<String, Object> response = this.datatableHelper.createDatatableFromJson(datatabelRequestJsonString, "");
        assertEquals("validation.msg.validation.errors.exist", ((Map) response).get("userMessageGlobalisationCode"));

        // creating client with datatables
        final Integer clientID = ClientHelper.addClientAsPerson("1", ClientHelper.LEGALFORM_ID_PERSON, UUID.randomUUID().toString())
                .getClientId().intValue();
        final Integer loanProductID = createLoanProductWithPeriodicAccrualAccountingEnabled();
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        // creating new client datatable entry
        final boolean genericResultSet = true;

        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put("itsABoolean", Utils.randomNumberGenerator(1) % 2 == 0);
        datatableEntryMap.put("itsADate", Utils.randomDateGenerator("yyyy-MM-dd"));
        datatableEntryMap.put("itsADatetime", Utils.randomDateTimeGenerator("yyyy-MM-dd"));
        datatableEntryMap.put("itsADecimal", Utils.randomDecimalGenerator(4, 3));
        datatableEntryMap.put(tst_tst_tst + "_cd_itsADropdown", createdCodeValueId);
        datatableEntryMap.put("itsANumber", Utils.randomNumberGenerator(5));
        datatableEntryMap.put("itsAString", Utils.randomStringGenerator("", 8));
        datatableEntryMap.put("itsAText", Utils.randomStringGenerator("", 1000));

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        String datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        HashMap<String, Object> datatableEntryResponseFirst = this.datatableHelper.createEntry(datatableName, loanID,
                datatabelEntryRequestJsonString);
        HashMap<String, Object> datatableEntryResponseSecond = this.datatableHelper.createEntry(datatableName, loanID,
                datatabelEntryRequestJsonString);
        assertNotNull(datatableEntryResponseFirst.get("resourceId"), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");
        assertNotNull(datatableEntryResponseSecond.get("resourceId"), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");

        // Read the Datatable entry generated with genericResultSet in true (default)
        HashMap<String, Object> items = this.datatableHelper.readEntry(datatableName, loanID, genericResultSet, null);
        assertNotNull(items);
        assertEquals(2, ((List) items.get("data")).size());

        assertEquals("id", ((Map) ((List) items.get("columnHeaders")).get(0)).get("columnName"));
        assertEquals(1, ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(0));
        assertEquals("loan_id", ((Map) ((List) items.get("columnHeaders")).get(1)).get("columnName"));
        assertEquals(loanID, ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(1));
        assertEquals("itsABoolean", ((Map) ((List) items.get("columnHeaders")).get(2)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsABoolean"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(2));
        assertEquals("itsADate", ((Map) ((List) items.get("columnHeaders")).get(3)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsADate"),
                Utils.arrayDateToString((List) ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(3)));
        assertEquals("itsADatetime", ((Map) ((List) items.get("columnHeaders")).get(4)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsADatetime"),
                Utils.arrayDateTimeToString(toIntegerList(((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(4))));
        assertEquals("itsADecimal", ((Map) ((List) items.get("columnHeaders")).get(5)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsADecimal"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(5));
        assertEquals(tst_tst_tst + "_cd_itsADropdown", ((Map) ((List) items.get("columnHeaders")).get(6)).get("columnName"));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(6));
        assertEquals("itsANumber", ((Map) ((List) items.get("columnHeaders")).get(7)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsANumber"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(7));
        assertEquals("itsAString", ((Map) ((List) items.get("columnHeaders")).get(8)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsAString"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(8));
        assertEquals("itsAText", ((Map) ((List) items.get("columnHeaders")).get(9)).get("columnName"));
        assertEquals(datatableEntryMap.get("itsAText"), ((List) ((Map) ((List) items.get("data")).get(0)).get("row")).get(9));

        assertEquals(2, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(0));
        assertEquals(loanID, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(1));
        assertEquals(datatableEntryMap.get("itsABoolean"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(2));
        assertEquals(datatableEntryMap.get("itsADate"),
                Utils.arrayDateToString((List) ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(3)));
        assertEquals(datatableEntryMap.get("itsADatetime"),
                Utils.arrayDateTimeToString(toIntegerList(((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(4))));
        assertEquals(datatableEntryMap.get("itsADecimal"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(5));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(6));
        assertEquals(datatableEntryMap.get("itsANumber"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(7));
        assertEquals(datatableEntryMap.get("itsAString"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(8));
        assertEquals(datatableEntryMap.get("itsAText"), ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(9));

        // Read the Datatable entry generated with genericResultSet in false
        List<HashMap<String, Object>> datatableEntryResponseNoGenericResult = this.datatableHelper.readEntry(datatableName, loanID,
                !genericResultSet, (Integer) datatableEntryResponseFirst.get("resourceId"));
        assertNotNull(datatableEntryResponseNoGenericResult, "ERROR IN GETTING THE DATE VALUE FROM DATATABLE RECORD");
        assertEquals(1, datatableEntryResponseNoGenericResult.size());

        assertEquals(loanID, datatableEntryResponseNoGenericResult.get(0).get("loan_id"));
        assertEquals(datatableEntryMap.get("itsABoolean"),
                Boolean.valueOf((String) datatableEntryResponseNoGenericResult.get(0).get("itsABoolean")));
        assertEquals(datatableEntryMap.get("itsADate"),
                Utils.arrayDateToString((List) datatableEntryResponseNoGenericResult.get(0).get("itsADate")));
        assertEquals(datatableEntryMap.get("itsADecimal"), datatableEntryResponseNoGenericResult.get(0).get("itsADecimal"));
        assertEquals(datatableEntryMap.get("itsADatetime"),
                Utils.arrayDateTimeToString(toIntegerList(datatableEntryResponseNoGenericResult.get(0).get("itsADatetime"))));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                datatableEntryResponseNoGenericResult.get(0).get(tst_tst_tst + "_cd_itsADropdown"));
        assertEquals(datatableEntryMap.get("itsANumber"), datatableEntryResponseNoGenericResult.get(0).get("itsANumber"));
        assertEquals(datatableEntryMap.get("itsAString"), datatableEntryResponseNoGenericResult.get(0).get("itsAString"));
        assertEquals(datatableEntryMap.get("itsAText"), datatableEntryResponseNoGenericResult.get(0).get("itsAText"));

        // Update datatable entry

        Boolean previousBoolean = (Boolean) datatableEntryMap.get("itsABoolean");

        datatableEntryMap.put("itsABoolean", null);
        datatableEntryMap.put("itsADate", null);
        datatableEntryMap.put("itsADatetime", null);
        datatableEntryMap.put("itsADecimal", null);
        datatableEntryMap.put(tst_tst_tst + "_cd_itsADropdown", null);
        datatableEntryMap.put("itsANumber", null);
        datatableEntryMap.put("itsAString", null);
        datatableEntryMap.put("itsAText", null);

        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        datatabelEntryRequestJsonString = new GsonBuilder().serializeNulls().create().toJson(datatableEntryMap);
        LOG.info("map : {}", datatabelEntryRequestJsonString);

        PutDataTablesAppTableIdDatatableIdResponse updatedDatatableEntryResponse = this.datatableHelper.updateEntryOneToMany(datatableName,
                loanID, 1, datatabelEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(1L, updatedDatatableEntryResponse.getResourceId());
        updatedDatatableEntryResponse = this.datatableHelper.updateEntryOneToMany(datatableName, loanID, 2,
                datatabelEntryRequestJsonString);
        assertNotNull(updatedDatatableEntryResponse);
        assertEquals(2L, updatedDatatableEntryResponse.getResourceId());

        assertEquals(Long.valueOf(loanID), updatedDatatableEntryResponse.getLoanId());

        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsABoolean"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsADate"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsADecimal"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsADatetime"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get(tst_tst_tst + "_cd_itsADropdown"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsANumber"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsAString"));
        assertEquals(null, updatedDatatableEntryResponse.getChanges().get("itsAText"));

        items = this.datatableHelper.readEntry(datatableName, loanID, genericResultSet, null);
        assertNotNull(items);
        assertEquals(2, ((List) items.get("data")).size());

        assertEquals("loan_id", ((Map) ((List) items.get("columnHeaders")).get(1)).get("columnName"));
        assertEquals(loanID, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(1));
        assertEquals("itsABoolean", ((Map) ((List) items.get("columnHeaders")).get(2)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(2));
        assertEquals("itsADate", ((Map) ((List) items.get("columnHeaders")).get(3)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(3));
        assertEquals("itsADatetime", ((Map) ((List) items.get("columnHeaders")).get(4)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(4));
        assertEquals("itsADecimal", ((Map) ((List) items.get("columnHeaders")).get(5)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(5));
        assertEquals(tst_tst_tst + "_cd_itsADropdown", ((Map) ((List) items.get("columnHeaders")).get(6)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(6));
        assertEquals("itsANumber", ((Map) ((List) items.get("columnHeaders")).get(7)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(7));
        assertEquals("itsAString", ((Map) ((List) items.get("columnHeaders")).get(8)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(8));
        assertEquals("itsAText", ((Map) ((List) items.get("columnHeaders")).get(9)).get("columnName"));
        assertEquals(null, ((List) ((Map) ((List) items.get("data")).get(1)).get("row")).get(9));

        // Read the Datatable entry generated with genericResultSet in false
        datatableEntryResponseNoGenericResult = this.datatableHelper.readEntry(datatableName, loanID, !genericResultSet,
                (Integer) datatableEntryResponseFirst.get("resourceId"));
        assertNotNull(datatableEntryResponseNoGenericResult, "ERROR IN GETTING THE DATE VALUE FROM DATATABLE RECORD");
        assertEquals(1, datatableEntryResponseNoGenericResult.size());

        assertEquals(loanID, datatableEntryResponseNoGenericResult.get(0).get("loan_id"));
        assertEquals(datatableEntryMap.get("itsABoolean"), datatableEntryResponseNoGenericResult.get(0).get("itsABoolean"));
        assertEquals(datatableEntryMap.get("itsADate"), datatableEntryResponseNoGenericResult.get(0).get("itsADate"));
        assertEquals(datatableEntryMap.get("itsADecimal"), datatableEntryResponseNoGenericResult.get(0).get("itsADecimal"));
        assertEquals(datatableEntryMap.get("itsADatetime"), datatableEntryResponseNoGenericResult.get(0).get("itsADatetime"));
        assertEquals(datatableEntryMap.get(tst_tst_tst + "_cd_itsADropdown"),
                datatableEntryResponseNoGenericResult.get(0).get(tst_tst_tst + "_cd_itsADropdown"));
        assertEquals(datatableEntryMap.get("itsANumber"), datatableEntryResponseNoGenericResult.get(0).get("itsANumber"));
        assertEquals(datatableEntryMap.get("itsAString"), datatableEntryResponseNoGenericResult.get(0).get("itsAString"));
        assertEquals(datatableEntryMap.get("itsAText"), datatableEntryResponseNoGenericResult.get(0).get("itsAText"));

        // deleting datatable entries
        Integer appTableId = (Integer) this.datatableHelper.deleteEntries(datatableName, loanID, "loanId");
        assertEquals(loanID, appTableId, "ERROR IN DELETING THE DATATABLE ENTRIES");

        // deleting the datatable
        String deletedDataTableName = this.datatableHelper.deleteDatatableByName(datatableName);
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");
    }

    private Integer applyForLoanApplication(final Integer clientID, final Integer loanProductID) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(LP_PRINCIPAL.toString())
                .withLoanTermFrequency(LOAN_TERM_FREQUENCY).withLoanTermFrequencyAsMonths().withNumberOfRepayments(LP_REPAYMENTS)
                .withRepaymentEveryAfter(LP_REPAYMENT_PERIOD).withRepaymentFrequencyTypeAsMonths()
                .withInterestRatePerPeriod(LP_INTEREST_RATE).withInterestTypeAsFlatBalance().withAmortizationTypeAsEqualPrincipalPayments()
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod().withExpectedDisbursementDate(EXPECTED_DISBURSAL_DATE)
                .withSubmittedOnDate(LOAN_APPLICATION_SUBMISSION_DATE).withLoanType(INDIVIDUAL_LOAN)
                .build(clientID.toString(), loanProductID.toString(), null);
        return Calls
                .ok(fineractClient().loans
                        .calculateLoanScheduleOrSubmitLoanApplication(SDK_GSON.fromJson(loanApplicationJSON, PostLoansRequest.class), null))
                .getLoanId().intValue();
    }

    private Integer createLoanProductWithPeriodicAccrualAccountingEnabled() {
        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(LP_PRINCIPAL.toString()).withRepaymentTypeAsMonth()
                .withRepaymentAfterEvery(LP_REPAYMENT_PERIOD).withNumberOfRepayments(LP_REPAYMENTS).withRepaymentTypeAsMonth()
                .withinterestRatePerPeriod(LP_INTEREST_RATE).withInterestRateFrequencyTypeAsMonths()
                .withAmortizationTypeAsEqualPrincipalPayment().withInterestTypeAsFlat().withAccountingRuleAsNone().withDaysInMonth("30")
                .withDaysInYear("365").build(null);
        return Calls.ok(fineractClient().loanProducts.createLoanProduct(SDK_GSON.fromJson(loanProductJSON, PostLoanProductsRequest.class)))
                .getResourceId().intValue();
    }

    private static List<Integer> toIntegerList(final Object raw) {
        final List<Integer> result = new ArrayList<>();
        for (Object element : (List<?>) raw) {
            result.add((Integer) element);
        }
        return result;
    }

    @Test
    public void testDropNullColumnWithData() {
        // Create datatable for client entity
        final HashMap<String, Object> columnMap = new HashMap<>();
        final List<HashMap<String, Object>> datatableColumnsList = new ArrayList<>();
        columnMap.put("datatableName", Utils.uniqueRandomStringGenerator(CLIENT_APP_TABLE_NAME + "_", 5));
        columnMap.put("apptableName", CLIENT_APP_TABLE_NAME);
        columnMap.put("entitySubType", CLIENT_PERSON_SUBTYPE_NAME);
        columnMap.put("multiRow", false);

        // Add columns: one that will have data and one that will be NULL
        addColumn(datatableColumnsList, "columnWithData", "String", false, 50, null);
        addColumn(datatableColumnsList, "columnWithNull", "String", false, 50, null);
        columnMap.put("columns", datatableColumnsList);

        String datatableRequestJsonString = new Gson().toJson(columnMap);
        LOG.info("Creating datatable: {}", datatableRequestJsonString);

        HashMap<String, Object> datatableResponse = this.datatableHelper.createDatatableFromJson(datatableRequestJsonString, "");
        String datatableName = (String) datatableResponse.get("resourceIdentifier");
        assertNotNull(datatableName);
        this.datatableHelper.verifyDatatableCreated(datatableName);

        // Create a client
        final Integer clientId = ClientHelper.addClientAsPerson("1", ClientHelper.LEGALFORM_ID_PERSON, UUID.randomUUID().toString())
                .getClientId().intValue();

        // Create a datatable entry with data in one column and NULL in the other
        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put("columnWithData", "TestValue");
        // columnWithNull is intentionally not set, so it will be NULL
        datatableEntryMap.put("locale", "en");

        String datatableEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        LOG.info("Creating datatable entry: {}", datatableEntryRequestJsonString);

        final boolean genericResultSet = true;
        HashMap<String, Object> datatableEntryResponse = this.datatableHelper.createEntry(datatableName, clientId,
                datatableEntryRequestJsonString);
        assertNotNull(datatableEntryResponse.get("resourceId"), "ERROR IN CREATING THE ENTITY DATATABLE RECORD");
        assertEquals(clientId, datatableEntryResponse.get("resourceId"));

        // Verify column count before drop
        GetDataTablesResponse dataTableBeforeDrop = datatableHelper.getDataTableDetails(datatableName);
        List<ResultsetColumnHeaderData> columnHeadersBeforeDrop = dataTableBeforeDrop.getColumnHeaderData();
        // Should have 5 columns before drop: client_id, columnWithData, columnWithNull, created_at, updated_at
        // Note: Datatables automatically add audit columns (created_at, updated_at)
        assertEquals(5, columnHeadersBeforeDrop.size(), "Should have 5 columns before dropping columnWithNull");

        // Now try to drop the NULL column - this should succeed with the fix
        HashMap<String, Object> updateMap = new HashMap<>();
        updateMap.put("apptableName", CLIENT_APP_TABLE_NAME);
        updateMap.put("entitySubType", CLIENT_PERSON_SUBTYPE_NAME);
        List<Map<String, Object>> dropColumnsList = Collections.singletonList(Collections.singletonMap("name", "columnWithNull"));
        updateMap.put("dropColumns", dropColumnsList);

        String updateRequestJsonString = new Gson().toJson(updateMap);
        LOG.info("Dropping NULL column: {}", updateRequestJsonString);

        PutDataTablesResponse updateResponse = this.datatableHelper.updateDatatableFromJson(datatableName, updateRequestJsonString);
        assertNotNull(updateResponse);
        assertEquals(datatableName, updateResponse.getResourceIdentifier());

        // Verify the column was dropped
        GetDataTablesResponse dataTable = datatableHelper.getDataTableDetails(datatableName);
        List<ResultsetColumnHeaderData> columnHeaders = dataTable.getColumnHeaderData();
        // Should have 4 columns after drop: client_id, columnWithData, created_at, updated_at (columnWithNull should be
        // dropped)
        assertEquals(4, columnHeaders.size(), "Should have 4 columns after dropping columnWithNull");
        boolean hasColumnWithData = false;
        boolean hasColumnWithNull = false;
        for (ResultsetColumnHeaderData header : columnHeaders) {
            if ("columnWithData".equals(header.getColumnName())) {
                hasColumnWithData = true;
            }
            if ("columnWithNull".equals(header.getColumnName())) {
                hasColumnWithNull = true;
            }
        }
        assertTrue(hasColumnWithData, "columnWithData should still exist");
        assertFalse(hasColumnWithNull, "columnWithNull should have been dropped");

        // Clean up
        this.datatableHelper.deleteEntries(datatableName, clientId, "clientId");
        this.datatableHelper.deleteDatatableByName(datatableName);
    }

}
