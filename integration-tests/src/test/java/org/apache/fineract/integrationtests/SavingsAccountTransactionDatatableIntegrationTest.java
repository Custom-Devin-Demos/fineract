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

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.client.models.GetDataTablesResponse;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostColumnHeaderData;
import org.apache.fineract.client.models.PostDataTablesRequest;
import org.apache.fineract.client.models.PostDataTablesResponse;
import org.apache.fineract.client.models.PostSavingsAccountTransactionsRequest;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.client.models.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.client.models.PostSavingsAccountsRequest;
import org.apache.fineract.client.models.PostSavingsProductsRequest;
import org.apache.fineract.client.models.PutDataTablesRequest;
import org.apache.fineract.client.models.PutDataTablesRequestAddColumns;
import org.apache.fineract.client.models.PutDataTablesResponse;
import org.apache.fineract.client.models.ResultsetColumnHeaderData;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.infrastructure.dataqueries.data.EntityTables;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsStatusChecker;
import org.apache.fineract.integrationtests.common.savings.SavingsTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.system.DatatableHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({ SavingsTestLifecycleExtension.class })
public class SavingsAccountTransactionDatatableIntegrationTest {

    private static final String SAVINGS_TRANSACTION_APP_TABLE_NAME = EntityTables.SAVINGS_TRANSACTION.getName();
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    final String startDate = "01 Jun 2023";
    final String firstDepositDate = "05 Jun 2023";
    private DatatableHelper datatableHelper;
    private GlobalConfigurationHelper globalConfigurationHelper;

    @BeforeEach
    public void setup() {
        this.datatableHelper = new DatatableHelper();
        this.globalConfigurationHelper = new GlobalConfigurationHelper();
    }

    @Test
    public void testDatatableCreateReadUpdateDeleteForSavingsAccountTransaction() {
        // create dataTable
        String datatableName = Utils.uniqueRandomStringGenerator("dt_savings_transaction_", 5).toLowerCase().toLowerCase();
        String column1Name = "aNumber";
        String column2Name = "aString";
        String column3Name = "aBoolean";

        PostDataTablesRequest request = new PostDataTablesRequest();
        request.setDatatableName(datatableName);
        request.setApptableName(SAVINGS_TRANSACTION_APP_TABLE_NAME);
        request.setMultiRow(false);

        PostColumnHeaderData column1HeaderRequestData = new PostColumnHeaderData();
        column1HeaderRequestData.setName(column1Name);
        column1HeaderRequestData.setType("Number");
        column1HeaderRequestData.setMandatory(false);
        column1HeaderRequestData.setLength(10L);
        column1HeaderRequestData.setCode("");
        column1HeaderRequestData.setUnique(false);
        column1HeaderRequestData.setIndexed(false);

        request.addColumnsItem(column1HeaderRequestData);

        PostColumnHeaderData column2HeaderRequestData = new PostColumnHeaderData();
        column2HeaderRequestData.setName(column2Name);
        column2HeaderRequestData.setType("String");
        column2HeaderRequestData.setMandatory(false);
        column2HeaderRequestData.setLength(10L);
        column2HeaderRequestData.setCode("");
        column2HeaderRequestData.setUnique(false);
        column2HeaderRequestData.setIndexed(false);

        request.addColumnsItem(column2HeaderRequestData);

        PostDataTablesResponse response = datatableHelper.createDatatable(request);
        assertNotNull(response.getResourceIdentifier());

        // update datatable
        PutDataTablesRequest putRequest = new PutDataTablesRequest();
        putRequest.setApptableName(SAVINGS_TRANSACTION_APP_TABLE_NAME);
        PutDataTablesRequestAddColumns column3HeaderPutRequestData = new PutDataTablesRequestAddColumns();
        column3HeaderPutRequestData.setName(column3Name);
        column3HeaderPutRequestData.setType("Boolean");
        column3HeaderPutRequestData.setMandatory(false);

        putRequest.addAddColumnsItem(column3HeaderPutRequestData);

        PutDataTablesResponse updateResponse = datatableHelper.updateDatatable(datatableName, putRequest);
        assertNotNull(updateResponse.getResourceIdentifier());

        // verify Datatable got created
        GetDataTablesResponse dataTable = datatableHelper.getDataTableDetails(datatableName);

        // verfify columns
        List<ResultsetColumnHeaderData> columnHeaderData = dataTable.getColumnHeaderData();
        assertNotNull(columnHeaderData);

        // two columns with 1 primary key and 2 audit columns created
        assertEquals(6, columnHeaderData.size());

        // deleting the datatable
        String deletedDataTableName = this.datatableHelper.deleteDatatableByName(datatableName);
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");
    }

