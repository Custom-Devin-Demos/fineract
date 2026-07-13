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
package org.apache.fineract.integrationtests.common.accounting;

import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import java.util.List;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.AccountRuleRequest;
import org.apache.fineract.client.models.AccountingRuleData;
import org.apache.fineract.client.models.PostAccountingRulesResponse;
import org.apache.fineract.integrationtests.common.Utils;

public class AccountRuleHelper {

    private final FineractFeignClient fineractClient;

    public AccountRuleHelper(final FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public List<AccountingRuleData> getAccountingRules() {
        return ok(() -> fineractClient.accountingRules().retrieveAllAccountingRules());
    }

    public PostAccountingRulesResponse createAccountRule(final Long officeId, final Account accountToCredit, final Account accountToDebit) {
        final String name = Utils.uniqueRandomStringGenerator("ACCOUNTRULE_NAME_", 5);
        final AccountRuleRequest request = new AccountRuleRequest()//
                .name(name)//
                .description(name)//
                .officeId(officeId)//
                .accountToCredit(accountToCredit.getAccountID().longValue())//
                .accountToDebit(accountToDebit.getAccountID().longValue());
        return ok(() -> fineractClient.accountingRules().createAccountingRule(request));
    }

}
