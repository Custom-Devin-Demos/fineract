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
package org.apache.fineract.integrationtests.bulkimport.importhandler.loan;

import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import feign.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.apache.fineract.client.models.ChargeRequest;
import org.apache.fineract.client.models.FundRequest;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.PaymentTypeCreateRequest;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostGroupsRequest;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.client.models.StaffData;
import org.apache.fineract.infrastructure.bulkimport.constants.LoanConstants;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.integrationtests.bulkimport.importhandler.LocalContentStorageUtil;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Order(3)
public class LoanImportHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoanImportHandlerTest.class);
    public static final String DATE_FORMAT = "dd MMMM yyyy";

    @Test
    public void testLoanImport() throws InterruptedException, IOException, ParseException {
        // in order to populate helper sheets
        OfficeHelper officeHelper = new OfficeHelper();
        Integer outcome_office_creation = officeHelper.createOffice(java.time.LocalDate.of(2000, 5, 2)).getResourceId().intValue();
        Assertions.assertNotNull(outcome_office_creation, "Could not create office");

        GetOfficesResponse office = officeHelper.retrieveOffice(outcome_office_creation.longValue());
        Assertions.assertNotNull(office, "Could not retrieve created office");

        String firstName = Utils.randomStringGenerator("Client_FirstName_", 5);
        String lastName = Utils.randomStringGenerator("Client_LastName_", 4);
        String externalId = UUID.randomUUID().toString();

        Long outcome_client_creation = ClientHelper.createClient(new PostClientsRequest().officeId(outcome_office_creation.longValue())
                .firstname(firstName).lastname(lastName).externalId(externalId).dateFormat(DATE_FORMAT).legalFormId(1L).locale("en")
                .active(true).activationDate("04 March 2011")).getClientId();
        Assertions.assertNotNull(outcome_client_creation, "Could not create client");

        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(outcome_client_creation, collateralId);
        Assertions.assertNotNull(clientCollateralId);

        final String chargeName = Utils.uniqueRandomStringGenerator("Charge_Loans_", 6);
        final double chargeAmount = 100.0;
        final int chargeCalculationType = ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT;
        final Long disbursementChargeId = new ChargesHelper().createCharges(new ChargeRequest().active(true).amount(chargeAmount)
                .chargeAppliesTo(1).chargeCalculationType(chargeCalculationType).chargeTimeType(ChargesHelper.CHARGE_DISBURSEMENT_FEE)
                .chargePaymentMode(0).currencyCode("USD").locale("en").monthDayFormat("dd MMM").name(chargeName)).getResourceId();
        Assertions.assertNotNull(disbursementChargeId, "Could not create charge");

        // in order to populate helper sheets
        Long outcome_group_creation = GroupHelper
                .createGroup(new PostGroupsRequest().officeId(1L).name(Utils.randomStringGenerator("Group_Name_", 5)).active(false))
                .getGroupId();
        Assertions.assertNotNull(outcome_group_creation, "Could not create group");

        // in order to populate helper sheets
        Long outcome_staff_creation = StaffHelper.createStaff(new StaffCreateRequest().officeId(1L)
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5)).lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))
                .isLoanOfficer(true).locale("en").dateFormat("dd MMMM yyyy").joiningDate("20 September 2011")).getResourceId();
        Assertions.assertNotNull(outcome_staff_creation, "Could not create staff");

        StaffData staffData = StaffHelper.getStaff(outcome_staff_creation);
        Assertions.assertNotNull(staffData, "Could not retrieve created staff");

        final String loanProductName = Utils.uniqueRandomStringGenerator("LOAN_PRODUCT_", 6);
        final double principal = 10000.00;
        final int numberOfRepayments = 5;
        final int repaymentEvery = 1;
        final double interestRatePerPeriod = 2.0;
        Long outcome_lp_creation = ok(() -> FineractFeignClientHelper.getFineractFeignClient().loanProducts()
                .createLoanProduct(new PostLoanProductsRequest().name(loanProductName).shortName(Utils.uniqueRandomStringGenerator("", 4))
                        .currencyCode("USD").locale("en").digitsAfterDecimal(2).inMultiplesOf(0).principal(principal).minPrincipal(1000.00)
                        .maxPrincipal(10000000.00).numberOfRepayments(numberOfRepayments).repaymentEvery(repaymentEvery)
                        .repaymentFrequencyType(2L).interestRatePerPeriod(interestRatePerPeriod).interestRateFrequencyType(2)
                        .amortizationType(1).interestType(1).interestCalculationPeriodType(1).inArrearsTolerance(0)
                        .transactionProcessingStrategyCode("mifos-standard-strategy").accountingRule(1).daysInMonthType(1).daysInYearType(1)
                        .isInterestRecalculationEnabled(false)))
                .getResourceId();
        Assertions.assertNotNull(outcome_lp_creation, "Could not create Loan Product");

        final String fundName = Utils.uniqueRandomStringGenerator("", 9);
        Long outcome_fund_creation = ok(() -> FineractFeignClientHelper.getFineractFeignClient().funds()
                .createFund(new FundRequest().name(fundName).externalId(UUID.randomUUID().toString()))).getResourceId();
        Assertions.assertNotNull(outcome_fund_creation, "Could not create Fund");

        String paymentTypeName = PaymentTypeHelper.randomNameGenerator("P_T", 5);
        String paymentTypeDescription = PaymentTypeHelper.randomNameGenerator("PT_Desc", 15);

        var paymentTypesResponse = PaymentTypeHelper.createPaymentType(
                new PaymentTypeCreateRequest().name(paymentTypeName).description(paymentTypeDescription).isCashPayment(true).position(1L));
        Long outcome_payment_creation = paymentTypesResponse.getResourceId();

        Assertions.assertNotNull(outcome_payment_creation, "Could not create payment type");

        Workbook workbook = getLoanWorkbook(DATE_FORMAT);

        // insert dummy data into loan Sheet
        Sheet loanSheet = workbook.getSheet(TemplatePopulateImportConstants.LOANS_SHEET_NAME);
        Row firstLoanRow = loanSheet.getRow(1);
        firstLoanRow.createCell(LoanConstants.OFFICE_NAME_COL).setCellValue(office.getName());
        firstLoanRow.createCell(LoanConstants.LOAN_TYPE_COL).setCellValue("Individual");
        firstLoanRow.createCell(LoanConstants.CLIENT_NAME_COL)
                .setCellValue(firstName + " " + lastName + "(" + outcome_client_creation + ")");
        firstLoanRow.createCell(LoanConstants.CLIENT_EXTERNAL_ID).setCellValue(externalId);
        firstLoanRow.createCell(LoanConstants.PRODUCT_COL).setCellValue(loanProductName);
        firstLoanRow.createCell(LoanConstants.LOAN_OFFICER_NAME_COL).setCellValue(staffData.getDisplayName());

        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(DATE_FORMAT, Locale.US);
        final LocalDate localDate = LocalDate.parse("17 May 2017", dateFormat);

        firstLoanRow.createCell(LoanConstants.SUBMITTED_ON_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.APPROVED_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.DISBURSED_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.DISBURSED_PAYMENT_TYPE_COL).setCellValue(paymentTypeName);
        firstLoanRow.createCell(LoanConstants.FUND_NAME_COL).setCellValue(fundName);
        firstLoanRow.createCell(LoanConstants.PRINCIPAL_COL).setCellValue(principal);
        firstLoanRow.createCell(LoanConstants.NO_OF_REPAYMENTS_COL).setCellValue(numberOfRepayments);
        firstLoanRow.createCell(LoanConstants.REPAID_EVERY_COL).setCellValue(repaymentEvery);
        firstLoanRow.createCell(LoanConstants.REPAID_EVERY_FREQUENCY_COL).setCellValue("Months");
        firstLoanRow.createCell(LoanConstants.LOAN_TERM_COL).setCellValue(repaymentEvery * numberOfRepayments);
        firstLoanRow.createCell(LoanConstants.LOAN_TERM_FREQUENCY_COL).setCellValue("Months");
        firstLoanRow.createCell(LoanConstants.NOMINAL_INTEREST_RATE_COL).setCellValue(interestRatePerPeriod);
        firstLoanRow.createCell(LoanConstants.NOMINAL_INTEREST_RATE_FREQUENCY_COL).setCellValue("Per month");
        firstLoanRow.createCell(LoanConstants.AMORTIZATION_COL).setCellValue("Equal installments");
        firstLoanRow.createCell(LoanConstants.INTEREST_METHOD_COL).setCellValue("Flat");
        firstLoanRow.createCell(LoanConstants.INTEREST_CALCULATION_PERIOD_COL).setCellValue("Same as repayment period");
        firstLoanRow.createCell(LoanConstants.ARREARS_TOLERANCE_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.REPAYMENT_STRATEGY_COL).setCellValue("mifos-standard-strategy");
        firstLoanRow.createCell(LoanConstants.GRACE_ON_PRINCIPAL_PAYMENT_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.GRACE_ON_INTEREST_PAYMENT_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.GRACE_ON_INTEREST_CHARGED_COL).setCellValue(0);
        firstLoanRow.createCell(LoanConstants.FIRST_REPAYMENT_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.TOTAL_AMOUNT_REPAID_COL).setCellValue(6000);
        firstLoanRow.createCell(LoanConstants.LAST_REPAYMENT_DATE_COL).setCellValue(localDate);
        firstLoanRow.createCell(LoanConstants.REPAYMENT_TYPE_COL).setCellValue(paymentTypeName);
        firstLoanRow.createCell(LoanConstants.LOAN_COLLATERAL_ID).setCellValue(collateralId.toString());
        firstLoanRow.createCell(LoanConstants.LOAN_COLLATERAL_QUANTITY).setCellValue("1");
        firstLoanRow.createCell(LoanConstants.CHARGE_NAME_1).setCellValue(chargeName);
        firstLoanRow.createCell(LoanConstants.CHARGE_AMOUNT_1).setCellValue(chargeAmount);
        firstLoanRow.createCell(LoanConstants.CHARGE_AMOUNT_TYPE_1).setCellValue(String.valueOf(chargeCalculationType));

        Path directory = Path.of("").toAbsolutePath().resolve("src").resolve("integrationTest").resolve("resources").resolve("bulkimport")
                .resolve("importhandler").resolve("loan");
        if (!directory.toFile().exists()) {
            directory.toFile().mkdirs();
        }
        File file = directory.resolve("Loan.xls").toFile();
        try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
            workbook.write(outputStream);
        }

        String importDocumentId = importLoanTemplate(file);
        file.delete();
        Assertions.assertNotNull(importDocumentId);

        // Wait for the creation of output excel
        Thread.sleep(1000);

        // check status column of output excel
        String location = LocalContentStorageUtil.path(getOutputTemplateLocation(importDocumentId));
        try (InputStream fileInputStream = Files.newInputStream(Path.of(location))) {
            Workbook outputworkbook = new HSSFWorkbook(fileInputStream);
            Sheet outputLoanSheet = outputworkbook.getSheet(TemplatePopulateImportConstants.LOANS_SHEET_NAME);
            Row row = outputLoanSheet.getRow(1);

            LOG.info("Output location: {}", location);
            LOG.info("Failure reason column: {}", row.getCell(LoanConstants.FAILURE_REPORT_COL).getStringCellValue());

            Assertions.assertEquals("Imported", row.getCell(LoanConstants.STATUS_COL).getStringCellValue());
            outputworkbook.close();
        }
    }

    private Workbook getLoanWorkbook(final String dateFormat) throws IOException {
        Response response = FineractFeignClientHelper.getFineractFeignClient().bulkImportFixed().getLoanTemplate(dateFormat);
        try (InputStream inputStream = response.body().asInputStream()) {
            return new HSSFWorkbook(inputStream);
        }
    }

    private String importLoanTemplate(File file) {
        return ok(() -> FineractFeignClientHelper.getFineractFeignClient().loans().postLoanTemplate("dd MMMM yyyy", "en", file));
    }

    private String getOutputTemplateLocation(final String importDocumentId) {
        return ok(() -> FineractFeignClientHelper.getFineractFeignClient().bulkImport()
                .retriveOutputTemplateLocation(Long.valueOf(importDocumentId)));
    }
}
