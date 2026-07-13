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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.apache.fineract.client.feign.services.MixTaxonomyApi;
import org.apache.fineract.client.models.MixTaxonomyData;
import org.apache.fineract.integrationtests.common.FineractFeignClientHelper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XBRLIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(XBRLIntegrationTest.class);

    private final MixTaxonomyApi mixTaxonomyApi = FineractFeignClientHelper.getFineractFeignClient().mixTaxonomy();

    @Test
    public void shouldRetrieveTaxonomyList() {
        final List<MixTaxonomyData> taxonomyList = ok(() -> mixTaxonomyApi.retrieveAllMixTaxonomies());
        verifyTaxonomyList(taxonomyList);
    }

    private void verifyTaxonomyList(final List<MixTaxonomyData> taxonomyList) {
        LOG.info("--------------------VERIFYING TAXONOMY LIST--------------------------");
        assertEquals("AdministrativeExpense", taxonomyList.get(0).getName(), "Checking for the 1st taxonomy");
    }

}
