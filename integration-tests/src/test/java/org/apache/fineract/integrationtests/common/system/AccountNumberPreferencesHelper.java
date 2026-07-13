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

import static org.apache.fineract.client.feign.util.FeignCalls.fail;
import static org.apache.fineract.client.feign.util.FeignCalls.ok;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.GetAccountNumberFormatsIdResponse;
import org.apache.fineract.client.models.PostAccountNumberFormatsRequest;
import org.apache.fineract.client.models.PutAccountNumberFormatsRequest;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountNumberPreferencesHelper {

    private static final Logger LOG = LoggerFactory.getLogger(AccountNumberPreferencesHelper.class);

    private static final Long CLIENT_ACCOUNT_TYPE = 1L;
    private static final Long LOAN_ACCOUNT_TYPE = 2L;
    private static final Long SAVINGS_ACCOUNT_TYPE = 3L;
    private static final Long CENTER_ACCOUNT_TYPE = 4L;
    private static final Long GROUPS_ACCOUNT_TYPE = 5L;

    private static final Long OFFICE_NAME_PREFIX = 1L;
    private static final Long CLIENT_TYPE_PREFIX = 101L;

    private final FineractFeignClient fineractClient;

    public AccountNumberPreferencesHelper() {
        this.fineractClient = FineractFeignClientHelper.getFineractFeignClient();
    }

    public Long createClientAccountNumberPreference() {
        return create(CLIENT_ACCOUNT_TYPE, CLIENT_TYPE_PREFIX);
    }

    public Long createLoanAccountNumberPreference() {
        return create(LOAN_ACCOUNT_TYPE, OFFICE_NAME_PREFIX);
    }

    public Long createSavingsAccountNumberPreference() {
        return create(SAVINGS_ACCOUNT_TYPE, OFFICE_NAME_PREFIX);
    }

    public Long createGroupsAccountNumberPreference() {
        return create(GROUPS_ACCOUNT_TYPE, OFFICE_NAME_PREFIX);
    }

    public Long createCenterAccountNumberPreference() {
        return create(CENTER_ACCOUNT_TYPE, OFFICE_NAME_PREFIX);
    }

    private Long create(final Long accountType, final Long prefixType) {
        return ok(() -> fineractClient.accountNumberFormat()
                .create(new PostAccountNumberFormatsRequest().accountType(accountType).prefixType(prefixType))).getResourceId();
    }

    public CallFailedRuntimeException createAccountNumberPreferenceExpectingFailure(final Long accountType, final Long prefixType) {
        return fail(() -> fineractClient.accountNumberFormat()
                .create(new PostAccountNumberFormatsRequest().accountType(accountType).prefixType(prefixType)));
    }

    public Long updateAccountNumberPreference(final Long accountNumberFormatId, final Long prefixType) {
        return ok(() -> fineractClient.accountNumberFormat().update1(accountNumberFormatId,
                new PutAccountNumberFormatsRequest().prefixType(prefixType))).getResourceId();
    }

    public CallFailedRuntimeException updateAccountNumberPreferenceExpectingFailure(final Long accountNumberFormatId,
            final Long prefixType) {
        return fail(() -> fineractClient.accountNumberFormat().update1(accountNumberFormatId,
                new PutAccountNumberFormatsRequest().prefixType(prefixType)));
    }

    public Long deleteAccountNumberPreference(final Long accountNumberFormatId) {
        return ok(() -> fineractClient.accountNumberFormat().delete(accountNumberFormatId)).getResourceId();
    }

    public CallFailedRuntimeException deleteAccountNumberPreferenceExpectingFailure(final Long accountNumberFormatId) {
        return fail(() -> fineractClient.accountNumberFormat().delete(accountNumberFormatId));
    }

    public GetAccountNumberFormatsIdResponse getAccountNumberPreference(final Long accountNumberFormatId) {
        return ok(() -> fineractClient.accountNumberFormat().retrieveOne(accountNumberFormatId));
    }

    public String getAccountNumberPreferencePrefixValue(final Long accountNumberFormatId) {
        return getAccountNumberPreference(accountNumberFormatId).getPrefixType().getValue();
    }

    public List<GetAccountNumberFormatsIdResponse> getAllAccountNumberPreferences() {
        return ok(() -> fineractClient.accountNumberFormat().retrieveAll2());
    }

    public void verifyCreationOfAccountNumberPreferences(final Long clientAccountNumberPreferenceId,
            final Long loanAccountNumberPreferenceId, final Long savingsAccountNumberPreferenceId,
            final Long groupsAccountNumberPreferenceId, final Long centerAccountNumberPreferenceId) {
        assertEquals(clientAccountNumberPreferenceId, getAccountNumberPreference(clientAccountNumberPreferenceId).getId());
        assertEquals(loanAccountNumberPreferenceId, getAccountNumberPreference(loanAccountNumberPreferenceId).getId());
        assertEquals(savingsAccountNumberPreferenceId, getAccountNumberPreference(savingsAccountNumberPreferenceId).getId());
        assertEquals(groupsAccountNumberPreferenceId, getAccountNumberPreference(groupsAccountNumberPreferenceId).getId());
        assertEquals(centerAccountNumberPreferenceId, getAccountNumberPreference(centerAccountNumberPreferenceId).getId());
    }

    public void verifyUpdationOfAccountNumberPreferences(final Long accountNumberPreferenceId) {
        assertEquals(accountNumberPreferenceId, getAccountNumberPreference(accountNumberPreferenceId).getId());
        LOG.info("Verified account number preference (ID: {})", accountNumberPreferenceId);
    }
}
