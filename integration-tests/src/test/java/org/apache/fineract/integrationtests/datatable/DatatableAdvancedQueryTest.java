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

import static org.apache.fineract.client.models.FilterData.OperatorEnum.BTW;
import static org.apache.fineract.client.models.FilterData.OperatorEnum.EQ;
import static org.apache.fineract.client.models.FilterData.OperatorEnum.GT;
import static org.apache.fineract.client.models.FilterData.OperatorEnum.GTE;
import static org.apache.fineract.client.models.FilterData.OperatorEnum.IN;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_FIELD_TYPE_BOOLEAN;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_FIELD_TYPE_DATE;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_FIELD_TYPE_DECIMAL;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_FIELD_TYPE_NUMBER;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_FIELD_TYPE_STRING;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_FIELD_TYPE_TEXT;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_PARAM_APPTABLE_NAME;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_PARAM_COLUMNS;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_PARAM_DATATABLE_NAME;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_PARAM_MULTIROW;
import static org.apache.fineract.infrastructure.dataqueries.api.DataTableApiConstant.API_PARAM_SUBTYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.client.models.AdvancedQueryData;
import org.apache.fineract.client.models.AdvancedQueryRequest;
import org.apache.fineract.client.models.BusinessDateUpdateRequest;
import org.apache.fineract.client.models.ColumnFilterData;
import org.apache.fineract.client.models.FilterData;
import org.apache.fineract.client.models.GetDataTablesResponse;
import org.apache.fineract.client.models.PagedLocalRequestAdvancedQueryData;
import org.apache.fineract.client.models.PagedLocalRequestAdvancedQueryRequest;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostDataTablesResponse;
import org.apache.fineract.client.models.PostSavingsAccountTransactionsRequest;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.client.models.PostSavingsAccountsRequest;
import org.apache.fineract.client.models.PostSavingsProductsRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.models.ResultsetColumnHeaderData;
import org.apache.fineract.client.models.SortOrder;
import org.apache.fineract.client.models.TableQueryData;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.data.EntityTables;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsStatusChecker;
import org.apache.fineract.integrationtests.common.system.DatatableHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

public class DatatableAdvancedQueryTest {

    private static final Logger LOG = LoggerFactory.getLogger(DatatableAdvancedQueryTest.class);

    private static final String SAVINGS_TRANSACTION_APP_TABLE_NAME = EntityTables.SAVINGS_TRANSACTION.getName();
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    public static final String SAVINGS_DATE_FORMAT = Utils.DATE_FORMAT;

    private static final String COLUMN_STRING = "aString";
    private static final String COLUMN_TEXT = "aText";
    private static final String COLUMN_DATE = "aDate";
    private static final String COLUMN_BOOLEAN = "aBoolean";
    private static final String COLUMN_INTEGER = "aNumber";
    private static final String COLUMN_DECIMAL = "aDecimal";
    private static final String COLUMN_TRANSACTION_ID = "savings_transaction_id";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_SUBMITTED_DATE = "submitted_on_date";
    private static final String COLUMN_AMOUNT = "amount";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DatatableHelper datatableHelper;
    private GlobalConfigurationHelper globalConfigurationHelper;

    @BeforeEach
    public void setup() {
        datatableHelper = new DatatableHelper();
        globalConfigurationHelper = new GlobalConfigurationHelper();
    }

    @Test
    public void testDatatableAdvancedQuery() {
        String datatable = createAndVerifyDatatable(SAVINGS_TRANSACTION_APP_TABLE_NAME, null, false);
        LocalDate today = Utils.getLocalDateOfTenant();
        String todayS = DateUtils.format(today, SAVINGS_DATE_FORMAT);
        LocalDate yesterday = today.minus(1, ChronoUnit.DAYS);
        String yesterdayS = DateUtils.format(yesterday, SAVINGS_DATE_FORMAT);
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(Utils.dateFormatter.format(today)).dateFormat(Utils.DATE_FORMAT).locale("en"));

            final Integer clientId = ClientHelper.createClient(clientRequest(yesterdayS)).getClientId().intValue();
            assertNotNull(clientId);
            final Integer savingsId = createSavingsAccountDailyPosting(clientId, yesterdayS);
            assertNotNull(savingsId);

