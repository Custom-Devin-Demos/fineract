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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.client.feign.FineractFeignClient;
import org.apache.fineract.client.feign.util.CallFailedRuntimeException;
import org.apache.fineract.client.models.CenterData;
import org.apache.fineract.client.models.GetCodeValuesDataResponse;
import org.apache.fineract.client.models.GetCodesResponse;
import org.apache.fineract.client.models.GroupGeneralData;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostGroupsGroupIdResponse;
import org.apache.fineract.client.models.PostGroupsResponse;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.client.models.PostSavingsAccountsResponse;
import org.apache.fineract.client.models.PostSavingsProductsResponse;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.common.CenterHelper;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.system.AccountNumberPreferencesHelper;
import org.apache.fineract.integrationtests.common.system.CodeHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(LoanTestLifecycleExtension.class)
public class AccountNumberPreferencesTest {

    private static final Logger LOG = LoggerFactory.getLogger(AccountNumberPreferencesTest.class);
    private static final Gson GSON = new JSON().getGson();

    private final FineractFeignClient fineractClient = FineractFeignClientHelper.getFineractFeignClient();

    private Long clientId;
    private Long loanProductId;
    private Long loanId;
    private Long savingsProductId;
    private String savingsProductShortName;
    private Long savingsId;
    private final String loanPrincipalAmount = "100000.00";
    private final String numberOfRepayments = "12";
    private final String interestRatePerPeriod = "18";
    private final String dateString = "04 September 2014";
    private final String minBalanceForInterestCalculation = null;
    private final String minRequiredBalance = null;
    private final String enforceMinRequiredBalance = "false";
    private AccountNumberPreferencesHelper accountNumberPreferencesHelper;
    private Long clientAccountNumberPreferenceId;
    private Long loanAccountNumberPreferenceId;
    private Long savingsAccountNumberPreferenceId;
    private Long groupsAccountNumberPreferenceId;
    private Long centerAccountNumberPreferenceId;
    private static final String MINIMUM_OPENING_BALANCE = "1000.0";
    private static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    private Boolean isAccountPreferenceSetUp = false;
    private Long clientTypeCodeId;
    private String clientCodeValueName;
    private Long clientCodeValueId;
    private final String clientTypeName = "CLIENT_TYPE";
    private final String officeName = "OFFICE_NAME";
    private final String loanShortName = "LOAN_PRODUCT_SHORT_NAME";
    private final String savingsShortName = "SAVINGS_PRODUCT_SHORT_NAME";
    private Long groupID;
    private Long centerId;
    private String groupAccountNo;

    @BeforeEach
    public void setup() {
        this.accountNumberPreferencesHelper = new AccountNumberPreferencesHelper();
    }

    @Test
    public void testAccountNumberPreferences() {

        /* Create Loan and Savings Product */
        this.createLoanAndSavingsProduct();

        /* Ensure no account number preferences are present in the system */
        this.deleteAllAccountNumberPreferences();

        /*
         * Validate the default account number generation rules for clients, loans and savings accounts.
         */
        this.validateDefaultAccountNumberGeneration();

        /* Create and Validate account number preferences */
        this.createAccountNumberPreference();

        /*
         * Validate account number preference rules apply to Clients,Loans and Saving Accounts
         */
        this.validateAccountNumberGenerationWithPreferences();

        /* Validate account number preferences Updation */
        this.updateAccountNumberPreference();

        /*
         * Validate account number preference rules apply to Clients,Loans and Saving Accounts after Updation
         */
        this.validateAccountNumberGenerationWithPreferences();

        /* Delete all account number preferences */
        this.deleteAllAccountNumberPreferences();

    }

    private void createLoanAndSavingsProduct() {
        this.createLoanProduct();
        this.createSavingsProduct();
    }

