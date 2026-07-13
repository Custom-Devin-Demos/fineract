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
package org.apache.fineract.integrationtests.bulkimport.importhandler.savings;

import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import feign.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostGroupsRequest;
import org.apache.fineract.client.models.PostSavingsProductsRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.client.models.StaffData;
import org.apache.fineract.infrastructure.bulkimport.constants.SavingsConstants;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.integrationtests.bulkimport.importhandler.LocalContentStorageUtil;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SavingsImportHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(SavingsImportHandlerTest.class);

    public static final String DATE_FORMAT = "dd MMMM yyyy";

    @Test
    public void testSavingsImport() throws InterruptedException, IOException, ParseException {

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
                .legalFormId(1L).firstname(firstName).lastname(lastName).externalId(externalId).dateFormat(DATE_FORMAT).locale("en")
                .active(true).activationDate("04 March 2011")).getClientId();
        Assertions.assertNotNull(outcome_client_creation, "Could not create client");

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

        Long outcome_sp_creaction = SavingsProductHelper.createSavingsProduct(new PostSavingsProductsRequest()
                .name(Utils.uniqueRandomStringGenerator("SAVINGS_PRODUCT_", 6)).shortName(Utils.uniqueRandomStringGenerator("", 4))
                .description(Utils.randomStringGenerator("", 20)).currencyCode("USD").digitsAfterDecimal(4).inMultiplesOf(0)
                .nominalAnnualInterestRate(10.0).interestCompoundingPeriodType(4).interestPostingPeriodType(4).interestCalculationType(1)
                .interestCalculationDaysInYearType(365).accountingRule(1).locale("en_GB").withdrawalFeeForTransfers(true)
                .allowOverdraft(false).enforceMinRequiredBalance(false).withHoldTax(false)).getResourceId();
        Assertions.assertNotNull(outcome_sp_creaction, "Could not create Savings product");

        Workbook workbook = getSavingsWorkbook("dd MMMM yyyy");

        // insert dummy data into Savings sheet
        Sheet savingsSheet = workbook.getSheet(TemplatePopulateImportConstants.SAVINGS_ACCOUNTS_SHEET_NAME);
        Row firstSavingsRow = savingsSheet.getRow(1);
        firstSavingsRow.createCell(SavingsConstants.OFFICE_NAME_COL).setCellValue(office.getName());
        firstSavingsRow.createCell(SavingsConstants.SAVINGS_TYPE_COL).setCellValue("Individual");
        firstSavingsRow.createCell(SavingsConstants.CLIENT_NAME_COL)
                .setCellValue(firstName + " " + lastName + "(" + outcome_client_creation + ")");
        Sheet savingsProductSheet = workbook.getSheet(TemplatePopulateImportConstants.PRODUCT_SHEET_NAME);
        firstSavingsRow.createCell(SavingsConstants.PRODUCT_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(1).getStringCellValue());
        firstSavingsRow.createCell(SavingsConstants.FIELD_OFFICER_NAME_COL).setCellValue(staffData.getDisplayName());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.US);
        Date date = simpleDateFormat.parse("13 May 2017");
        firstSavingsRow.createCell(SavingsConstants.SUBMITTED_ON_DATE_COL).setCellValue(date);
        firstSavingsRow.createCell(SavingsConstants.APPROVED_DATE_COL).setCellValue(date);
        firstSavingsRow.createCell(SavingsConstants.ACTIVATION_DATE_COL).setCellValue(date);
        firstSavingsRow.createCell(SavingsConstants.CURRENCY_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(10).getStringCellValue());
        firstSavingsRow.createCell(SavingsConstants.DECIMAL_PLACES_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(11).getNumericCellValue());
        safeNumericValueSetter(firstSavingsRow, SavingsConstants.IN_MULTIPLES_OF_COL, savingsProductSheet, 1, 12);
        firstSavingsRow.createCell(SavingsConstants.NOMINAL_ANNUAL_INTEREST_RATE_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(2).getNumericCellValue());
        firstSavingsRow.createCell(SavingsConstants.INTEREST_COMPOUNDING_PERIOD_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(3).getStringCellValue());
        firstSavingsRow.createCell(SavingsConstants.INTEREST_POSTING_PERIOD_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(4).getStringCellValue());
        firstSavingsRow.createCell(SavingsConstants.INTEREST_CALCULATION_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(5).getStringCellValue());
        firstSavingsRow.createCell(SavingsConstants.INTEREST_CALCULATION_DAYS_IN_YEAR_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(6).getStringCellValue());
        firstSavingsRow.createCell(SavingsConstants.MIN_OPENING_BALANCE_COL).setCellValue(1000.0);
        firstSavingsRow.createCell(SavingsConstants.LOCKIN_PERIOD_COL).setCellValue(1);
        firstSavingsRow.createCell(SavingsConstants.LOCKIN_PERIOD_FREQUENCY_COL).setCellValue("Weeks");
        firstSavingsRow.createCell(SavingsConstants.APPLY_WITHDRAWAL_FEE_FOR_TRANSFERS).setCellValue("False");
        firstSavingsRow.createCell(SavingsConstants.ALLOW_OVER_DRAFT_COL).setCellValue("False");
        firstSavingsRow.createCell(SavingsConstants.OVER_DRAFT_LIMIT_COL)
                .setCellValue(savingsProductSheet.getRow(1).getCell(15).getNumericCellValue());

        Path directory = Path.of("").toAbsolutePath().resolve("src").resolve("integrationTest").resolve("resources").resolve("bulkimport")
                .resolve("importhandler").resolve("savings");
        if (!directory.toFile().exists()) {
            directory.toFile().mkdirs();
        }
        File file = directory.resolve("Savings.xls").toFile();
        try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
            workbook.write(outputStream);
        }

        String importDocumentId = importSavingsTemplate(file);
        file.delete();
        Assertions.assertNotNull(importDocumentId);

        // Wait for the creation of output excel
        Thread.sleep(1000);

        // check status column of output excel
        String location = LocalContentStorageUtil.path(getOutputTemplateLocation(importDocumentId));
        try (InputStream fileInputStream = Files.newInputStream(Path.of(location))) {
            Workbook wb = new HSSFWorkbook(fileInputStream);
            Sheet sheet = wb.getSheet(TemplatePopulateImportConstants.SAVINGS_ACCOUNTS_SHEET_NAME);
            Row row = sheet.getRow(1);

            LOG.info("Output location: {}", location);
            LOG.info("Failure reason column: {}", row.getCell(SavingsConstants.STATUS_COL).getStringCellValue());

            Assertions.assertEquals("Imported", row.getCell(SavingsConstants.STATUS_COL).getStringCellValue());
            wb.close();
        }
    }

    private Workbook getSavingsWorkbook(final String dateFormat) throws IOException {
        Response response = FineractFeignClientHelper.getFineractFeignClient().bulkImportFixed().getSavingsTemplate(dateFormat);
        try (InputStream inputStream = response.body().asInputStream()) {
            return new HSSFWorkbook(inputStream);
        }
    }

    private String importSavingsTemplate(File file) {
        return ok(
                () -> FineractFeignClientHelper.getFineractFeignClient().savingsAccount().postSavingsTemplate("dd MMMM yyyy", "en", file));
    }

    private String getOutputTemplateLocation(final String importDocumentId) {
        return ok(() -> FineractFeignClientHelper.getFineractFeignClient().bulkImport()
                .retriveOutputTemplateLocation(Long.valueOf(importDocumentId)));
    }

    private void safeNumericValueSetter(Row targetRow, int targetColId, Sheet sourceSheet, int rowId, int colId) {
        Row row = sourceSheet.getRow(rowId);
        if (row == null) {
            targetRow.createCell(targetColId).setBlank();
        } else {
            Cell cell = row.getCell(colId);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                targetRow.createCell(targetColId).setBlank();
            } else {
                targetRow.createCell(targetColId).setCellValue(cell.getNumericCellValue());
            }
        }
    }
}
