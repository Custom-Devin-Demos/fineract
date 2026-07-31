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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestLine;
import feign.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.client.models.AdvancedPaymentData;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.GetLoansLoanIdLoanTransactionEnumData;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.GetLoansLoanIdTransactions;
import org.apache.fineract.client.models.PostLoansLoanIdChargesRequest;
import org.apache.fineract.client.models.PostLoansLoanIdRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsRequest;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsResponse;
import org.apache.fineract.client.models.PostLoansLoanIdTransactionsTransactionIdRequest;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleProcessingType;
import org.apache.fineract.portfolio.loanaccount.loanschedule.domain.LoanScheduleType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LoanWriteOffWithAdvancedPaymentAllocationTest extends BaseLoanIntegrationTest {

    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder().appendPattern(DATE_FORMAT).toFormatter();

    private static final ObjectMapper RAW_MAPPER = new ObjectMapper();
    private static final RawApi RAW = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);
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
    public void loanWriteOffWithAdvancedPaymentAllocationTest() {
        // create loan product with Advanced Payment Allocation Strategy with default allocation with future installment
        // allocation as NEXT_INSTALLMENT
        String futureInstallmentAllocationRule = "NEXT_INSTALLMENT";
        AdvancedPaymentData defaultAllocation = createDefaultPaymentAllocation(futureInstallmentAllocationRule);

        Integer loanProductId = createLoanProduct(defaultAllocation);
        Assertions.assertNotNull(loanProductId);

        String loanExternalIdStr = UUID.randomUUID().toString();
        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final Integer loanId = createLoanAccountAndDisbursePrincipalAmount(clientId, loanProductId, loanExternalIdStr);

        // apply charges
        Long feeCharge = chargesHelper.createCharges(new ChargeRequest().active(true).amount(200.0).chargeAppliesTo(1)
                .chargeCalculationType(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT).currencyCode("USD").locale("en").monthDayFormat("dd MMM")
                .name(Utils.uniqueRandomStringGenerator("Charge_Loans_", 6)).chargeTimeType(ChargesHelper.CHARGE_SPECIFIED_DUE_DATE)
                .chargePaymentMode(0).penalty(false)).getResourceId();

        LocalDate targetDate = LocalDate.of(2022, 9, 5);
        final String feeCharge1AddedDate = DATE_FORMATTER.format(targetDate);
        loanTransactionHelper.addLoanCharge(loanId.longValue(), new PostLoansLoanIdChargesRequest().chargeId(feeCharge).amount(200.0)
                .dueDate(feeCharge1AddedDate).dateFormat(DATE_FORMAT).locale("en_GB"));

        // make Repayment
        final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("9 September 2022").locale("en")
                        .transactionAmount(100.0));

        // write off loan and verify amount
        final PostLoansLoanIdTransactionsResponse writeOffTransaction = loanTransactionHelper.writeOffLoanAccount(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("10 September 2022").locale("en")
                        .note("test WriteOff"));

        GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getClosedWrittenOff());

        // verify amounts for write-off transaction
        verifyTransaction(LocalDate.of(2022, 9, 10), 1100.0f, 1000.0f, 0.0f, 100.0f, 0.0f, loanId, "writeOff");

    }

    @Test
    public void loanUndoRepaymentAfterWriteOffShouldGiveErrorTest() {
        // create loan product with Advanced Payment Allocation Strategy with default allocation with future installment
        // allocation as NEXT_INSTALLMENT
        String futureInstallmentAllocationRule = "NEXT_INSTALLMENT";
        AdvancedPaymentData defaultAllocation = createDefaultPaymentAllocation(futureInstallmentAllocationRule);

        Integer loanProductId = createLoanProduct(defaultAllocation);
        Assertions.assertNotNull(loanProductId);

        String loanExternalIdStr = UUID.randomUUID().toString();
        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final Integer loanId = createLoanAccountAndDisbursePrincipalAmount(clientId, loanProductId, loanExternalIdStr);

        // make Repayment
        final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("9 September 2022").locale("en")
                        .transactionAmount(250.0));

        // write off loan
        final PostLoansLoanIdTransactionsResponse writeOffTransaction = loanTransactionHelper.writeOffLoanAccount(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("10 September 2022").locale("en")
                        .note("test WriteOff"));

        GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getClosedWrittenOff());

        // reverse repayment
        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> loanTransactionHelper.reverseLoanTransaction(loanExternalIdStr, repaymentTransaction.getResourceId(),
                        new PostLoansLoanIdTransactionsTransactionIdRequest().transactionDate("9 September 2022").locale("en")
                                .dateFormat("dd MMMM yyyy").transactionAmount(0.0)));

        assertEquals(403, exception.getResponse().code());
        assertTrue(exception.getMessage().contains("error.msg.loan.written.off.update.not.allowed"));
    }

    @Test
    public void loanBackdatedRepaymentAfterWriteOffShouldGiveErrorTest() {
        // create loan product with Advanced Payment Allocation Strategy with default allocation with future installment
        // allocation as NEXT_INSTALLMENT
        String futureInstallmentAllocationRule = "NEXT_INSTALLMENT";
        AdvancedPaymentData defaultAllocation = createDefaultPaymentAllocation(futureInstallmentAllocationRule);

        Integer loanProductId = createLoanProduct(defaultAllocation);
        Assertions.assertNotNull(loanProductId);

        String loanExternalIdStr = UUID.randomUUID().toString();
        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final Integer loanId = createLoanAccountAndDisbursePrincipalAmount(clientId, loanProductId, loanExternalIdStr);

        // make Repayment
        final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("9 September 2022").locale("en")
                        .transactionAmount(250.0));

        // write off loan
        final PostLoansLoanIdTransactionsResponse writeOffTransaction = loanTransactionHelper.writeOffLoanAccount(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("10 September 2022").locale("en")
                        .note("test WriteOff"));

        GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getClosedWrittenOff());

        // backdate repayment after write-off
        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> loanTransactionHelper.makeLoanRepayment(loanExternalIdStr, new PostLoansLoanIdTransactionsRequest()
                        .dateFormat("dd MMMM yyyy").transactionDate("8 September 2022").locale("en").transactionAmount(50.0)));

        assertEquals(400, exception.getResponse().code());
        assertTrue(exception.getMessage().contains("error.msg.loan.must.be.active.fully.paid.or.overpaid"));
    }

    @Test
    public void loanUndoWriteOffShouldGiveErrorTest() {
        // create loan product with Advanced Payment Allocation Strategy with default allocation with future installment
        // allocation as NEXT_INSTALLMENT
        String futureInstallmentAllocationRule = "NEXT_INSTALLMENT";
        AdvancedPaymentData defaultAllocation = createDefaultPaymentAllocation(futureInstallmentAllocationRule);

        Integer loanProductId = createLoanProduct(defaultAllocation);
        Assertions.assertNotNull(loanProductId);

        String loanExternalIdStr = UUID.randomUUID().toString();
        final Integer clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        final Integer loanId = createLoanAccountAndDisbursePrincipalAmount(clientId, loanProductId, loanExternalIdStr);

        // make Repayment
        final PostLoansLoanIdTransactionsResponse repaymentTransaction = loanTransactionHelper.makeLoanRepayment(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("9 September 2022").locale("en")
                        .transactionAmount(250.0));

        // write off loan
        final PostLoansLoanIdTransactionsResponse writeOffTransaction = loanTransactionHelper.writeOffLoanAccount(loanExternalIdStr,
                new PostLoansLoanIdTransactionsRequest().dateFormat("dd MMMM yyyy").transactionDate("10 September 2022").locale("en")
                        .note("test WriteOff"));

        GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails((long) loanId);
        assertTrue(loanDetails.getStatus().getClosedWrittenOff());

        // reverse write-off
        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> loanTransactionHelper.reverseLoanTransaction(loanExternalIdStr, writeOffTransaction.getResourceId(),
                        new PostLoansLoanIdTransactionsTransactionIdRequest().transactionDate("8 September 2022").locale("en")
                                .dateFormat("dd MMMM yyyy").transactionAmount(0.0)));

        assertEquals(403, exception.getResponse().code());
        assertTrue(exception.getMessage().contains("error.msg.loan.written.off.update.not.allowed"));
    }

    private Integer createLoanProduct(AdvancedPaymentData... advancedPaymentData) {
        String loanProductCreateJSON = new LoanProductTestBuilder().withPrincipal("15,000.00").withNumberOfRepayments("4")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("1")
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestTypeAsDecliningBalance()
                .addAdvancedPaymentAllocation(advancedPaymentData).withLoanScheduleType(LoanScheduleType.PROGRESSIVE)
                .withLoanScheduleProcessingType(LoanScheduleProcessingType.HORIZONTAL).build();
        return body(RAW.createLoanProduct(json(loanProductCreateJSON))).get("resourceId").intValue();

    }

    private Integer createLoanAccountAndDisbursePrincipalAmount(final Integer clientID, final Integer loanProductID,
            final String externalId) {

        String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("1000").withLoanTermFrequency("30")
                .withLoanTermFrequencyAsDays().withNumberOfRepayments("1").withRepaymentEveryAfter("30").withRepaymentFrequencyTypeAsDays()
                .withInterestRatePerPeriod("0").withInterestTypeAsFlatBalance().withAmortizationTypeAsEqualPrincipalPayments()
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod().withExpectedDisbursementDate("03 September 2022")
                .withSubmittedOnDate("01 September 2022").withLoanType("individual").withExternalId(externalId)
                .withRepaymentStrategy("advanced-payment-allocation-strategy").build(clientID.toString(), loanProductID.toString(), null);

        final Integer loanId = body(RAW.createLoan(json(loanApplicationJSON))).get("loanId").intValue();
        loanTransactionHelper.approveLoan(loanId.longValue(), new PostLoansLoanIdRequest().approvedOnDate("02 September 2022")
                .approvedLoanAmount(new BigDecimal("1000")).dateFormat(DATE_FORMAT).locale("en"));
        loanTransactionHelper.disburseLoan(loanId.longValue(), new PostLoansLoanIdRequest().actualDisbursementDate("03 September 2022")
                .dateFormat(DATE_FORMAT).locale("en").transactionAmount(new BigDecimal("1000")).note("DISBURSE NOTE"));
        return loanId;
    }

    private void verifyTransaction(final LocalDate transactionDate, final Float transactionAmount, final Float principalPortion,
            final Float interestPortion, final Float feePortion, final Float penaltyPortion, final Integer loanID,
            final String transactionOfType) {
        List<GetLoansLoanIdTransactions> transactions = loanTransactionHelper.getLoanDetails(loanID.longValue()).getTransactions();
        boolean isTransactionFound = false;
        for (GetLoansLoanIdTransactions transaction : transactions) {
            boolean isTransaction = matchesType(transaction.getType(), transactionOfType);

            if (isTransaction) {
                LocalDate transactionEntryDate = transaction.getDate();

                if (transactionDate.isEqual(transactionEntryDate)) {
                    isTransactionFound = true;
                    assertEquals(transactionAmount, toFloat(transaction.getAmount()), "Mismatch in transaction amounts");
                    assertEquals(principalPortion, toFloat(transaction.getPrincipalPortion()), "Mismatch in transaction amounts");
                    assertEquals(interestPortion, toFloat(transaction.getInterestPortion()), "Mismatch in transaction amounts");
                    assertEquals(feePortion, toFloat(transaction.getFeeChargesPortion()), "Mismatch in transaction amounts");
                    assertEquals(penaltyPortion, toFloat(transaction.getPenaltyChargesPortion()), "Mismatch in transaction amounts");
                    break;
                }
            }
        }
        assertTrue(isTransactionFound, "No Transaction entries are posted");
    }

    private static boolean matchesType(final GetLoansLoanIdLoanTransactionEnumData type, final String transactionOfType) {
        if (type == null) {
            return false;
        }
        if ("writeOff".equals(transactionOfType)) {
            return Boolean.TRUE.equals(type.getWriteOff());
        }
        throw new IllegalArgumentException("Unsupported transaction type: " + transactionOfType);
    }

    private static Float toFloat(final BigDecimal value) {
        return value == null ? 0.0f : value.floatValue();
    }

}
