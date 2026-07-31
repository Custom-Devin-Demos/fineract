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
package org.apache.fineract.integrationtests.bulkimport.populator.client;

import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.infrastructure.bulkimport.data.GlobalEntityType;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClientEntityWorkbookPopulatorTest {

    @Test
    public void testClientEntityWorkbookPopulate() throws IOException {
        // in order to populate helper sheets
        Long outcome_staff_creation = StaffHelper.createStaff(new StaffCreateRequest().officeId(1L)
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5)).lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))
                .isLoanOfficer(true).locale("en").dateFormat("dd MMMM yyyy").joiningDate("20 September 2011")).getResourceId();
        Assertions.assertNotNull(outcome_staff_creation, "Could not create staff");

        // in order to populate helper sheets
        OfficeHelper officeHelper = new OfficeHelper();
        Integer outcome_office_creation = officeHelper.createOffice(java.time.LocalDate.of(2000, 5, 2)).getResourceId().intValue();
        Assertions.assertNotNull(outcome_office_creation, "Could not create office");

        Workbook workbook = getClientEntityWorkbook("dd MMMM yyyy");
        Sheet officeSheet = workbook.getSheet(TemplatePopulateImportConstants.OFFICE_SHEET_NAME);
        Row firstOfficeRow = officeSheet.getRow(1);
        Assertions.assertNotNull(firstOfficeRow.getCell(1), "No offices found for given OfficeId ");
        Sheet staffSheet = workbook.getSheet(TemplatePopulateImportConstants.STAFF_SHEET_NAME);
        Row firstStaffRow = staffSheet.getRow(1);
        Assertions.assertNotNull(firstStaffRow.getCell(1), "No staff found for given staffId");
    }

    private Workbook getClientEntityWorkbook(final String dateFormat) throws IOException {
        Response response = FineractFeignClientHelper.getFineractFeignClient().bulkImportFixed()
                .getClientTemplate(GlobalEntityType.CLIENTS_ENTITY.toString(), dateFormat);
        try (InputStream inputStream = response.body().asInputStream()) {
            return new HSSFWorkbook(inputStream);
        }
    }
}
