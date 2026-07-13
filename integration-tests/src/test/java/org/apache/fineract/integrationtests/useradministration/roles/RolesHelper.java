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
package org.apache.fineract.integrationtests.useradministration.roles;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.feign.FeignException;
import org.apache.fineract.client.feign.util.FeignCalls;
import org.apache.fineract.client.models.CommandProcessingResult;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostRolesResponse;
import org.apache.fineract.client.models.PutPermissionsRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsResponse;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.useradministration.data.PermissionData;

public final class RolesHelper {

    public static final long SUPER_USER_ROLE_ID = 1L; // This is hardcoded into the initial Liquibase migration

    public RolesHelper() {

    }

    private static final String DISABLE_ROLE_COMMAND = "disable";
    private static final String ENABLE_ROLE_COMMAND = "enable";

    private static final Gson GSON = new JSON().getGson();

    public static PostRolesResponse createRole(PostRolesRequest request) {
        return FeignCalls.ok(() -> FineractFeignClientHelper.getFineractFeignClient().roles().createRole(request));
    }

    public static Long createRole() {
        return createRole(new PostRolesRequest().name(Utils.uniqueRandomStringGenerator("Role_Name_", 5))
                .description(Utils.randomStringGenerator("Role_Description_", 10))).getResourceId();
    }

    public static PutRolesRoleIdPermissionsResponse addPermissionsToRole(Long roleId, Map<String, Boolean> permissionMap) {
        PutRolesRoleIdPermissionsRequest request = new PutRolesRoleIdPermissionsRequest();
        permissionMap.forEach(request::putPermissionsItem);
        return FeignCalls.ok(() -> FineractFeignClientHelper.getFineractFeignClient().roles().updateRolePermissions(roleId, request));
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static Integer createRole(final RequestSpecification requestSpec, final ResponseSpecification responseSpec) {
        return Math.toIntExact(createRole());
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static String getTestCreateRoleAsJSON() {
        final HashMap<String, String> map = new HashMap<>();
        map.put("name", Utils.uniqueRandomStringGenerator("Role_Name_", 5));
        map.put("description", Utils.randomStringGenerator("Role_Description_", 10));
        return new Gson().toJson(map);
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static HashMap<String, Object> getRoleDetails(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final Integer roleId) {
        return FeignCalls
                .ok(() -> FineractFeignClientHelper.getFineractFeignClient().create(RolesRawApi.class).retrieveRole(roleId.longValue()));
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static Integer disableRole(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final Integer roleId) {
        return Math.toIntExact(FeignCalls.ok(
                () -> FineractFeignClientHelper.getFineractFeignClient().roles().actionsOnRoles(roleId.longValue(), DISABLE_ROLE_COMMAND))
                .getResourceId());
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static Integer enableRole(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final Integer roleId) {
        return Math.toIntExact(FeignCalls.ok(
                () -> FineractFeignClientHelper.getFineractFeignClient().roles().actionsOnRoles(roleId.longValue(), ENABLE_ROLE_COMMAND))
                .getResourceId());
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static Integer deleteRole(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final Integer roleId) {
        try {
            return Math
                    .toIntExact(FineractFeignClientHelper.getFineractFeignClient().roles().deleteRole(roleId.longValue()).getResourceId());
        } catch (FeignException exception) {
            if (exception.status() == 403) {
                return null;
            }
            throw exception;
        }
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static String addPermissionsToRole(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final Integer roleId, final Map<String, Boolean> permissionMap) {
        return GSON.toJson(addPermissionsToRole(roleId.longValue(), permissionMap));
    }

    // TODO: Rewrite to use fineract-client instead!
    // Example: org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper.disburseLoan(java.lang.Long,
    // org.apache.fineract.client.models.PostLoansLoanIdRequest)
    @Deprecated(forRemoval = true)
    public static List<PermissionData> getPermissions(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            boolean makerCheckerable) {
        String response = GSON.toJson(FeignCalls.ok(() -> FineractFeignClientHelper.getFineractFeignClient().permissions()
                .retrieveAllPermissionsUniversal(Map.of("makerCheckerable", makerCheckerable))));
        final Type listType = new TypeToken<List<PermissionData>>() {}.getType();
        return GSON.fromJson(response, listType);
    }

    public CommandProcessingResult updatePermissions(PutPermissionsRequest request) {
        return FeignCalls.ok(() -> FineractFeignClientHelper.getFineractFeignClient().permissions().updatePermissionsDetails(request));
    }

    private interface RolesRawApi {

        @RequestLine("GET /v1/roles/{roleId}")
        @Headers("Accept: application/json")
        HashMap<String, Object> retrieveRole(@Param("roleId") Long roleId);
    }
}