    private void deleteAllAccountNumberPreferences() {
        /* Deletion of valid account preference ID */
        this.accountNumberPreferencesHelper.getAllAccountNumberPreferences().forEach(preference -> {
            Long resourceId = this.accountNumberPreferencesHelper.deleteAccountNumberPreference(preference.getId());
            LOG.info("Successfully deleted account number preference (ID: {} )", resourceId);
        });
        /* Deletion of invalid account preference ID should fail */
        LOG.info(
                "---------------------------------DELETING ACCOUNT NUMBER PREFERENCE WITH INVALID ID------------------------------------------");

        CallFailedRuntimeException deletionError = this.accountNumberPreferencesHelper.deleteAccountNumberPreferenceExpectingFailure(10L);
        Assertions.assertEquals(404, deletionError.getStatus());
        Assertions.assertEquals("error.msg.resource.not.found", deletionError.getUserMessageGlobalisationCode());
    }

    private void validateDefaultAccountNumberGeneration() {
        this.createAndValidateClientEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateLoanEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateSavingsEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateGroup(this.isAccountPreferenceSetUp);
        this.createAndValidateCenter(this.isAccountPreferenceSetUp);
    }

    private void validateAccountNumberGenerationWithPreferences() {
        this.isAccountPreferenceSetUp = true;
        this.createAndValidateClientEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateLoanEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateSavingsEntity(this.isAccountPreferenceSetUp);
        this.createAndValidateGroup(this.isAccountPreferenceSetUp);
        this.createAndValidateCenter(this.isAccountPreferenceSetUp);
    }

    private void createAccountNumberPreference() {
        this.clientAccountNumberPreferenceId = this.accountNumberPreferencesHelper.createClientAccountNumberPreference();
        LOG.info("Successfully created account number preferences for Client (ID: {})", this.clientAccountNumberPreferenceId);

        this.loanAccountNumberPreferenceId = this.accountNumberPreferencesHelper.createLoanAccountNumberPreference();
        LOG.info("Successfully created account number preferences for Loan (ID: {} )", this.loanAccountNumberPreferenceId);

        this.savingsAccountNumberPreferenceId = this.accountNumberPreferencesHelper.createSavingsAccountNumberPreference();
        LOG.info("Successfully created account number preferences for Savings (ID: {})", this.savingsAccountNumberPreferenceId);

        this.groupsAccountNumberPreferenceId = this.accountNumberPreferencesHelper.createGroupsAccountNumberPreference();
        LOG.info("Successfully created account number preferences for Groups (ID: {})", this.groupsAccountNumberPreferenceId);

        this.centerAccountNumberPreferenceId = this.accountNumberPreferencesHelper.createCenterAccountNumberPreference();
        LOG.info("Successfully created account number preferences for Center (ID: {})", this.centerAccountNumberPreferenceId);

        this.accountNumberPreferencesHelper.verifyCreationOfAccountNumberPreferences(this.clientAccountNumberPreferenceId,
                this.loanAccountNumberPreferenceId, this.savingsAccountNumberPreferenceId, this.groupsAccountNumberPreferenceId,
                this.centerAccountNumberPreferenceId);

        this.createAccountNumberPreferenceInvalidData(1000L, 1001L);
        this.createAccountNumberPreferenceDuplicateData(1L, 101L);

    }

    private void createAccountNumberPreferenceDuplicateData(final Long accountType, final Long prefixType) {
        /* Creating account Preference with duplicate data should fail */
        LOG.info(
                "---------------------------------CREATING ACCOUNT NUMBER PREFERENCE WITH DUPLICATE DATA------------------------------------------");

        CallFailedRuntimeException creationError = this.accountNumberPreferencesHelper
                .createAccountNumberPreferenceExpectingFailure(accountType, prefixType);

        Assertions.assertEquals(403, creationError.getStatus());
        Assertions.assertEquals("error.msg.account.number.format.duplicate.account.type", creationError.getUserMessageGlobalisationCode());

    }