            final Integer transactionIdD1 = deposit(savingsId, "100", yesterdayS);
            assertNotNull(transactionIdD1);
            BigDecimal decValue1 = new BigDecimal("1.111");
            createDatatableEntry(datatable, transactionIdD1, yesterday, true, 1, decValue1);
            final Integer transactionIdD2 = deposit(savingsId, "300", yesterdayS);
            assertNotNull(transactionIdD2);
            createDatatableEntry(datatable, transactionIdD2, yesterday, false, 2, new BigDecimal("2.2"));
            final Integer transactionIdW1 = withdrawal(savingsId, "100", todayS);
            assertNotNull(transactionIdW1);
            createDatatableEntry(datatable, transactionIdW1, today, true, 3, new BigDecimal("3"));

            String yesterdayIsoS = DateUtils.format(yesterday, DateUtils.DEFAULT_DATE_FORMAT);
            String todayIsoS = DateUtils.format(today, DateUtils.DEFAULT_DATE_FORMAT);
            AdvancedQueryData query = new AdvancedQueryData()
                    .resultColumns(List.of(COLUMN_TRANSACTION_ID, COLUMN_STRING, COLUMN_TEXT, COLUMN_DATE, COLUMN_BOOLEAN, COLUMN_INTEGER,
                            COLUMN_DECIMAL))
                    .addColumnFiltersItem(new ColumnFilterData().column(COLUMN_TEXT)
                            .addFiltersItem(new FilterData().operator(EQ).values(List.of(transactionIdD1.toString()))))
                    .addColumnFiltersItem(new ColumnFilterData().column(COLUMN_DATE)
                            .addFiltersItem(new FilterData().operator(BTW).values(List.of(yesterdayIsoS, yesterdayIsoS))));
            PagedLocalRequestAdvancedQueryData pagedQuery = new PagedLocalRequestAdvancedQueryData().page(0).size(3)
                    .addSortsItem(new SortOrder().property("created_at").direction(SortOrder.DirectionEnum.DESC))
                    .addSortsItem(new SortOrder().property(COLUMN_TRANSACTION_ID).direction(SortOrder.DirectionEnum.DESC)).request(query);
            Map<String, Object> response = datatableHelper.queryDatatable(datatable, pagedQuery);

            assertEquals(1, response.get("total"));
            List<?> content = (List<?>) response.get("content");
            assertNotNull(content);
            assertEquals(1, content.size());
            Map<?, ?> first = (Map<?, ?>) content.get(0);
            assertEquals(transactionIdD1, first.get(COLUMN_TRANSACTION_ID));
            assertEquals(transactionIdD1.toString(), first.get(COLUMN_TEXT));
            assertEquals(yesterdayIsoS, first.get(COLUMN_DATE));
            assertTrue((Boolean) first.get(COLUMN_BOOLEAN));
            assertEquals(1, first.get(COLUMN_INTEGER));
            assertEquals(decValue1.floatValue(), first.get(COLUMN_DECIMAL));

            query.resultColumns(List.of(COLUMN_TRANSACTION_ID));
            query.columnFilters(List.of(
                    new ColumnFilterData().column(COLUMN_INTEGER).addFiltersItem(new FilterData().operator(GTE).values(List.of("1"))),
                    new ColumnFilterData().column(COLUMN_DATE)
                            .addFiltersItem(new FilterData().operator(BTW).values(List.of(yesterdayIsoS, todayIsoS)))));
            response = datatableHelper.queryDatatable(datatable, pagedQuery);

            assertEquals(3, response.get("total"));
            content = (List<?>) response.get("content");
            assertNotNull(content);
            assertEquals(3, content.size());
            first = (Map<?, ?>) content.get(0);
            assertEquals(transactionIdW1, first.get(COLUMN_TRANSACTION_ID));
            assertNull(first.get(COLUMN_TEXT));
            assertNull(first.get(COLUMN_DATE));
            assertNull(first.get(COLUMN_BOOLEAN));
            assertNull(first.get(COLUMN_INTEGER));
            assertNull(first.get(COLUMN_DECIMAL));
            assertEquals(transactionIdD2, ((Map<?, ?>) content.get(1)).get(COLUMN_TRANSACTION_ID));

