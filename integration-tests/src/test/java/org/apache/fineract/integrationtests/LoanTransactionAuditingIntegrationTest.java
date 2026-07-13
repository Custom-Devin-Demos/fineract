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

import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.CREATED_BY;
import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.CREATED_DATE;
import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.LAST_MODIFIED_BY;
import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.LAST_MODIFIED_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.apache.fineract.client.models.GetLoansLoanIdStatus;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanTransactionAuditingIntegrationTest extends BaseLoanIntegrationTest {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String TEST_PASSWORD = "A1b2c3d4e5f$";
    private static final Logger LOG = LoggerFactory.getLogger(LoanTransactionAuditingIntegrationTest.class);

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("POST v1/loans/{loanId}?command=disburse")
        Response disburseLoan(@Param("loanId") Integer loanId, JsonNode body);

        @RequestLine("GET v1/internal/loan/{loanId}/transaction/{transactionId}/audit")
        Response getTransactionAudit(@Param("loanId") Integer loanId, @Param("transactionId") Integer transactionId);
    }

    private static JsonNode body(Response response) {
        try (Response r = response) {
            return RAW_MAPPER.readTree(r.body().asInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode json(String raw) {
        try {
            return RAW_MAPPER.readTree(raw);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void checkAuditDates() throws InterruptedException {
        final Long staffId = StaffHelper.createStaff(new StaffCreateRequest().officeId(1L)
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5)).lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))
                .isLoanOfficer(true).locale("en").dateFormat(DATE_FORMAT).joiningDate("20 September 2011")).getResourceId();
        String username = Utils.uniqueRandomStringGenerator("user", 8);
        final Integer userId = UserHelper.createUser(new PostUsersRequest().username(username).firstname("Test").lastname("User")
                .email("whatever@mifos.org").officeId(1L).staffId(staffId).roles(List.of(1L)).sendPasswordToEmail(false)
                .password(TEST_PASSWORD).repeatPassword(TEST_PASSWORD)).getResourceId().intValue();

        LOG.info("-------------------------Creating Client---------------------------");

        final Integer clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        ClientHelper.verifyClientCreatedOnServer(clientID.longValue());
        LOG.info("-------------------------Creating Loan---------------------------");
        final Account assetAccount = this.accountHelper.createAssetAccount();
        final Account incomeAccount = this.accountHelper.createIncomeAccount();
        final Account expenseAccount = this.accountHelper.createExpenseAccount();
        final Account overpaymentAccount = this.accountHelper.createLiabilityAccount();

        final Integer loanProductID = createLoanProduct("0", "0", LoanProductTestBuilder.DEFAULT_STRATEGY, "2", assetAccount, incomeAccount,
                expenseAccount, overpaymentAccount);

        final Integer loanID = applyForLoanApplicationWithPaymentStrategyAndPastMonth(clientID, loanProductID, null, "10000",
                LoanApplicationTestBuilder.DEFAULT_STRATEGY, "10 July 2022", "12 July 2022");
        Assertions.assertNotNull(loanID);
        assertTrue(getLoanStatus(loanID).getPendingApproval());

        LOG.info("-----------------------------------APPROVE LOAN-----------------------------------------");
        loanTransactionHelper.approveLoan(loanID.longValue(),
                new PostLoansLoanIdRequest().approvedOnDate("11 July 2022").dateFormat(DATE_FORMAT).locale("en"));
        assertFalse(getLoanStatus(loanID).getPendingApproval());

        ObjectNode disburseBody = RAW_MAPPER.createObjectNode();
        disburseBody.put("locale", "en");
        disburseBody.put("dateFormat", DATE_FORMAT);
        disburseBody.put("actualDisbursementDate", "11 July 2022");
        disburseBody.put("netDisbursalAmount", "10000");
        disburseBody.put("note", "DISBURSE NOTE");
        body(RAW.disburseLoan(loanID, disburseBody));
        assertTrue(getLoanStatus(loanID).getActive());

        OffsetDateTime now = Utils.getAuditDateTimeToCompare();
        final PostLoansLoanIdTransactionsResponse repaymentDetails = loanTransactionHelper.makeLoanRepayment(loanID.longValue(),
                new PostLoansLoanIdTransactionsRequest().dateFormat(DATE_FORMAT).transactionDate("11 July 2022").locale("en")
                        .transactionAmount(100.0));
        Integer transactionId = repaymentDetails.getResourceId().intValue();
        JsonNode auditFieldsResponse = body(RAW.getTransactionAudit(loanID, transactionId));

        OffsetDateTime createdDate = OffsetDateTime.parse(auditFieldsResponse.get(CREATED_DATE).asText(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        OffsetDateTime lastModifiedDate = OffsetDateTime.parse(auditFieldsResponse.get(LAST_MODIFIED_DATE).asText(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        LOG.info("-------------------------Check Audit dates---------------------------");
        assertEquals(1, auditFieldsResponse.get(CREATED_BY).asInt());
        assertEquals(1, auditFieldsResponse.get(LAST_MODIFIED_BY).asInt());
        assertTrue(DateUtils.isEqual(now, createdDate, ChronoUnit.MINUTES));
        assertTrue(DateUtils.isEqual(now, lastModifiedDate, ChronoUnit.MINUTES));

        Thread.sleep(2000);

        OffsetDateTime now2 = Utils.getAuditDateTimeToCompare();
        Calls.ok(FineractClientHelper.createNewFineractClient(username, TEST_PASSWORD).loanTransactions
                .adjustLoanTransaction(loanID.longValue(), transactionId.longValue(), new PostLoansLoanIdTransactionsTransactionIdRequest()
                        .transactionDate("11 July 2022").transactionAmount(0.0).dateFormat(DATE_FORMAT).locale("en"), "undo"));

        auditFieldsResponse = body(RAW.getTransactionAudit(loanID, transactionId));

        OffsetDateTime createdDate2 = OffsetDateTime.parse(auditFieldsResponse.get(CREATED_DATE).asText(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        lastModifiedDate = OffsetDateTime.parse(auditFieldsResponse.get(LAST_MODIFIED_DATE).asText(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        LOG.info("-------------------------Check Audit dates---------------------------");
        assertEquals(1, auditFieldsResponse.get(CREATED_BY).asInt());
        assertTrue(DateUtils.isEqual(now, createdDate2, ChronoUnit.MINUTES));
        assertTrue(DateUtils.isEqual(createdDate, createdDate2));

        assertEquals(userId.intValue(), auditFieldsResponse.get(LAST_MODIFIED_BY).asInt());
        assertTrue(DateUtils.isEqual(now2, lastModifiedDate, ChronoUnit.MINUTES));
    }

    private GetLoansLoanIdStatus getLoanStatus(final Integer loanID) {
        return loanTransactionHelper.getLoanDetails(loanID.longValue()).getStatus();
    }

    private Integer applyForLoanApplicationWithPaymentStrategyAndPastMonth(final Integer clientID, final Integer loanProductID,
            final String savingsId, String principal, final String repaymentStrategy, final String submittedOnDate,
            final String disbursementDate) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");

        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("6") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("6") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsFlatBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate(disbursementDate) //
                .withSubmittedOnDate(submittedOnDate) //
                .withRepaymentStrategy(repaymentStrategy) //
                .build(clientID.toString(), loanProductID.toString(), savingsId);
        return body(RAW.createLoan(json(loanApplicationJSON))).get("loanId").intValue();
    }

    private Integer createLoanProduct(final String inMultiplesOf, final String digitsAfterDecimal, final String repaymentStrategy,
            final String accountingRule, final Account... accounts) {
        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal("10000000.00") //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("2") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withRepaymentStrategy(repaymentStrategy) //
                .withAmortizationTypeAsEqualPrincipalPayment() //
                .withInterestTypeAsDecliningBalance() //
                .currencyDetails(digitsAfterDecimal, inMultiplesOf).withAccounting(accountingRule, accounts).build(null);
        return body(RAW.createLoanProduct(json(loanProductJSON))).get("resourceId").intValue();
    }

}
