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
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.Collections;
import java.util.List;
import net.minidev.json.JSONArray;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.PostLoansRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(LoanTestLifecycleExtension.class)
public class LoanValidationIntegrationTest extends BaseLoanIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoanValidationIntegrationTest.class);
    private static final Gson GSON = new JSON().getGson();

    @Test
    public void checkPrincipalErrors() {
        final Long staffId = StaffHelper.createStaff(new StaffCreateRequest().officeId(1L)
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5)).lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))
                .isLoanOfficer(true).joiningDate("20 September 2011").locale("en").dateFormat("dd MMMM yyyy")).getResourceId();
        String username = Utils.uniqueRandomStringGenerator("user", 8);
        UserHelper.createUser(new PostUsersRequest().username(username).firstname("Test").lastname("User").email("whatever@mifos.org")
                .officeId(1L).staffId(staffId).roles(List.of(1L)).sendPasswordToEmail(false).password("A1b2c3d4e5f$")
                .repeatPassword("A1b2c3d4e5f$"));

        LOG.info("-------------------------Creating Client---------------------------");
        final Long clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        ClientHelper.verifyClientCreatedOnServer(clientID);

        LOG.info("-------------------------Creating Loan---------------------------");
        final Account assetAccount = this.accountHelper.createAssetAccount();
        final Account incomeAccount = this.accountHelper.createIncomeAccount();
        final Account expenseAccount = this.accountHelper.createExpenseAccount();
        final Account overpaymentAccount = this.accountHelper.createLiabilityAccount();

        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withRepaymentStrategy(LoanProductTestBuilder.DEFAULT_STRATEGY) //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails("0", "0")
                .withAccounting("2", new Account[] { assetAccount, incomeAccount, expenseAccount, overpaymentAccount }).build(null);
        final Integer loanProductID = loanTransactionHelper.createLoanProduct(GSON.fromJson(loanProductJSON, PostLoanProductsRequest.class))
                .getResourceId().intValue();

        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal("-1") //
                .withLoanTermFrequency("6") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("6") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsFlatBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate("12 July 2022") //
                .withSubmittedOnDate("10 July 2022") //
                .withRepaymentStrategy(LoanApplicationTestBuilder.DEFAULT_STRATEGY) //
                .withCharges(Collections.emptyList()) //
                .build(clientID.toString(), loanProductID.toString(), null);

        final CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> loanTransactionHelper.applyLoan(GSON.fromJson(loanApplicationJSON, PostLoansRequest.class)));
        assertEquals(400, exception.getResponse().code());
        final String errorBody = exception.getMessage().substring(exception.getMessage().indexOf("errorBody: ") + "errorBody: ".length());
        final DocumentContext json = JsonPath.parse(errorBody);
        final JSONArray errors = json.read("$.errors[*].developerMessage");
        LOG.info("errors: {}", errors);
        assertEquals(1, errors.size());
    }
}
