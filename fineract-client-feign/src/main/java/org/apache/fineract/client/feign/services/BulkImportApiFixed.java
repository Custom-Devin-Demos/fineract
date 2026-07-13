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
package org.apache.fineract.client.feign.services;

import feign.Param;
import feign.RequestLine;
import feign.Response;

/**
 * This interface was manually written to work around
 * <a href="https://issues.apache.org/jira/browse/FINERACT-1227">FINERACT-1227</a>, the same binary-response generation
 * problem that {@link DocumentsApiFixed} addresses. The generated bulk-import {@code downloadtemplate} operations
 * return {@code void} (their {@code application/vnd.ms-excel} workbook body is not modeled), so the raw
 * {@link Response} cannot be obtained through the generated services. These methods expose the raw {@link Response} so
 * the binary workbook bytes can be read. If the OpenAPI / Swagger generation is fixed to model these binary responses
 * correctly, this could be removed again.
 */
public interface BulkImportApiFixed {

    /**
     * Download the office bulk-import workbook template.
     *
     * @param dateFormat
     *            the date format used to render the template (required)
     * @return the raw {@link Response} whose body is the {@code application/vnd.ms-excel} workbook
     */
    @RequestLine("GET /v1/offices/downloadtemplate?dateFormat={dateFormat}")
    @feign.Headers("Accept: application/vnd.ms-excel")
    Response getOfficeTemplate(@Param("dateFormat") String dateFormat);

    /**
     * Download the client bulk-import workbook template.
     *
     * @param legalFormType
     *            the client legal form type (required)
     * @param dateFormat
     *            the date format used to render the template (required)
     * @return the raw {@link Response} whose body is the {@code application/vnd.ms-excel} workbook
     */
    @RequestLine("GET /v1/clients/downloadtemplate?legalFormType={legalFormType}&dateFormat={dateFormat}")
    @feign.Headers("Accept: application/vnd.ms-excel")
    Response getClientTemplate(@Param("legalFormType") String legalFormType, @Param("dateFormat") String dateFormat);

    /**
     * Download the savings-account bulk-import workbook template.
     *
     * @param dateFormat
     *            the date format used to render the template (required)
     * @return the raw {@link Response} whose body is the {@code application/vnd.ms-excel} workbook
     */
    @RequestLine("GET /v1/savingsaccounts/downloadtemplate?dateFormat={dateFormat}")
    @feign.Headers("Accept: application/vnd.ms-excel")
    Response getSavingsTemplate(@Param("dateFormat") String dateFormat);

    /**
     * Download the loan bulk-import workbook template.
     *
     * @param dateFormat
     *            the date format used to render the template (required)
     * @return the raw {@link Response} whose body is the {@code application/vnd.ms-excel} workbook
     */
    @RequestLine("GET /v1/loans/downloadtemplate?dateFormat={dateFormat}")
    @feign.Headers("Accept: application/vnd.ms-excel")
    Response getLoanTemplate(@Param("dateFormat") String dateFormat);
}
