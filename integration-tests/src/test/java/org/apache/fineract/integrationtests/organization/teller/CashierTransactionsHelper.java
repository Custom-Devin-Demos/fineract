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

import java.time.LocalDate;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashiersIdSummaryAndTransactionsResponse;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashiersIdTransactionsResponse;
import org.apache.fineract.client.models.PostTellersRequest;
import org.apache.fineract.client.models.PostTellersResponse;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersRequest;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersResponse;
import org.apache.fineract.integrationtests.common.Utils;

public class CashierTransactionsHelper {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String LOCALE = "en";

    private final FineractFeignClient fineractClient;

    public CashierTransactionsHelper(final FineractFeignClient fineractClient) {
        this.fineractClient = fineractClient;
    }

    public GetTellersTellerIdCashiersCashiersIdTransactionsResponse getTellersTellerIdCashiersCashiersIdTransactionsResponse(Long tellerId,
            Long cashierId, String currencyCode, int offset, int limit, String orderBy, String sortOrder) {
        return ok(() -> fineractClient.tellerCashManagement().retrieveCashierTransactions(tellerId, cashierId, currencyCode, offset, limit,
                orderBy, sortOrder));
    }

    public GetTellersTellerIdCashiersCashiersIdSummaryAndTransactionsResponse getTellersTellerIdCashiersCashiersIdSummaryAndTransactionsResponse(
            Long tellerId, Long cashierId, String currencyCode, int offset, int limit, String orderBy, String sortOrder) {
        return ok(() -> fineractClient.tellerCashManagement().retrieveCashierTransactionsWithSummary(tellerId, cashierId, currencyCode,
                offset, limit, orderBy, sortOrder));
    }

    public PostTellersResponse createTeller() {
        final PostTellersRequest request = new PostTellersRequest()//
                .officeId(1L)//
                .name(Utils.uniqueRandomStringGenerator("Teller 1", 5))//
                .description(Utils.uniqueRandomStringGenerator("Teller For Testing", 4))//
                .status(PostTellersRequest.StatusEnum.ACTIVE)//
                .startDate(LocalDate.of(2011, 9, 20))//
                .dateFormat(DATE_FORMAT)//
                .locale(LOCALE);
        return ok(() -> fineractClient.tellerCashManagement().createTeller(request));
    }

    public PostTellersTellerIdCashiersResponse createCashier(final Long tellerId) {
        final PostTellersTellerIdCashiersRequest request = new PostTellersTellerIdCashiersRequest()//
                .staffId(1L)//
                .description(Utils.uniqueRandomStringGenerator("test__", 4))//
                .startDate(LocalDate.of(2023, 1, 1))//
                .endDate(LocalDate.of(2023, 12, 31))//
                .isFullDay(true)//
                .dateFormat(DATE_FORMAT)//
                .locale(LOCALE);
        return ok(() -> fineractClient.tellerCashManagement().createCashierForTeller(tellerId, request));
    }

}