    private void createAccountNumberPreferenceInvalidData(final Long accountType, final Long prefixType) {

        /* Creating account Preference with invalid data should fail */
        LOG.info(
                "---------------------------------CREATING ACCOUNT NUMBER PREFERENCE WITH INVALID DATA------------------------------------------");

        CallFailedRuntimeException creationError = this.accountNumberPreferencesHelper
                .createAccountNumberPreferenceExpectingFailure(accountType, prefixType);

        Assertions.assertEquals(400, creationError.getStatus());
        final String errorCode = creationError.getUserMessageGlobalisationCode();
        if ("validation.msg.accountNumberFormat.accountType.is.not.within.expected.range".equals(errorCode)) {
            Assertions.assertEquals("validation.msg.accountNumberFormat.accountType.is.not.within.expected.range", errorCode);
        } else if ("validation.msg.accountNumberFormat.prefixType.is.not.one.of.expected.enumerations".equals(errorCode)) {
            Assertions.assertEquals("validation.msg.accountNumberFormat.prefixType.is.not.one.of.expected.enumerations", errorCode);
        }
    }

    private void updateAccountNumberPreference() {
        Long updatedResourceId = this.accountNumberPreferencesHelper.updateAccountNumberPreference(this.clientAccountNumberPreferenceId,
                101L);

        LOG.info("--------------------------UPDATION SUCCESSFUL FOR ACCOUNT NUMBER PREFERENCE ID {}", updatedResourceId);

        this.accountNumberPreferencesHelper.verifyUpdationOfAccountNumberPreferences(updatedResourceId);

        /* Update invalid account preference id should fail */
        LOG.info(
                "---------------------------------UPDATING ACCOUNT NUMBER PREFERENCE WITH INVALID DATA------------------------------------------");

        /* Invalid Account Type */
        CallFailedRuntimeException updationError = this.accountNumberPreferencesHelper.updateAccountNumberPreferenceExpectingFailure(9999L,
                101L);
        Assertions.assertEquals(404, updationError.getStatus());
        Assertions.assertEquals("error.msg.resource.not.found", updationError.getUserMessageGlobalisationCode());

        /* Invalid Prefix Type */
        CallFailedRuntimeException updationError1 = this.accountNumberPreferencesHelper
                .updateAccountNumberPreferenceExpectingFailure(this.clientAccountNumberPreferenceId, 103L);

        Assertions.assertEquals(400, updationError1.getStatus());
        Assertions.assertEquals("validation.msg.validation.errors.exist", updationError1.getUserMessageGlobalisationCode());

    }

    private void createAndValidateClientEntity(Boolean isAccountPreferenceSetUp) {
        if (isAccountPreferenceSetUp) {
            this.createAndValidateClientBasedOnAccountPreference();
        } else {
            this.createAndValidateClientWithoutAccountPreference();
        }
    }

    private void createAndValidateGroup(Boolean isAccountPreferenceSetUp) {
        this.groupID = raw().createGroup(inactiveGroupRequest()).getGroupId();
        Assertions.assertEquals(this.groupID, GroupHelper.getGroup(this.groupID).getId(), "ERROR IN CREATING THE GROUP");

        raw().activateGroup(this.groupID, activateGroupRequest());
        final GroupGeneralData group = raw().retrieveGroup(this.groupID);
        Assertions.assertEquals(Boolean.TRUE, group.getActive(), "ERROR IN ACTIVATING THE GROUP");

        this.groupAccountNo = group.getAccountNo();

        if (isAccountPreferenceSetUp) {
            String groupsPrefixName = this.accountNumberPreferencesHelper
                    .getAccountNumberPreferencePrefixValue(this.groupsAccountNumberPreferenceId);

            if (groupsPrefixName.equals(this.officeName)) {
                this.validateAccountNumberLengthAndStartsWithPrefix(this.groupAccountNo, group.getOfficeName());
            }
        } else {
            validateAccountNumberLengthAndStartsWithPrefix(this.groupAccountNo, null);
        }
    }

    private void createAndValidateCenter(Boolean isAccountPreferenceSetUp) {
        Long officeId = new OfficeHelper().createOffice(LocalDate.of(2007, 7, 1)).getResourceId();

        String name = "CenterCreation" + new Timestamp(new java.util.Date().getTime());
        this.centerId = CenterHelper.createCenter(name, officeId);
        CenterData center = CenterHelper.retrieveCenter(this.centerId);
        Assertions.assertNotNull(center);
        Assertions.assertTrue(center.getName().equals(name));

        if (isAccountPreferenceSetUp) {
            String centerPrefixName = this.accountNumberPreferencesHelper
                    .getAccountNumberPreferencePrefixValue(this.centerAccountNumberPreferenceId);

            if (centerPrefixName.equals(this.officeName)) {
                this.validateAccountNumberLengthAndStartsWithPrefix(center.getAccountNo(), center.getOfficeName());
            }
        } else {
            validateAccountNumberLengthAndStartsWithPrefix(center.getAccountNo(), null);
        }
    }

