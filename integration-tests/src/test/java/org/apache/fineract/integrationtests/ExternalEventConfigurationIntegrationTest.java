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

import static org.apache.fineract.client.feign.util.FeignCalls.ok;

import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.ExternalEventConfigurationItemResponse;
import org.apache.fineract.client.models.ExternalEventConfigurationUpdateRequest;
import org.apache.fineract.client.models.ExternalEventConfigurationUpdateResponse;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ExternalEventConfigurationIntegrationTest {

    @Test
    public void getExternalEventConfigurations() {
        final List<ExternalEventConfigurationItemResponse> externalEventConfigurations = ok(
                () -> FineractFeignClientHelper.getFineractFeignClient().externalEventConfiguration().getExternalEventConfigurations())
                .getExternalEventConfiguration();
        Assertions.assertNotNull(externalEventConfigurations);
        Assertions.assertFalse(externalEventConfigurations.isEmpty());
        for (ExternalEventConfigurationItemResponse configuration : externalEventConfigurations) {
            Assertions.assertFalse(configuration.getEnabled(), "Expected " + configuration.getType() + " to be disabled by default");
        }
    }

    @Test
    public void updateExternalEventConfigurations() {
        final ExternalEventConfigurationUpdateResponse response = ok(() -> FineractFeignClientHelper.getFineractFeignClient()
                .externalEventConfiguration().updateExternalEventConfigurations(new ExternalEventConfigurationUpdateRequest()
                        .externalEventConfigurations(Map.of("CentersCreateBusinessEvent", true, "ClientActivateBusinessEvent", true))));
        final Map<?, ?> updatedConfigurations = (Map<?, ?>) response.getChanges().get("externalEventConfigurations");
        Assertions.assertEquals(2, updatedConfigurations.size());
        Assertions.assertTrue(updatedConfigurations.containsKey("CentersCreateBusinessEvent"));
        Assertions.assertTrue(updatedConfigurations.containsKey("ClientActivateBusinessEvent"));
        Assertions.assertEquals(Boolean.TRUE, updatedConfigurations.get("CentersCreateBusinessEvent"));
        Assertions.assertEquals(Boolean.TRUE, updatedConfigurations.get("ClientActivateBusinessEvent"));
    }

    @AfterEach
    public void tearDown() {
        ok(() -> FineractFeignClientHelper.getFineractFeignClient().externalEventConfiguration()
                .updateExternalEventConfigurations(new ExternalEventConfigurationUpdateRequest()
                        .externalEventConfigurations(Map.of("CentersCreateBusinessEvent", false, "ClientActivateBusinessEvent", false))));
    }
}
