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
package org.apache.fineract.integrationtests.organization.teller;

import static org.apache.fineract.client.feign.util.FeignCalls.ok;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashiersIdTransactionsResponse;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CashierSummaryAndTransactionsTest {

    private CashierTransactionsHelper cashierTransactionsHelper;

    @BeforeEach
    public void setup() {
        final FineractFeignClient fineractClient = FineractFeignClientHelper.getFineractFeignClient();
        cashierTransactionsHelper = new CashierTransactionsHelper(fineractClient);
        createStaff(fineractClient);
        cashierTransactionsHelper.createTeller();
        cashierTransactionsHelper.createCashier(1L);
    }

    private void createStaff(final FineractFeignClient fineractClient) {
        final StaffCreateRequest request = new StaffCreateRequest()//
                .officeId(1L)//
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5))//
                .lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))//
                .isLoanOfficer(true)//
                .joiningDate("20 September 2011")//
                .dateFormat("dd MMMM yyyy")//
                .locale("en");
        ok(() -> fineractClient.staff().createStaff(request));
    }

    @Test
    public void testGetCashierTransactions() {
        Long tellerId = 1L;
        Long cashierId = 1L;

        final GetTellersTellerIdCashiersCashiersIdTransactionsResponse result = cashierTransactionsHelper
                .getTellersTellerIdCashiersCashiersIdTransactionsResponse(tellerId, cashierId, "UGX", 0, 0, null, null);
        assertNotNull(result);
    }

}
