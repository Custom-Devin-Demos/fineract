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
package org.apache.fineract.integrationtests.common;

import java.math.BigDecimal;
import org.apache.fineract.client.models.ClientCollateralCreateRequest;
import org.apache.fineract.client.models.ClientCollateralManagementData;
import org.apache.fineract.client.models.ClientCollateralUpdateRequest;
import org.apache.fineract.client.models.ClientCollateralUpdateResponse;
import org.apache.fineract.client.models.CollateralProductCreateRequest;
import org.apache.fineract.client.models.CollateralProductUpdateRequest;
import org.apache.fineract.client.models.CollateralProductUpdateResponse;
import org.apache.fineract.client.services.ClientCollateralManagementApi;
import org.apache.fineract.client.services.CollateralManagementApi;
import org.apache.fineract.client.util.Calls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollateralManagementHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CollateralManagementHelper.class);

    public CollateralManagementHelper() {}

    public static Integer createClientCollateral(final String clientId, final Integer collateralId) {
        LOG.info("---------------------------------CREATING A CLIENT_COLLATERAL---------------------------------------------");
        final ClientCollateralCreateRequest request = new ClientCollateralCreateRequest().collateralId(collateralId.longValue())
                .quantity(BigDecimal.valueOf(100)).locale("en");
        return Calls.ok(clientCollateralApi().addClientCollateral(Long.valueOf(clientId), request)).getResourceId().intValue();
    }

    public static BigDecimal getClientCollateralData(final Integer collateralId, final String clientId) {
        final ClientCollateralManagementData data = Calls
                .ok(clientCollateralApi().getClientCollateralData(Long.valueOf(clientId), collateralId.longValue()));
        return data.getQuantity();
    }

    public static Integer createCollateralProduct() {
        LOG.info("---------------------------------CREATING A COLLATERAL_PRODUCT---------------------------------------------");
        final CollateralProductCreateRequest request = new CollateralProductCreateRequest()
                .name(Utils.randomStringGenerator("COLLATERAL_PRODUCT", 5)).currency("USD").unitType("acre").quality("agriculture")
                .pctToBase(BigDecimal.valueOf(40)).basePrice(BigDecimal.valueOf(100000000)).locale("en");
        return Calls.ok(collateralApi().createCollateral1(request)).getResourceId().intValue();
    }

    public static Integer updateCollateralProduct(final Integer collateralId) {
        LOG.info("---------------------------------UPDATING A COLLATERAL_PRODUCT---------------------------------------------");
        final CollateralProductUpdateRequest request = new CollateralProductUpdateRequest()
                .name(Utils.randomStringGenerator("COLLATERAL_PRODUCT", 5)).currency("USD").unitType("acre").quality("agriculture")
                .pctToBase(BigDecimal.valueOf(30)).basePrice(BigDecimal.valueOf(100000)).locale("en");
        final CollateralProductUpdateResponse response = Calls.ok(collateralApi().updateCollateral1(collateralId.longValue(), request));
        return response.getResourceId().intValue();
    }

    public static ClientCollateralUpdateResponse updateClientCollateral(final Integer collateralId) {
        final Integer clientID = ClientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId().intValue();
        LOG.info("---------------------------------UPDATING A CLIENT COLLATERAL---------------------------------------------");
        final ClientCollateralUpdateRequest request = new ClientCollateralUpdateRequest().quantity(BigDecimal.valueOf(1)).locale("en");
        return Calls.ok(clientCollateralApi().updateClientCollateral(clientID.longValue(), collateralId.longValue(), request));
    }

    private static CollateralManagementApi collateralApi() {
        return FineractClientHelper.getFineractClient().createService(CollateralManagementApi.class);
    }

    private static ClientCollateralManagementApi clientCollateralApi() {
        return FineractClientHelper.getFineractClient().createService(ClientCollateralManagementApi.class);
    }
}
