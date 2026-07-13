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
package org.apache.fineract.integrationtests.bulkimport.importhandler.client;

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
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.infrastructure.bulkimport.constants.ClientEntityConstants;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.infrastructure.bulkimport.data.GlobalEntityType;
import org.apache.fineract.integrationtests.bulkimport.importhandler.LocalContentStorageUtil;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientEntityImportHandlerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClientEntityImportHandlerTest.class);

    @Test
    public void testClientImport() throws InterruptedException, IOException, ParseException {

        // in order to populate helper sheets
        Long outcome_staff_creation = StaffHelper.createStaff(new StaffCreateRequest().officeId(1L)
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5)).lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))
                .isLoanOfficer(true).locale("en").dateFormat("dd MMMM yyyy").joiningDate("20 September 2011")).getResourceId();
        Assertions.assertNotNull(outcome_staff_creation, "Could not create staff");

        // in order to populate helper sheets
        OfficeHelper officeHelper = new OfficeHelper();
        Integer outcome_office_creation = officeHelper.createOffice(java.time.LocalDate.of(2000, 5, 2)).getResourceId().intValue();
        Assertions.assertNotNull(outcome_office_creation, "Could not create office");

        // in order to populate helper columns in client entity sheet
        CodeHelper codeHelper = new CodeHelper();
        // create constitution
        codeHelper.retrieveOrCreateCodeValue(24L);
        // create client classification
        codeHelper.retrieveOrCreateCodeValue(17L);
        // create client types
        codeHelper.retrieveOrCreateCodeValue(16L);
        // create Address types
        codeHelper.retrieveOrCreateCodeValue(29L);
        // create State
        codeHelper.retrieveOrCreateCodeValue(27L);
        // create Country
        codeHelper.retrieveOrCreateCodeValue(28L);
        // create Main business line
        codeHelper.retrieveOrCreateCodeValue(25L);

        Workbook workbook = getClientEntityWorkbook("dd MMMM yyyy");

        // insert dummy data into client entity sheet
        Sheet clientEntitySheet = workbook.getSheet(TemplatePopulateImportConstants.CLIENT_ENTITY_SHEET_NAME);
        Row firstClientRow = clientEntitySheet.getRow(1);
        firstClientRow.createCell(ClientEntityConstants.NAME_COL).setCellValue(Utils.randomStringGenerator("C_E_", 6));
        Sheet staffSheet = workbook.getSheet(TemplatePopulateImportConstants.STAFF_SHEET_NAME);
        firstClientRow.createCell(ClientEntityConstants.OFFICE_NAME_COL).setCellValue(staffSheet.getRow(1).getCell(0).getStringCellValue());
        firstClientRow.createCell(ClientEntityConstants.STAFF_NAME_COL).setCellValue(staffSheet.getRow(1).getCell(1).getStringCellValue());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.US);
        Date incoporationDate = simpleDateFormat.parse("14 May 2001");
        firstClientRow.createCell(ClientEntityConstants.INCOPORATION_DATE_COL).setCellValue(incoporationDate);
        Date validTill = simpleDateFormat.parse("14 May 2019");
        firstClientRow.createCell(ClientEntityConstants.INCOPORATION_VALID_TILL_COL).setCellValue(validTill);
        firstClientRow.createCell(ClientEntityConstants.MOBILE_NO_COL).setCellValue(Utils.uniqueRandomNumberGenerator(7));
        firstClientRow.createCell(ClientEntityConstants.CLIENT_TYPE_COL)
                .setCellValue(clientEntitySheet.getRow(1).getCell(ClientEntityConstants.LOOKUP_CLIENT_TYPES).getStringCellValue());
        firstClientRow.createCell(ClientEntityConstants.CLIENT_CLASSIFICATION_COL)
                .setCellValue(clientEntitySheet.getRow(1).getCell(ClientEntityConstants.LOOKUP_CLIENT_CLASSIFICATION).getStringCellValue());
        firstClientRow.createCell(ClientEntityConstants.INCOPORATION_NUMBER_COL).setCellValue(Utils.randomNumberGenerator(6));
        firstClientRow.createCell(ClientEntityConstants.MAIN_BUSINESS_LINE)
                .setCellValue(clientEntitySheet.getRow(1).getCell(ClientEntityConstants.LOOKUP_MAIN_BUSINESS_LINE).getStringCellValue());
        firstClientRow.createCell(ClientEntityConstants.CONSTITUTION_COL)
                .setCellValue(clientEntitySheet.getRow(1).getCell(ClientEntityConstants.LOOKUP_CONSTITUTION_COL).getStringCellValue());
        firstClientRow.createCell(ClientEntityConstants.ACTIVE_COL).setCellValue("False");
        Date submittedDate = simpleDateFormat.parse("28 September 2017");
        firstClientRow.createCell(ClientEntityConstants.SUBMITTED_ON_COL).setCellValue(submittedDate);
        firstClientRow.createCell(ClientEntityConstants.ADDRESS_ENABLED).setCellValue("False");

        Path directory = Path.of(System.getProperty("user.home")).resolve("Fineract").resolve("bulkimport").resolve("integration_tests")
                .resolve("importhandler").resolve("client");
        if (!directory.toFile().exists()) {
            directory.toFile().mkdirs();
        }
        File file = directory.resolve("ClientEntity.xls").toFile();
        try (OutputStream outputStream = Files.newOutputStream(file.toPath())) {
            workbook.write(outputStream);
        }

        String importDocumentId = importClientEntityTemplate(file);
        file.delete();
        Assertions.assertNotNull(importDocumentId);

        // Wait for the creation of output excel
        Thread.sleep(1000);

        // check status column of output excel
        String location = LocalContentStorageUtil.path(getOutputTemplateLocation(importDocumentId));
        try (InputStream fileInputStream = Files.newInputStream(Path.of(location))) {
            Workbook outputWorkbook = new HSSFWorkbook(fileInputStream);
            Sheet outputClientEntitySheet = outputWorkbook.getSheet(TemplatePopulateImportConstants.CLIENT_ENTITY_SHEET_NAME);
            Row row = outputClientEntitySheet.getRow(1);

            LOG.info("Output location: {}", location);
            LOG.info("Failure reason column: {}", row.getCell(ClientEntityConstants.STATUS_COL).getStringCellValue());

            Assertions.assertEquals("Imported", row.getCell(ClientEntityConstants.STATUS_COL).getStringCellValue());
            outputWorkbook.close();
        }
    }

    private Workbook getClientEntityWorkbook(final String dateFormat) throws IOException {
        Response response = FineractFeignClientHelper.getFineractFeignClient().bulkImportFixed()
                .getClientTemplate(GlobalEntityType.CLIENTS_ENTITY.toString(), dateFormat);
        try (InputStream inputStream = response.body().asInputStream()) {
            return new HSSFWorkbook(inputStream);
        }
    }

    private String importClientEntityTemplate(File file) {
        return ok(() -> FineractFeignClientHelper.getFineractFeignClient().clients()
                .postClientTemplate(GlobalEntityType.CLIENTS_ENTITY.toString(), "dd MMMM yyyy", "en", file));
    }

    private String getOutputTemplateLocation(final String importDocumentId) {
        return ok(() -> FineractFeignClientHelper.getFineractFeignClient().bulkImport()
                .retriveOutputTemplateLocation(Long.valueOf(importDocumentId)));
    }
}
