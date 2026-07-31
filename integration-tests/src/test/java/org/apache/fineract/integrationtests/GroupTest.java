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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.Util;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PutGroupsGroupIdRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.GroupHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTestLifecycleExtension;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Group Test for checking Group: Creation, Activation, Client Association, Updating & Deletion
 */
@ExtendWith(LoanTestLifecycleExtension.class)
public class GroupTest {

    private static final Logger LOG = LoggerFactory.getLogger(GroupTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DATE_FORMAT = "dd MMMM yyyy";

    private final String principal = "10000.00";
    private final String numberOfRepayments = "5";
    private final String interestRatePerPeriod = "18";

    private final RawApi rawApi = FineractFeignClientHelper.getFineractFeignClient().create(RawApi.class);

    interface RawApi {

        @RequestLine("POST v1/groups")
        Response createGroup(JsonNode body);

        @RequestLine("GET v1/groups/{groupId}")
        Response getGroup(@Param("groupId") long groupId);

        @RequestLine("GET v1/groups/{groupId}?associations=clientMembers")
        Response getGroupWithMembers(@Param("groupId") long groupId);

        @RequestLine("POST v1/groups/{groupId}?command={command}")
        Response groupCommand(@Param("groupId") long groupId, @Param("command") String command, JsonNode body);

        @RequestLine("PUT v1/groups/{groupId}")
        Response updateGroup(@Param("groupId") long groupId, JsonNode body);

        @RequestLine("GET v1/clients/{clientId}")
        Response getClient(@Param("clientId") long clientId);

        @RequestLine("POST v1/clients/{clientId}?command={command}")
        Response clientCommand(@Param("clientId") long clientId, @Param("command") String command, JsonNode body);

        @RequestLine("POST v1/loanproducts")
        Response createLoanProduct(JsonNode body);

        @RequestLine("POST v1/loans")
        Response createLoan(JsonNode body);

        @RequestLine("GET v1/loans/{loanId}?associations=all")
        Response getLoan(@Param("loanId") long loanId);

        @RequestLine("POST v1/loans/{loanId}?command={command}")
        Response loanCommand(@Param("loanId") long loanId, @Param("command") String command, JsonNode body);
    }

    @Test
    public void checkGroupFunctions() {
        final Integer clientID = createClient();
        Integer groupID = createGroup();
        verifyGroupCreatedOnServer(groupID);

        groupID = activateGroup(groupID);
        verifyGroupActivatedOnServer(groupID, true);

        groupID = associateClient(groupID, clientID);
        verifyGroupMembers(groupID, clientID);

        groupID = disAssociateClient(groupID, clientID);
        verifyEmptyGroupMembers(groupID);

        final String updatedGroupName = GroupHelper.randomNameGenerator("Group-", 5);
        groupID = updateGroup(updatedGroupName, groupID);
        verifyGroupDetails(groupID, "name", updatedGroupName);

        // NOTE: removed as consistently provides false positive result on
        // cloudbees server.
        // groupID = GroupHelper.createGroup(this.requestSpec,
        // this.responseSpec);
        // GroupHelper.deleteGroup(this.requestSpec, this.responseSpec,
        // groupID.toString());
        // GroupHelper.verifyGroupDeleted(this.requestSpec, this.responseSpec,
        // groupID);
    }

    @Test
    public void assignStaffToGroup() {
        Integer groupID = createGroup();
        verifyGroupCreatedOnServer(groupID);

        final String updateGroupName = Utils.uniqueRandomStringGenerator("Savings Group Help_", 5);
        groupID = activateGroup(groupID);
        Integer updateGroupId = updateGroup(updateGroupName, groupID);

        // create client and add client to group
        final Integer clientID = createClient();
        ClientHelper.verifyClientCreatedOnServer(clientID.longValue());

        groupID = associateClient(groupID, clientID);
        verifyGroupMembers(groupID, clientID);

        // create staff
        Integer createStaffId1 = createStaff();
        LOG.info("--------------creating first staff with id------------- {}", createStaffId1);
        Assertions.assertNotNull(createStaffId1);

        Integer createStaffId2 = createStaff();
        LOG.info("--------------creating second staff with id------------- {}", createStaffId2);
        Assertions.assertNotNull(createStaffId2);

        // assign staff "createStaffId1" to group
        JsonNode assignStaffGroupChanges = assignStaff(groupID, createStaffId1.longValue());
        assertEquals(createStaffId1.intValue(), assignStaffGroupChanges.get("staffId").asInt(),
                "Verify assigned staff id is the same as id sent");

        // assign staff "createStaffId2" to client
        final JsonNode assignStaffToClientChanges = assignStaffToClient(clientID, createStaffId2);
        assertEquals(createStaffId2.intValue(), assignStaffToClientChanges.get("staffId").asInt(),
                "Verify assigned staff id is the same as id sent");

        final Integer loanProductId = this.createLoanProduct();

        final Integer loanId = this.applyForLoanApplication(clientID, loanProductId, this.principal);

        approveLoan("20 September 2014", loanId);
        final JsonNode loanDetails = getLoanDetails(loanId);
        disburseLoanWithNetDisbursalAmount("20 September 2014", loanId, loanDetails.get("netDisbursalAmount"));

        final JsonNode assignStaffAndInheritStaffForClientAccounts = assignStaffInheritStaffForClientAccounts(groupID, createStaffId1);
        final Integer getClientStaffId = getClientsStaffId(clientID);

        // assert if client staff officer has change Note client was assigned
        // staff with createStaffId2
        assertNotEquals(createStaffId2.intValue(), assignStaffAndInheritStaffForClientAccounts.get("staffId").asInt(),
                "Verify if client stuff has changed");
        assertEquals(getClientStaffId.intValue(), assignStaffAndInheritStaffForClientAccounts.get("staffId").asInt(),
                "Verify if client inherited staff assigned above");

        // assert if clients loan officer has changed
        final Integer loanOfficerId = getLoanOfficerId(loanId);
        assertEquals(loanOfficerId.intValue(), assignStaffAndInheritStaffForClientAccounts.get("staffId").asInt(),
                "Verify if client loan inherited staff");

    }

    private Integer createClient() {
        final PostClientsResponse clientResponse = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest());
        return clientResponse.getClientId().intValue();
    }

