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

import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import org.apache.fineract.client.feign.util.FeignCalls;
import org.apache.fineract.client.models.PostRunaccrualsRequest;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;

public class PeriodicAccrualAccountingHelper {

    public PeriodicAccrualAccountingHelper() {}

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public PeriodicAccrualAccountingHelper(final RequestSpecification requestSpec, final ResponseSpecification responseSpec) {}

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public Object runPeriodicAccrualAccounting(String date) {
        runPeriodicAccrualAccounting(new PostRunaccrualsRequest().dateFormat("dd MMMM yyyy").locale("en_GB").tillDate(date));
        return null;
    }

    public void runPeriodicAccrualAccounting(PostRunaccrualsRequest request) {
        FeignCalls.executeVoid(() -> FineractFeignClientHelper.getFineractFeignClient().periodicAccrualAccounting()
                .executePeriodicAccrualAccounting(request));
    }

}
