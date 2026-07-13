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

import static org.apache.fineract.client.feign.util.FeignCalls.fail;
import static org.apache.fineract.client.feign.util.FeignCalls.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.apache.fineract.accounting.common.AccountingConstants.FinancialActivity;
import org.apache.fineract.accounting.financialactivityaccount.exception.DuplicateFinancialActivityAccountFoundException;
import org.apache.fineract.accounting.financialactivityaccount.exception.FinancialActivityAccountInvalidException;
import org.apache.fineract.client.feign.FeignException;
import org.apache.fineract.client.feign.services.MappingFinancialActivitiesToAccountsApi;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.GetFinancialActivityAccountsResponse;
import org.apache.fineract.client.models.PostFinancialActivityAccountsRequest;
import org.apache.fineract.client.models.PostFinancialActivityAccountsResponse;
import org.apache.fineract.client.models.PutFinancialActivityAccountsResponse;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FinancialActivityAccountsTest {

    private final Integer assetTransferFinancialActivityId = FinancialActivity.ASSET_TRANSFER.getValue();
    public static final Integer LIABILITY_TRANSFER_FINANCIAL_ACTIVITY_ID = FinancialActivity.LIABILITY_TRANSFER.getValue();

    private final MappingFinancialActivitiesToAccountsApi financialActivityApi = FineractFeignClientHelper.getFineractFeignClient()
            .mappingFinancialActivitiesToAccounts();

    @Test
    public void testFinancialActivityAccounts() {

        /** Create a Liability and an Asset Transfer Account **/
        Account liabilityTransferAccount = AccountHelper.createLiabilityGlAccount("liability-transfer");
        Account assetTransferAccount = AccountHelper.createAssetGlAccount("asset-transfer");
        Assertions.assertNotNull(assetTransferAccount);
        Assertions.assertNotNull(liabilityTransferAccount);

        /*** Create A Financial Activity to Account Mapping **/
        PostFinancialActivityAccountsResponse createResponse = ok(() -> financialActivityApi.createGLAccountMappingFinancialActivityAccount(
                new PostFinancialActivityAccountsRequest().financialActivityId(LIABILITY_TRANSFER_FINANCIAL_ACTIVITY_ID.longValue())
                        .glAccountId(liabilityTransferAccount.getAccountID().longValue())));
        Long financialActivityAccountId = createResponse.getResourceId();
        Assertions.assertNotNull(financialActivityAccountId);

        /***
         * Fetch Created Financial Activity to Account Mapping and validate created values
         **/
        assertFinancialActivityAccountCreation(financialActivityAccountId, LIABILITY_TRANSFER_FINANCIAL_ACTIVITY_ID,
                liabilityTransferAccount);

        /**
         * Update Existing Financial Activity to Account Mapping and assert changes
         **/
        Account newLiabilityTransferAccount = AccountHelper.createLiabilityGlAccount("new-liability-transfer");
        Assertions.assertNotNull(newLiabilityTransferAccount);

        PutFinancialActivityAccountsResponse updateResponse = ok(
                () -> financialActivityApi.updateGLAccountMappingFinancialActivityAccount(financialActivityAccountId,
                        new PostFinancialActivityAccountsRequest().financialActivityId(LIABILITY_TRANSFER_FINANCIAL_ACTIVITY_ID.longValue())
                                .glAccountId(newLiabilityTransferAccount.getAccountID().longValue())));
        Assertions.assertEquals(financialActivityAccountId, updateResponse.getResourceId());

        /** Validate update works correctly **/
        assertFinancialActivityAccountCreation(financialActivityAccountId, LIABILITY_TRANSFER_FINANCIAL_ACTIVITY_ID,
                newLiabilityTransferAccount);

        /** Update with Invalid Financial Activity should fail **/
        CallFailedRuntimeException invalidFinancialActivityUpdateError = fail(() -> financialActivityApi
                .updateGLAccountMappingFinancialActivityAccount(financialActivityAccountId, new PostFinancialActivityAccountsRequest()
                        .financialActivityId(232L).glAccountId(newLiabilityTransferAccount.getAccountID().longValue())));
        assertEquals(400, invalidFinancialActivityUpdateError.getStatus());
        assertEquals("validation.msg.financialactivityaccount.financialActivityId.is.not.one.of.expected.enumerations",
                firstErrorCode(invalidFinancialActivityUpdateError));

        /** Creating Duplicate Financial Activity should fail **/
        CallFailedRuntimeException duplicateFinancialActivityAccountError = fail(
                () -> financialActivityApi.createGLAccountMappingFinancialActivityAccount(
                        new PostFinancialActivityAccountsRequest().financialActivityId(LIABILITY_TRANSFER_FINANCIAL_ACTIVITY_ID.longValue())
                                .glAccountId(liabilityTransferAccount.getAccountID().longValue())));
        assertEquals(403, duplicateFinancialActivityAccountError.getStatus());
        assertEquals(DuplicateFinancialActivityAccountFoundException.getErrorcode(),
                firstErrorCode(duplicateFinancialActivityAccountError));

        /**
         * Associating incorrect GL account types with a financial activity should fail
         **/
        CallFailedRuntimeException invalidFinancialActivityAccountError = fail(
                () -> financialActivityApi.updateGLAccountMappingFinancialActivityAccount(financialActivityAccountId,
                        new PostFinancialActivityAccountsRequest().financialActivityId(assetTransferFinancialActivityId.longValue())
                                .glAccountId(newLiabilityTransferAccount.getAccountID().longValue())));
        assertEquals(403, invalidFinancialActivityAccountError.getStatus());
        assertEquals(FinancialActivityAccountInvalidException.getErrorcode(), firstErrorCode(invalidFinancialActivityAccountError));

        /** Should be able to delete a Financial Activity to Account Mapping **/
        Long deletedFinancialActivityAccountId = ok(
                () -> financialActivityApi.deleteGLAccountMappingFinancialActivityAccount(financialActivityAccountId)).getResourceId();
        Assertions.assertNotNull(deletedFinancialActivityAccountId);
        Assertions.assertEquals(financialActivityAccountId, deletedFinancialActivityAccountId);

        /*** Trying to fetch a Deleted Account Mapping should give me a 404 **/
        CallFailedRuntimeException notFoundError = fail(() -> financialActivityApi.retreive(deletedFinancialActivityAccountId));
        assertEquals(404, notFoundError.getStatus());
    }

    private void assertFinancialActivityAccountCreation(Long financialActivityAccountId, Integer financialActivityId, Account glAccount) {
        GetFinancialActivityAccountsResponse mappingDetails = ok(() -> financialActivityApi.retreive(financialActivityAccountId));
        Assertions.assertEquals(financialActivityId, mappingDetails.getFinancialActivityData().getId());
        Assertions.assertEquals(Long.valueOf(glAccount.getAccountID()), mappingDetails.getGlAccountData().getId());
    }

    private static String firstErrorCode(CallFailedRuntimeException exception) {
        FeignException cause = (FeignException) exception.getCause();
        JsonObject body = JsonParser.parseString(cause.responseBodyAsString()).getAsJsonObject();
        return body.getAsJsonArray("errors").get(0).getAsJsonObject().get("userMessageGlobalisationCode").getAsString();
    }

    /**
     * Delete the Financial activities
     */
    @AfterEach
    public void tearDown() {
        List<GetFinancialActivityAccountsResponse> financialActivities = ok(() -> financialActivityApi.retrieveAll());
        for (GetFinancialActivityAccountsResponse financialActivity : financialActivities) {
            Long financialActivityAccountId = financialActivity.getId();
            Long deletedFinancialActivityAccountId = ok(
                    () -> financialActivityApi.deleteGLAccountMappingFinancialActivityAccount(financialActivityAccountId)).getResourceId();
            Assertions.assertNotNull(deletedFinancialActivityAccountId);
            Assertions.assertEquals(financialActivityAccountId, deletedFinancialActivityAccountId);
        }
    }
}