    private Integer createStaff() {
        return StaffHelper.createStaff(new StaffCreateRequest().officeId(1L).firstname(Utils.uniqueRandomStringGenerator("michael_", 5))
                .lastname(Utils.uniqueRandomStringGenerator("Doe_", 4)).isLoanOfficer(true).locale("en").dateFormat(DATE_FORMAT)
                .joiningDate("20 September 2011")).getResourceId().intValue();
    }

    private Integer createGroup() {
        LOG.info("---------------------------------CREATING A GROUP---------------------------------------------");
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("officeId", "1");
        body.put("name", GroupHelper.randomNameGenerator("Group_Name_", 5));
        body.put("externalId", UUID.randomUUID().toString());
        body.put("dateFormat", DATE_FORMAT);
        body.put("locale", "en");
        body.put("active", "false");
        body.put("submittedOnDate", "04 March 2011");
        return readBody(rawApi.createGroup(body)).get("groupId").asInt();
    }

    private Integer activateGroup(final Integer groupID) {
        LOG.info("---------------------------------Activate A GROUP---------------------------------------------");
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("dateFormat", DATE_FORMAT);
        body.put("locale", "en");
        body.put("activationDate", "04 March 2011");
        return readBody(rawApi.groupCommand(groupID, "activate", body)).get("groupId").asInt();
    }

    private Integer associateClient(final Integer groupID, final Integer clientID) {
        LOG.info("---------------------------------Associate Client To A GROUP---------------------------------------------");
        return readBody(rawApi.groupCommand(groupID, "associateClients", clientMembersBody(clientID))).get("groupId").asInt();
    }

    private Integer disAssociateClient(final Integer groupID, final Integer clientID) {
        LOG.info("---------------------------------Disassociate Client To A GROUP---------------------------------------------");
        return readBody(rawApi.groupCommand(groupID, "disassociateClients", clientMembersBody(clientID))).get("groupId").asInt();
    }

    private static JsonNode clientMembersBody(final Integer clientID) {
        final ObjectNode body = MAPPER.createObjectNode();
        final ArrayNode members = body.putArray("clientMembers");
        members.add(clientID.toString());
        return body;
    }

    private Integer updateGroup(final String name, final Integer groupID) {
        LOG.info("---------------------------------UPDATE GROUP---------------------------------------------");
        final PutGroupsGroupIdRequest request = new PutGroupsGroupIdRequest().name(name);
        return GroupHelper.updateGroup(groupID.longValue(), request).getResourceId().intValue();
    }

    private JsonNode assignStaff(final Integer groupID, final Long staffId) {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("staffId", staffId);
        return readBody(rawApi.groupCommand(groupID, "assignStaff", body)).get("changes");
    }

    private JsonNode assignStaffInheritStaffForClientAccounts(final Integer groupID, final Integer staffId) {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("staffId", staffId.toString());
        body.put("inheritStaffForClientAccounts", "true");
        return readBody(rawApi.groupCommand(groupID, "assignStaff", body)).get("changes");
    }

    private JsonNode assignStaffToClient(final Integer clientID, final Integer staffId) {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("staffId", staffId.toString());
        return readBody(rawApi.clientCommand(clientID, "assignStaff", body)).get("changes");
    }