    @Test
    public void testDatatableCreateReadUpdateDeleteEntryForSavingsAccountTransaction() {
        // Create Client
        final Integer clientID = ClientHelper.createClient(clientRequest(startDate)).getClientId().intValue();
        Assertions.assertNotNull(clientID);
        // Create savings product and account
        final Integer savingsId = createSavingsAccountDailyPosting(clientID, startDate);

        final Integer transactionId = deposit(savingsId, "100", firstDepositDate);

        assertNotNull(transactionId);

        // create dataTable
        String datatableName = Utils.uniqueRandomStringGenerator("dt_savings_transaction_", 5).toLowerCase().toLowerCase();
        String column1Name = "aNumber";

        PostDataTablesRequest request = new PostDataTablesRequest();
        request.setDatatableName(datatableName);
        request.setApptableName(SAVINGS_TRANSACTION_APP_TABLE_NAME);
        request.setMultiRow(true);

        PostColumnHeaderData column1HeaderRequestData = new PostColumnHeaderData();
        column1HeaderRequestData.setName(column1Name);
        column1HeaderRequestData.setType("Number");
        column1HeaderRequestData.setMandatory(false);
        column1HeaderRequestData.setLength(10L);
        column1HeaderRequestData.setCode("");
        column1HeaderRequestData.setUnique(false);
        column1HeaderRequestData.setIndexed(false);

        request.addColumnsItem(column1HeaderRequestData);

        PostDataTablesResponse response = datatableHelper.createDatatable(request);

        assertNotNull(response);

        String createdName = response.getResourceIdentifier();
        assertEquals(datatableName, createdName);

        // add entries
        final HashMap<String, Object> datatableEntryMap = new HashMap<>();
        datatableEntryMap.put(column1Name, Utils.randomNumberGenerator(5));
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");

        String datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);

        final boolean genericResultSet = true;

        HashMap<String, Object> datatableEntryResponseFirst = this.datatableHelper.createEntry(datatableName, transactionId,
                datatabelEntryRequestJsonString);

        Integer datatableId = (Integer) datatableEntryResponseFirst.get("resourceId");
        assertNotNull(datatableId);

        // Read the Datatable entry generated with genericResultSet
        HashMap<String, Object> items = this.datatableHelper.readEntry(datatableName, transactionId, genericResultSet, null);
        assertNotNull(items);
        assertEquals(1, ((List) items.get("data")).size());

        // update datatable entry
        datatableEntryMap.put(column1Name, 100);
        datatableEntryMap.put("locale", "en");
        datatableEntryMap.put("dateFormat", "yyyy-MM-dd");
        datatabelEntryRequestJsonString = new Gson().toJson(datatableEntryMap);
        HashMap<String, Object> updatedDatatableEntryResponse = this.datatableHelper.updateEntry(datatableName, transactionId, datatableId,
                datatabelEntryRequestJsonString);

        assertEquals(transactionId, Integer.valueOf((String) updatedDatatableEntryResponse.get("transactionId")));
        assertEquals(datatableId, updatedDatatableEntryResponse.get("resourceId"));

        // deleting datatable entries
        String deletedTransactionId = (String) this.datatableHelper.deleteEntries(datatableName, transactionId, "transactionId");
        assertEquals(transactionId, Integer.valueOf(deletedTransactionId), "ERROR IN DELETING THE DATATABLE ENTRIES");

        // deleting the datatable
        String deletedDataTableName = this.datatableHelper.deleteDatatableByName(datatableName);
        assertEquals(datatableName, deletedDataTableName, "ERROR IN DELETING THE DATATABLE");
    }

    private Integer createSavingsAccountDailyPosting(final Integer clientID, final String startDate) {
        final Integer savingsProductID = createSavingsProductDailyPosting();
        Assertions.assertNotNull(savingsProductID);
        PostSavingsAccountsRequest applicationRequest = new PostSavingsAccountsRequest().clientId(clientID.longValue())
                .productId(savingsProductID.longValue()).dateFormat(Utils.DATE_FORMAT).locale("en_GB").submittedOnDate(startDate);
        final Integer savingsId = Calls
                .ok(FineractClientHelper.getFineractClient().savingsAccounts.submitSavingsApplication(applicationRequest)).getSavingsId()
                .intValue();
        Assertions.assertNotNull(savingsId);
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

    private Integer createSavingsProductDailyPosting() {
        return SavingsProductHelper.createSavingsProduct(dailyPostingSavingsProductRequest()).getResourceId().intValue();
    }

    private Integer deposit(final Integer savingsId, final String amount, final String date) {
        PostSavingsAccountTransactionsRequest depositRequest = new PostSavingsAccountTransactionsRequest().locale(CommonConstants.LOCALE)
                .dateFormat(CommonConstants.DATE_FORMAT).transactionDate(date).transactionAmount(new BigDecimal(amount)).paymentTypeId(1);
        return Calls.ok(FineractClientHelper.getFineractClient().savingsTransactions.createSavingsAccountTransaction(savingsId.longValue(),
                depositRequest, "deposit")).getResourceId().intValue();
    }

    private static HashMap<String, Object> statusChanges(final PostSavingsAccountsAccountIdResponse response) {
        final Map<?, ?> changes = (Map<?, ?>) response.getChanges();
        final Map<?, ?> status = (Map<?, ?>) changes.get("status");
        final HashMap<String, Object> result = new HashMap<>();
        status.forEach((key, value) -> result.put((String) key, value));
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

    // Reset configuration fields
    @AfterEach
    public void tearDown() {
        globalConfigurationHelper.resetAllDefaultGlobalConfigurations();
        globalConfigurationHelper.verifyAllDefaultGlobalConfigurations();
    }
}