            deleteDatatable(datatable, transactionIdD1, transactionIdD2, transactionIdW1);

        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    @Test
    public void testApptableWithDatatableAdvancedQuery() {
        String datatable = createAndVerifyDatatable(SAVINGS_TRANSACTION_APP_TABLE_NAME, null, false);

        LocalDate today = Utils.getLocalDateOfTenant();
        String todayS = DateUtils.format(today, SAVINGS_DATE_FORMAT);
        LocalDate yesterday = today.minus(1, ChronoUnit.DAYS);
        String yesterdayS = DateUtils.format(yesterday, SAVINGS_DATE_FORMAT);
        try {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(true));
            BusinessDateHelper.updateBusinessDate(new BusinessDateUpdateRequest().type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                    .date(Utils.dateFormatter.format(today)).dateFormat(Utils.DATE_FORMAT).locale("en"));

            final Integer clientId = ClientHelper.createClient(clientRequest(yesterdayS)).getClientId().intValue();
            assertNotNull(clientId);
            final Integer savingsId = createSavingsAccountDailyPosting(clientId, yesterdayS);
            assertNotNull(savingsId);

            final Integer transactionIdD1 = deposit(savingsId, "100", yesterdayS);
            assertNotNull(transactionIdD1);
            BigDecimal decValue1 = new BigDecimal("1.111");
            createDatatableEntry(datatable, transactionIdD1, yesterday, true, 1, decValue1);
            final Integer transactionIdD2 = deposit(savingsId, "300", yesterdayS);
            assertNotNull(transactionIdD2);
            BigDecimal decValue2 = new BigDecimal("2.2");
            createDatatableEntry(datatable, transactionIdD2, yesterday, false, 2, decValue2);
            final Integer transactionIdW1 = withdrawal(savingsId, "100", todayS);
            assertNotNull(transactionIdW1);
            createDatatableEntry(datatable, transactionIdW1, today, true, 3, new BigDecimal("3"));

            String yesterdayIsoS = DateUtils.format(yesterday, DateUtils.DEFAULT_DATE_FORMAT);
            String todayIsoS = DateUtils.format(today, DateUtils.DEFAULT_DATE_FORMAT);

            AdvancedQueryData baseQuery = new AdvancedQueryData().resultColumns(List.of(COLUMN_ID, COLUMN_SUBMITTED_DATE))
                    .addColumnFiltersItem(new ColumnFilterData().column(COLUMN_AMOUNT)
                            .addFiltersItem(new FilterData().operator(GT).values(List.of("100"))))
                    .addColumnFiltersItem(new ColumnFilterData().column(COLUMN_SUBMITTED_DATE)
                            .addFiltersItem(new FilterData().operator(BTW).values(List.of(todayIsoS, todayIsoS))));
            AdvancedQueryData dataQuery = new AdvancedQueryData()
                    .resultColumns(List.of(COLUMN_TRANSACTION_ID, COLUMN_STRING, COLUMN_TEXT, COLUMN_DATE, COLUMN_BOOLEAN, COLUMN_INTEGER,
                            COLUMN_DECIMAL))
                    .addColumnFiltersItem(new ColumnFilterData().column(COLUMN_TEXT).addFiltersItem(
                            new FilterData().operator(IN).values(List.of(transactionIdD1.toString(), transactionIdD2.toString()))))
                    .addColumnFiltersItem(new ColumnFilterData().column(COLUMN_DATE)
                            .addFiltersItem(new FilterData().operator(BTW).values(List.of(yesterdayIsoS, yesterdayIsoS))));
            AdvancedQueryRequest queryRequest = new AdvancedQueryRequest().baseQuery(baseQuery)
                    .datatableQueries(List.of(new TableQueryData().table(datatable).query(dataQuery)));
            PagedLocalRequestAdvancedQueryRequest pagedRequest = new PagedLocalRequestAdvancedQueryRequest().page(0).size(2)
                    .addSortsItem(new SortOrder().property(COLUMN_SUBMITTED_DATE).direction(SortOrder.DirectionEnum.DESC))
                    .addSortsItem(new SortOrder().property(COLUMN_ID).direction(SortOrder.DirectionEnum.DESC)).request(queryRequest);
            Map<String, Object> response = querySavingsTransactions(savingsId, pagedRequest);

            assertEquals(1, response.get("total"));
            List<?> content = (List<?>) response.get("content");
            assertNotNull(content);
            assertEquals(1, content.size());
            Map<?, ?> first = (Map<?, ?>) content.get(0);
            assertEquals(transactionIdD2, first.get(COLUMN_ID));
            assertEquals(todayIsoS, first.get(COLUMN_SUBMITTED_DATE));
            assertEquals(transactionIdD2, first.get(COLUMN_TRANSACTION_ID));
            assertEquals(transactionIdD2.toString(), first.get(COLUMN_TEXT));
            assertEquals(yesterdayIsoS, first.get(COLUMN_DATE));
            assertFalse((Boolean) first.get(COLUMN_BOOLEAN));
            assertEquals(2, first.get(COLUMN_INTEGER));
            assertEquals(decValue2.floatValue(), first.get(COLUMN_DECIMAL));

            baseQuery.columnFilters(List.of(
                    new ColumnFilterData().column(COLUMN_AMOUNT).addFiltersItem(new FilterData().operator(GTE).values(List.of("100"))),
                    new ColumnFilterData().column(COLUMN_SUBMITTED_DATE)
                            .addFiltersItem(new FilterData().operator(BTW).values(List.of(todayIsoS, todayIsoS)))));
            dataQuery.resultColumns(List.of(COLUMN_TRANSACTION_ID));
            dataQuery.columnFilters(List.of(
                    new ColumnFilterData().column(COLUMN_INTEGER).addFiltersItem(new FilterData().operator(GTE).values(List.of("1"))),
                    new ColumnFilterData().column(COLUMN_DATE)
                            .addFiltersItem(new FilterData().operator(BTW).values(List.of(yesterdayIsoS, todayIsoS)))));
            response = querySavingsTransactions(savingsId, pagedRequest);

            assertEquals(3, response.get("total"));
            content = (List<?>) response.get("content");
            assertNotNull(content);
            assertEquals(2, content.size()); // page size 2
            first = (Map<?, ?>) content.get(0);
            assertEquals(transactionIdW1, first.get(COLUMN_ID));
            assertEquals(todayIsoS, first.get(COLUMN_SUBMITTED_DATE));
            assertEquals(transactionIdW1, first.get(COLUMN_TRANSACTION_ID));
            assertNull(first.get(COLUMN_TEXT));
            assertNull(first.get(COLUMN_DATE));
            assertNull(first.get(COLUMN_BOOLEAN));
            assertNull(first.get(COLUMN_INTEGER));
            assertNull(first.get(COLUMN_DECIMAL));
            assertEquals(transactionIdD2, ((Map<?, ?>) content.get(1)).get(COLUMN_TRANSACTION_ID));

            deleteDatatable(datatable, transactionIdD1, transactionIdD2, transactionIdW1);

        } finally {
            globalConfigurationHelper.updateGlobalConfiguration(GlobalConfigurationConstants.ENABLE_BUSINESS_DATE,
                    new PutGlobalConfigurationsRequest().enabled(false));
        }
    }

    private String createAndVerifyDatatable(String apptable, String subType, boolean multiRow) {
        // creating datatable for apptable entity
        final HashMap<String, Object> request = new HashMap<>();
        request.put(API_PARAM_DATATABLE_NAME, Utils.uniqueRandomStringGenerator("dt_" + apptable + "_", 5));
        request.put(API_PARAM_APPTABLE_NAME, apptable);
        if (subType != null) {
            request.put(API_PARAM_SUBTYPE, subType);
        }
        request.put(API_PARAM_MULTIROW, multiRow);

        final List<HashMap<String, Object>> datatableColumns = new ArrayList<>();
        DatatableHelper.addDatatableColumnWithUniqueAndIndex(datatableColumns, COLUMN_STRING, API_FIELD_TYPE_STRING, true, 50, null,
                !multiRow, true);
        DatatableHelper.addColumn(datatableColumns, COLUMN_TEXT, API_FIELD_TYPE_TEXT, false, null, null);
        DatatableHelper.addColumn(datatableColumns, COLUMN_DATE, API_FIELD_TYPE_DATE, true, null, null);
        DatatableHelper.addColumn(datatableColumns, COLUMN_BOOLEAN, API_FIELD_TYPE_BOOLEAN, false, null, null);
        DatatableHelper.addColumn(datatableColumns, COLUMN_INTEGER, API_FIELD_TYPE_NUMBER, false, null, null);
        DatatableHelper.addColumn(datatableColumns, COLUMN_DECIMAL, API_FIELD_TYPE_DECIMAL, false, null, null);
        request.put(API_PARAM_COLUMNS, datatableColumns);

        String requestJson = new Gson().toJson(request);
        LOG.info("map : {}", requestJson);

        PostDataTablesResponse response = datatableHelper.createDatatableFromJson(requestJson);
        String datatable = response.getResourceIdentifier();
        assertNotNull(datatable);
        GetDataTablesResponse dataTable = datatableHelper.getDataTableDetails(datatable);
        List<ResultsetColumnHeaderData> columnHeaderData = dataTable.getColumnHeaderData();
        assertNotNull(columnHeaderData);
        // pk column and 2 audit columns were added automatically
        assertEquals(9, columnHeaderData.size());
        return datatable;
    }

    @NonNull
    private HashMap<String, Object> createDatatableEntry(String datatable, Integer apptableId, LocalDate dateValue, Boolean boolValue,
            Integer intValue, BigDecimal decValue) {
        final HashMap<String, Object> request = new HashMap<>();
        request.put(COLUMN_STRING, Utils.uniqueRandomStringGenerator(apptableId.toString() + "_", 5));
        request.put(COLUMN_TEXT, apptableId);
        request.put(COLUMN_DATE, DateUtils.format(dateValue, SAVINGS_DATE_FORMAT));
        request.put(COLUMN_BOOLEAN, boolValue);
        request.put(COLUMN_INTEGER, intValue == null ? null : intValue.toString());
        request.put(COLUMN_DECIMAL, decValue == null ? null : decValue.toString());
        request.put("locale", "en");
        request.put("dateFormat", SAVINGS_DATE_FORMAT);

        String requestJson = new Gson().toJson(request);
        HashMap<String, Object> response = datatableHelper.createEntry(datatable, apptableId, requestJson);
        assertNotNull(response.get("resourceId"));
        return response;
    }

    private void deleteDatatable(String datatable, Integer... apptableIds) {
        for (Integer apptableId : apptableIds) {
            String deletedId = (String) this.datatableHelper.deleteEntries(datatable, apptableId, "transactionId");
            assertEquals(apptableId, Integer.valueOf(deletedId), "ERROR IN DELETING THE DATATABLE ENTRY");
        }
        String deletedDatatable = this.datatableHelper.deleteDatatableByName(datatable);
        assertEquals(datatable, deletedDatatable, "ERROR IN DELETING THE DATATABLE");
    }

    private Integer createSavingsProductDailyPosting() {
        return SavingsProductHelper.createSavingsProduct(dailyPostingSavingsProductRequest()).getResourceId().intValue();
    }

    private Integer createSavingsAccountDailyPosting(final Integer clientID, final String startDate) {
        final Integer savingsProductID = createSavingsProductDailyPosting();
        assertNotNull(savingsProductID);
        PostSavingsAccountsRequest applicationRequest = new PostSavingsAccountsRequest().clientId(clientID.longValue())
                .productId(savingsProductID.longValue()).dateFormat(Utils.DATE_FORMAT).locale("en_GB").submittedOnDate(startDate);
        final Integer savingsId = Calls
                .ok(FineractClientHelper.getFineractClient().savingsAccounts.submitSavingsApplication(applicationRequest)).getSavingsId()
                .intValue();
        assertNotNull(savingsId);
        PostSavingsAccountsAccountIdResponse approveResponse = Calls.ok(FineractClientHelper.getFineractClient().savingsAccounts
                .handleCommandsSavingsAccount(savingsId.longValue(), new PostSavingsAccountsAccountIdRequest()
                        .locale(CommonConstants.LOCALE).dateFormat(CommonConstants.DATE_FORMAT).approvedOnDate(startDate), "approve"));
        SavingsStatusChecker.verifySavingsIsApproved(statusChanges(approveResponse));
        PostSavingsAccountsAccountIdResponse activateResponse = Calls.ok(FineractClientHelper.getFineractClient().savingsAccounts
                .handleCommandsSavingsAccount(savingsId.longValue(), new PostSavingsAccountsAccountIdRequest()
                        .locale(CommonConstants.LOCALE).dateFormat(CommonConstants.DATE_FORMAT).activatedOnDate(startDate), "activate"));
        SavingsStatusChecker.verifySavingsIsActive(statusChanges(activateResponse));
        return savingsId;
    }

    private Integer deposit(final Integer savingsId, final String amount, final String date) {
        return transaction(savingsId, amount, date, "deposit");
    }

    private Integer withdrawal(final Integer savingsId, final String amount, final String date) {
        return transaction(savingsId, amount, date, "withdrawal");
    }

    private Integer transaction(final Integer savingsId, final String amount, final String date, final String command) {
        PostSavingsAccountTransactionsRequest request = new PostSavingsAccountTransactionsRequest().locale(CommonConstants.LOCALE)
                .dateFormat(CommonConstants.DATE_FORMAT).transactionDate(date).transactionAmount(new BigDecimal(amount)).paymentTypeId(1);
        return Calls.ok(FineractClientHelper.getFineractClient().savingsTransactions.createSavingsAccountTransaction(savingsId.longValue(),
                request, command)).getResourceId().intValue();
    }

    private Map<String, Object> querySavingsTransactions(final Integer savingsId, final PagedLocalRequestAdvancedQueryRequest request) {
        String response = Calls.ok(FineractClientHelper.getFineractClient().savingsTransactions
                .advancedQuerySavingsAccountTransactions(savingsId.longValue(), request));
        final JsonNode root = readTree(response);
        final Map<String, Object> result = new LinkedHashMap<>();
        final Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            final String name = names.next();
            result.put(name, toPlainValue(root.get(name)));
        }
        return result;
    }

    private static PostClientsRequest clientRequest(final String activationDate) {
        return new PostClientsRequest().officeId(1L).legalFormId(ClientHelper.LEGALFORM_ID_PERSON)
                .firstname(Utils.randomFirstNameGenerator()).lastname(Utils.randomLastNameGenerator())
                .externalId(UUID.randomUUID().toString()).dateFormat(Utils.DATE_FORMAT).locale("en").active(true)
                .activationDate(activationDate);
    }

    private static PostSavingsProductsRequest dailyPostingSavingsProductRequest() {
        return new PostSavingsProductsRequest().name(Utils.uniqueRandomStringGenerator("SAVINGS_PRODUCT_", 6))
                .shortName(Utils.uniqueRandomStringGenerator("", 4)).description(Utils.randomStringGenerator("", 20)).currencyCode("USD")
                .interestCalculationDaysInYearType(365).locale("en_GB").digitsAfterDecimal(4).inMultiplesOf(0).interestCalculationType(1)
                .nominalAnnualInterestRate(10.0).interestCompoundingPeriodType(1).interestPostingPeriodType(1).accountingRule(1)
                .withdrawalFeeForTransfers(true).allowOverdraft(false).enforceMinRequiredBalance(false).withHoldTax(false);
    }

    private static HashMap<String, Object> statusChanges(final PostSavingsAccountsAccountIdResponse response) {
        final Map<?, ?> changes = (Map<?, ?>) response.getChanges();
        final Map<?, ?> status = (Map<?, ?>) changes.get("status");
        final HashMap<String, Object> result = new HashMap<>();
        status.forEach((key, value) -> result.put((String) key, value));
        return result;
    }

    private static JsonNode readTree(final String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to parse advanced query response", e);
        }
    }

    private static Object toPlainValue(final JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isObject()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            final Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                final String name = names.next();
                map.put(name, toPlainValue(node.get(name)));
            }
            return map;
        }
        if (node.isArray()) {
            final List<Object> list = new ArrayList<>();
            for (JsonNode child : node) {
                list.add(toPlainValue(child));
            }
            return list;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isIntegralNumber()) {
            final long longValue = node.longValue();
            if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                return Integer.valueOf((int) longValue);
            }
            return Long.valueOf(longValue);
        }
        if (node.isFloatingPointNumber()) {
            final double doubleValue = node.doubleValue();
            final float floatValue = (float) doubleValue;
            if ((double) floatValue == doubleValue) {
                return Float.valueOf(floatValue);
            }
            return Double.valueOf(doubleValue);
        }
        return node.asText();
    }
}