    private void createAndValidateClientWithoutAccountPreference() {
        this.clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
        Assertions.assertNotNull(this.clientId);
        String clientAccountNo = ClientHelper.getClient(this.clientId).getAccountNo();
        validateAccountNumberLengthAndStartsWithPrefix(clientAccountNo, null);
    }

    private void createAndValidateClientBasedOnAccountPreference() {
        final String codeName = "ClientType";
        String clientAccountNo = null;
        String clientPrefixName = this.accountNumberPreferencesHelper
                .getAccountNumberPreferencePrefixValue(this.clientAccountNumberPreferenceId);
        if (clientPrefixName.equals(this.clientTypeName)) {

            /* Retrieve Code id for the Code "ClientType" */
            GetCodesResponse code = new CodeHelper().retrieveCodes().stream().filter(c -> codeName.equals(c.getName())).findFirst()
                    .orElseThrow();
            this.clientTypeCodeId = code.getId();

            /* Retrieve/Create Code Values for the Code "ClientType" */
            GetCodeValuesDataResponse codeValue = new CodeHelper().retrieveOrCreateCodeValue(this.clientTypeCodeId);

            this.clientCodeValueName = codeValue.getName();
            this.clientCodeValueId = codeValue.getId();

            /* Create Client with Client Type */
            this.clientId = createClientWithClientType(this.clientCodeValueId);
            ClientHelper.verifyClientCreatedOnServer(this.clientId);

            clientAccountNo = ClientHelper.getClient(this.clientId).getAccountNo();
            this.validateAccountNumberLengthAndStartsWithPrefix(clientAccountNo, this.clientCodeValueName);

        } else if (clientPrefixName.equals(this.officeName)) {
            this.clientId = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();
            ClientHelper.verifyClientCreatedOnServer(this.clientId);
            clientAccountNo = ClientHelper.getClient(this.clientId).getAccountNo();
            String officeName = ClientHelper.getClient(this.clientId).getOfficeName();
            this.validateAccountNumberLengthAndStartsWithPrefix(clientAccountNo, officeName);
        }
    }

    private void validateAccountNumberLengthAndStartsWithPrefix(final String accountNumber, String prefix) {
        if (prefix != null) {
            prefix = prefix.substring(0, Math.min(prefix.length(), 10));
            Assertions.assertEquals(accountNumber.length(), prefix.length() + 9);
            Assertions.assertTrue(accountNumber.startsWith(prefix));
        } else {
            Assertions.assertEquals(9, accountNumber.length());
        }
    }

    private void createLoanProduct() {
        LOG.info("---------------------------------CREATING LOAN PRODUCT------------------------------------------");

        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(loanPrincipalAmount)
                .withNumberOfRepayments(numberOfRepayments).withinterestRatePerPeriod(interestRatePerPeriod)
                .withInterestRateFrequencyTypeAsYear().build(null);

        this.loanProductId = raw().createLoanProduct(toMap(loanProductJSON)).getResourceId();
        LOG.info("Successfully created loan product  (ID: {} )", this.loanProductId);
    }

