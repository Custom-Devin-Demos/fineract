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
package org.apache.fineract.integrationtests.bulkimport.populator.loan;

import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import feign.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.apache.fineract.client.models.FundRequest;
import org.apache.fineract.client.models.PaymentTypeCreateRequest;
import org.apache.fineract.client.models.PostGroupsRequest;
import org.apache.fineract.client.models.PostLoanProductsRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LoanWorkbookPopulatorTest {

    @Test
    public void testLoanWorkbookPopulate() throws IOException {
        // in order to populate helper sheets
        OfficeHelper officeHelper = new OfficeHelper();
        Integer outcome_office_creation = officeHelper.createOffice(java.time.LocalDate.of(2000, 5, 2)).getResourceId().intValue();
        Assertions.assertNotNull(outcome_office_creation, "Could not create office");

        // in order to populate helper sheets
        Long outcome_client_creation = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
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

        Long outcome_lp_creaion = ok(() -> FineractFeignClientHelper.getFineractFeignClient().loanProducts()
                .createLoanProduct(new PostLoanProductsRequest().name(Utils.uniqueRandomStringGenerator("LOAN_PRODUCT_", 6))
                        .shortName(Utils.uniqueRandomStringGenerator("", 4)).currencyCode("USD").locale("en").digitsAfterDecimal(2)
                        .inMultiplesOf(0).principal(10000.00).minPrincipal(1000.00).maxPrincipal(10000000.00).numberOfRepayments(5)
                        .repaymentEvery(1).repaymentFrequencyType(2L).interestRatePerPeriod(2.0).interestRateFrequencyType(2)
                        .amortizationType(1).interestType(1).interestCalculationPeriodType(1).inArrearsTolerance(0)
                        .transactionProcessingStrategyCode("mifos-standard-strategy").accountingRule(1).daysInMonthType(1).daysInYearType(1)
                        .isInterestRecalculationEnabled(false)))
                .getResourceId();
        Assertions.assertNotNull(outcome_lp_creaion, "Could not create Loan Product");

        String fundName = Utils.uniqueRandomStringGenerator("Fund_Name", 9);
        Long outcome_fund_creation = ok(() -> FineractFeignClientHelper.getFineractFeignClient().funds()
                .createFund(new FundRequest().name(fundName).externalId(UUID.randomUUID().toString()))).getResourceId();
        Assertions.assertNotNull(outcome_fund_creation, "Could not create Fund");

        String name = PaymentTypeHelper.randomNameGenerator("P_T", 5);
        String description = PaymentTypeHelper.randomNameGenerator("PT_Desc", 15);
        Boolean isCashPayment = true;
        Long position = 1L;
        var paymentTypesResponse = PaymentTypeHelper.createPaymentType(
                new PaymentTypeCreateRequest().name(name).description(description).isCashPayment(isCashPayment).position(position));
        Long outcome_payment_creation = paymentTypesResponse.getResourceId();
        Assertions.assertNotNull(outcome_payment_creation, "Could not create payment type");

        Workbook workbook = getLoanWorkbook("dd MMMM yyyy");

        Sheet officeSheet = workbook.getSheet(TemplatePopulateImportConstants.OFFICE_SHEET_NAME);
        Row firstOfficeRow = officeSheet.getRow(1);
        Assertions.assertNotNull(firstOfficeRow.getCell(1), "No offices found ");

        Sheet clientSheet = workbook.getSheet(TemplatePopulateImportConstants.CLIENT_SHEET_NAME);
        Row firstClientRow = clientSheet.getRow(1);
        Assertions.assertNotNull(firstClientRow.getCell(1), "No clients found ");

        Sheet groupSheet = workbook.getSheet(TemplatePopulateImportConstants.GROUP_SHEET_NAME);
        Row firstGroupRow = groupSheet.getRow(1);
        Assertions.assertNotNull(firstGroupRow.getCell(1), "No groups found ");

        Sheet staffSheet = workbook.getSheet(TemplatePopulateImportConstants.STAFF_SHEET_NAME);
        Row firstStaffRow = staffSheet.getRow(1);
        Assertions.assertNotNull(firstStaffRow.getCell(1), "No staff found ");

        Sheet productSheet = workbook.getSheet(TemplatePopulateImportConstants.PRODUCT_SHEET_NAME);
        Row firstProductRow = productSheet.getRow(1);
        Assertions.assertNotNull(firstProductRow.getCell(1), "No products found ");

        Sheet extrasSheet = workbook.getSheet(TemplatePopulateImportConstants.EXTRAS_SHEET_NAME);
        Row firstExtrasRow = extrasSheet.getRow(1);
        Assertions.assertNotNull(firstExtrasRow.getCell(1), "No Extras found ");
    }

    private Workbook getLoanWorkbook(final String dateFormat) throws IOException {
        Response response = FineractFeignClientHelper.getFineractFeignClient().bulkImportFixed().getLoanTemplate(dateFormat);
        try (InputStream inputStream = response.body().asInputStream()) {
            return new HSSFWorkbook(inputStream);
        }
    }
}
