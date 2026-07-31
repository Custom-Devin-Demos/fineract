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
package org.apache.fineract.integrationtests.bulkimport.populator.savings;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import org.apache.fineract.client.models.PostGroupsRequest;
import org.apache.fineract.client.models.PostSavingsProductsRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsProductHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

public class SavingsWorkbookPopulateTest {

    @Test
    public void testSavingsWorkbookPopulate() throws IOException {
        // in order to populate helper sheets
        OfficeHelper officeHelper = new OfficeHelper();
        Integer outcome_office_creation = officeHelper.createOffice(java.time.LocalDate.of(2000, 5, 2)).getResourceId().intValue();
        assertNotNull(outcome_office_creation, "Could not create office");

        // in order to populate helper sheets
        Long outcome_client_creation = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        assertNotNull(outcome_client_creation, "Could not create client");

        // in order to populate helper sheets
        Long outcome_group_creation = GroupHelper
                .createGroup(new PostGroupsRequest().officeId(1L).name(Utils.randomStringGenerator("Group_Name_", 5)).active(false))
                .getGroupId();
        assertNotNull(outcome_group_creation, "Could not create group");

        // in order to populate helper sheets
        Long outcome_staff_creation = StaffHelper.createStaff(new StaffCreateRequest().officeId(1L)
                .firstname(Utils.uniqueRandomStringGenerator("michael_", 5)).lastname(Utils.uniqueRandomStringGenerator("Doe_", 4))
                .isLoanOfficer(true).locale("en").dateFormat("dd MMMM yyyy").joiningDate("20 September 2011")).getResourceId();
        assertNotNull(outcome_staff_creation, "Could not create staff");

        Long outcome_sp_creaction = SavingsProductHelper.createSavingsProduct(new PostSavingsProductsRequest()
                .name(Utils.uniqueRandomStringGenerator("SAVINGS_PRODUCT_", 6)).shortName(Utils.uniqueRandomStringGenerator("", 4))
                .description(Utils.randomStringGenerator("", 20)).currencyCode("USD").digitsAfterDecimal(4).inMultiplesOf(0)
                .nominalAnnualInterestRate(10.0).interestCompoundingPeriodType(4).interestPostingPeriodType(4).interestCalculationType(1)
                .interestCalculationDaysInYearType(365).accountingRule(1).locale("en_GB").withdrawalFeeForTransfers(true)
                .allowOverdraft(false).enforceMinRequiredBalance(false).withHoldTax(false)).getResourceId();
        assertNotNull(outcome_sp_creaction, "Could not create Savings product");

        Workbook workbook = getSavingsWorkbook("dd MMMM yyyy");

        Sheet officeSheet = workbook.getSheet(TemplatePopulateImportConstants.OFFICE_SHEET_NAME);
        Row firstOfficeRow = officeSheet.getRow(1);
        assertNotNull(firstOfficeRow.getCell(1), "No offices found ");

        Sheet clientSheet = workbook.getSheet(TemplatePopulateImportConstants.CLIENT_SHEET_NAME);
        Row firstClientRow = clientSheet.getRow(1);
        assertNotNull(firstClientRow.getCell(1), "No clients found ");

        Sheet groupSheet = workbook.getSheet(TemplatePopulateImportConstants.GROUP_SHEET_NAME);
        Row firstGroupRow = groupSheet.getRow(1);
        assertNotNull(firstGroupRow.getCell(1), "No groups found ");

        Sheet staffSheet = workbook.getSheet(TemplatePopulateImportConstants.STAFF_SHEET_NAME);
        Row firstStaffRow = staffSheet.getRow(1);
        assertNotNull(firstStaffRow.getCell(1), "No staff found ");

        Sheet productSheet = workbook.getSheet(TemplatePopulateImportConstants.PRODUCT_SHEET_NAME);
        Row firstProductRow = productSheet.getRow(1);
        assertNotNull(firstProductRow.getCell(1), "No products found ");
    }

    private Workbook getSavingsWorkbook(final String dateFormat) throws IOException {
        Response response = FineractFeignClientHelper.getFineractFeignClient().bulkImportFixed().getSavingsTemplate(dateFormat);
        try (InputStream inputStream = response.body().asInputStream()) {
            return new HSSFWorkbook(inputStream);
        }
    }
}