    private void addCollaterals(List<HashMap> collaterals, Long collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private HashMap<String, String> collaterals(Long collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<String, String>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

    private void createAndValidateLoanEntity(Boolean isAccountPreferenceSetUp) {
        LOG.info("---------------------------------NEW LOAN APPLICATION------------------------------------------");
        List<HashMap> collaterals = new ArrayList<>();
        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(this.clientId, collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(loanPrincipalAmount)
                .withLoanTermFrequency(numberOfRepayments).withLoanTermFrequencyAsMonths().withNumberOfRepayments(numberOfRepayments)
                .withRepaymentEveryAfter("1").withRepaymentFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments()
                .withInterestCalculationPeriodTypeAsDays().withInterestRatePerPeriod(interestRatePerPeriod).withLoanTermFrequencyAsMonths()
                .withSubmittedOnDate(dateString).withExpectedDisbursementDate(dateString).withPrincipalGrace("2").withInterestGrace("2")
                .withCollaterals(collaterals).build(this.clientId.toString(), this.loanProductId.toString(), null);

        LOG.info("Loan Application :{}", loanApplicationJSON);

        this.loanId = raw().submitLoanApplication(toMap(loanApplicationJSON)).getLoanId();
        String loanAccountNo = this.fineractClient.loans().retrieveLoan(this.loanId, null, null, null, null).getAccountNo();

        if (isAccountPreferenceSetUp) {
            String loanPrefixName = this.accountNumberPreferencesHelper
                    .getAccountNumberPreferencePrefixValue(this.loanAccountNumberPreferenceId);
            if (loanPrefixName.equals(this.officeName)) {
                String loanOfficeName = ClientHelper.getClient(this.clientId).getOfficeName();
                this.validateAccountNumberLengthAndStartsWithPrefix(loanAccountNo, loanOfficeName);
            } else if (loanPrefixName.equals(this.loanShortName)) {
                String loanShortName = this.fineractClient.loanProducts().retrieveOneLoanProduct(this.loanProductId).getShortName();
                this.validateAccountNumberLengthAndStartsWithPrefix(loanAccountNo, loanShortName);
            }
            LOG.info("SUCCESSFULLY CREATED LOAN APPLICATION BASED ON ACCOUNT PREFERENCES (ID: {} )", this.loanId);
        } else {
            this.validateAccountNumberLengthAndStartsWithPrefix(loanAccountNo, null);
            LOG.info("SUCCESSFULLY CREATED LOAN APPLICATION (ID: {} )", loanId);
        }
    }

    private void createSavingsProduct() {
        LOG.info("------------------------------CREATING NEW SAVINGS PRODUCT ---------------------------------------");

        this.savingsProductId = raw().createSavingsProduct(savingsProductRequest()).getResourceId();
        LOG.info("Sucessfully created savings product (ID: {} )", this.savingsProductId);

    }

    private void createAndValidateSavingsEntity(Boolean isAccountPreferenceSetUp) {
        final String savingsApplicationJSON = new SavingsApplicationTestBuilder().withExternalId(null).withWithdrawalFeeForTransfers(false)
                .withSubmittedOnDate(SavingsAccountHelper.CREATED_DATE)
                .build(this.clientId.toString(), this.savingsProductId.toString(), ACCOUNT_TYPE_INDIVIDUAL);

        this.savingsId = raw().submitSavingsApplication(toMap(savingsApplicationJSON)).getSavingsId();

        String savingsAccountNo = this.fineractClient.savingsAccount().retrieveSavingsAccount(this.savingsId, null, null, "all")
                .getAccountNo();

        if (isAccountPreferenceSetUp) {
            String savingsPrefixName = this.accountNumberPreferencesHelper
                    .getAccountNumberPreferencePrefixValue(this.savingsAccountNumberPreferenceId);

            if (savingsPrefixName.equals(this.officeName)) {
                String savingsOfficeName = ClientHelper.getClient(this.clientId).getOfficeName();
                this.validateAccountNumberLengthAndStartsWithPrefix(savingsAccountNo, savingsOfficeName);
            } else if (savingsPrefixName.equals(this.savingsShortName)) {
                this.validateAccountNumberLengthAndStartsWithPrefix(savingsAccountNo, this.savingsProductShortName);
            }
            LOG.info("SUCCESSFULLY CREATED SAVINGS APPLICATION BASED ON ACCOUNT PREFERENCES (ID:  {} )", this.loanId);
        } else {
            this.validateAccountNumberLengthAndStartsWithPrefix(savingsAccountNo, null);
            LOG.info("SUCCESSFULLY CREATED SAVINGS APPLICATION (ID:{} )", this.savingsId);
        }
    }

    private Long createClientWithClientType(final Long clientTypeId) {
        final Map<String, Object> request = toMap(GSON.toJson(ClientHelper.defaultClientCreationRequest()));
        request.put("clientTypeId", clientTypeId);
        return raw().createClient(request).getClientId();
    }

    private Map<String, Object> inactiveGroupRequest() {
        final Map<String, Object> map = new HashMap<>();
        map.put("officeId", "1");
        map.put("name", Utils.uniqueRandomStringGenerator("Group_Name_", 5));
        map.put("externalId", UUID.randomUUID().toString());
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("locale", "en");
        map.put("active", "false");
        map.put("submittedOnDate", "04 March 2011");
        return map;
    }

    private Map<String, Object> activateGroupRequest() {
        final Map<String, Object> map = new HashMap<>();
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("locale", "en");
        map.put("activationDate", "04 March 2011");
        return map;
    }

    private Map<String, Object> savingsProductRequest() {
        this.savingsProductShortName = Utils.uniqueRandomStringGenerator("", 4);
        final Map<String, Object> map = new HashMap<>();
        map.put("name", Utils.uniqueRandomStringGenerator("SAVINGS_PRODUCT_", 6));
        map.put("shortName", this.savingsProductShortName);
        map.put("description", Utils.randomStringGenerator("", 20));
        map.put("currencyCode", "USD");
        map.put("interestCalculationDaysInYearType", "365");
        map.put("locale", "en_GB");
        map.put("digitsAfterDecimal", "4");
        map.put("inMultiplesOf", "0");
        map.put("interestCalculationType", "1");
        map.put("nominalAnnualInterestRate", "10.0");
        map.put("interestCompoundingPeriodType", "1");
        map.put("interestPostingPeriodType", "4");
        map.put("accountingRule", "1");
        map.put("minRequiredOpeningBalance", MINIMUM_OPENING_BALANCE);
        map.put("lockinPeriodFrequency", "0");
        map.put("lockinPeriodFrequencyType", "0");
        map.put("withdrawalFeeForTransfers", "true");
        map.put("allowOverdraft", "false");
        map.put("enforceMinRequiredBalance", enforceMinRequiredBalance);
        map.put("lienAllowed", "false");
        map.put("withHoldTax", "false");
        if (minBalanceForInterestCalculation != null) {
            map.put("minBalanceForInterestCalculation", minBalanceForInterestCalculation);
        }
        if (minRequiredBalance != null) {
            map.put("minRequiredBalance", minRequiredBalance);
        }
        return map;
    }

    private AccountNumberPreferencesApi raw() {
        return this.fineractClient.create(AccountNumberPreferencesApi.class);
    }

    private static Map<String, Object> toMap(final String json) {
        return GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    /**
     * Endpoints whose generated request models are too thin to preserve the exact payloads that this test relies on, so
     * their raw (but typed-response) representations are used directly through fineract-client-feign.
     */
    interface AccountNumberPreferencesApi {

        @RequestLine("POST /v1/loanproducts")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostLoanProductsResponse createLoanProduct(Map<String, Object> request);

        @RequestLine("POST /v1/loans")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostLoansResponse submitLoanApplication(Map<String, Object> request);

        @RequestLine("POST /v1/savingsproducts")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostSavingsProductsResponse createSavingsProduct(Map<String, Object> request);

        @RequestLine("POST /v1/savingsaccounts")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostSavingsAccountsResponse submitSavingsApplication(Map<String, Object> request);

        @RequestLine("POST /v1/clients")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostClientsResponse createClient(Map<String, Object> request);

        @RequestLine("POST /v1/groups")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostGroupsResponse createGroup(Map<String, Object> request);

        @RequestLine("POST /v1/groups/{groupId}?command=activate")
        @Headers({ "Content-Type: application/json", "Accept: application/json" })
        PostGroupsGroupIdResponse activateGroup(@Param("groupId") Long groupId, Map<String, Object> request);

        @RequestLine("GET /v1/groups/{groupId}")
        @Headers("Accept: application/json")
        GroupGeneralData retrieveGroup(@Param("groupId") Long groupId);
    }
}