    private Integer getClientsStaffId(final Integer clientID) {
        return readBody(rawApi.getClient(clientID)).get("staffId").asInt();
    }

    private void verifyGroupCreatedOnServer(final Integer generatedGroupID) {
        LOG.info("------------------------------CHECK GROUP DETAILS------------------------------------\n");
        final Integer responseGroupID = readBody(rawApi.getGroup(generatedGroupID)).get("id").asInt();
        assertEquals(generatedGroupID, responseGroupID, "ERROR IN CREATING THE GROUP");
    }

    private void verifyGroupDetails(final Integer generatedGroupID, final String field, final String expectedValue) {
        LOG.info("------------------------------CHECK GROUP DETAILS------------------------------------\n");
        final String responseValue = readBody(rawApi.getGroup(generatedGroupID)).get(field).asText();
        assertEquals(expectedValue, responseValue, "ERROR IN CREATING THE GROUP");
    }

    private void verifyGroupActivatedOnServer(final Integer generatedGroupID, final boolean generatedGroupStatus) {
        LOG.info("------------------------------CHECK GROUP STATUS------------------------------------\n");
        final Boolean responseGroupStatus = readBody(rawApi.getGroup(generatedGroupID)).get("active").asBoolean();
        assertEquals(generatedGroupStatus, responseGroupStatus, "ERROR IN ACTIVATING THE GROUP");
    }

    private void verifyGroupMembers(final Integer generatedGroupID, final Integer groupMember) {
        LOG.info("------------------------------CHECK GROUP MEMBERS------------------------------------\n");
        final JsonNode clientMembers = readBody(rawApi.getGroupWithMembers(generatedGroupID)).get("clientMembers");
        final List<Integer> memberIds = new ArrayList<>();
        if (clientMembers != null) {
            clientMembers.forEach(member -> memberIds.add(member.get("id").asInt()));
        }
        assertTrue(memberIds.contains(groupMember), "ERROR IN GROUP MEMBER");
    }

    private void verifyEmptyGroupMembers(final Integer generatedGroupID) {
        LOG.info("------------------------------CHECK EMPTY GROUP MEMBER LIST------------------------------------\n");
        final JsonNode clientMembers = readBody(rawApi.getGroupWithMembers(generatedGroupID)).get("clientMembers");
        assertNull(clientMembers, "GROUP MEMBER LIST NOT EMPTY");
    }

    private void approveLoan(final String approvalDate, final Integer loanID) {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("locale", "en");
        body.put("dateFormat", DATE_FORMAT);
        body.put("approvedOnDate", approvalDate);
        readBody(rawApi.loanCommand(loanID, "approve", body));
    }

    private JsonNode getLoanDetails(final Integer loanID) {
        return readBody(rawApi.getLoan(loanID));
    }

    private void disburseLoanWithNetDisbursalAmount(final String date, final Integer loanID, final JsonNode netDisbursalAmount) {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("locale", "en");
        body.put("dateFormat", DATE_FORMAT);
        body.put("actualDisbursementDate", date);
        body.set("netDisbursalAmount", netDisbursalAmount);
        body.put("note", "DISBURSE NOTE");
        readBody(rawApi.loanCommand(loanID, "disburse", body));
    }

    private Integer getLoanOfficerId(final Integer loanID) {
        return readBody(rawApi.getLoan(loanID)).get("loanOfficerId").asInt();
    }

    private Integer createLoanProduct() {
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(this.principal)
                .withNumberOfRepayments(this.numberOfRepayments).withinterestRatePerPeriod(this.interestRatePerPeriod)
                .withInterestRateFrequencyTypeAsYear().build(null);
        return readBody(rawApi.createLoanProduct(readTree(loanProductJSON))).get("resourceId").asInt();
    }

    private Integer applyForLoanApplication(final Integer clientID, final Integer loanProductID, String principal) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        List<HashMap> collaterals = new ArrayList<>();
        final Long collateralId = CollateralManagementHelper.createCollateralProduct();
        Assertions.assertNotNull(collateralId);
        final Long clientCollateralId = CollateralManagementHelper.createClientCollateral(clientID.longValue(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));

        final String loanApplicationJSON = new LoanApplicationTestBuilder() //
                .withPrincipal(principal) //
                .withLoanTermFrequency("4") //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments("4") //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("2") //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsDecliningBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate("20 September 2014") //
                .withSubmittedOnDate("20 September 2014") //
                .withCollaterals(collaterals).build(clientID.toString(), loanProductID.toString(), null);
        return readBody(rawApi.createLoan(readTree(loanApplicationJSON))).get("loanId").asInt();
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

    private static JsonNode readTree(final String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static JsonNode readBody(final Response response) {
        try (Response r = response) {
            assertEquals(200, r.status(), "Unexpected status code");
            return MAPPER.readTree(Util.toString(r.body().asReader(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
