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

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import org.apache.fineract.client.models.PutDataTablesResponse;

/**
 * Hand-written companion to the generated {@link DataTablesApi}, inspired by {@link DocumentsApiFixed}.
 *
 * <p>
 * The generated data table operations model their request bodies with strongly typed classes that cannot express every
 * property the server accepts. Most notably {@code PutDataTablesRequestChangeColumns} has no {@code length} property,
 * although the server honours it for a schema modification. This interface exposes the same endpoints with a raw
 * request body so callers can send the exact payload without losing information.
 */
public interface DataTablesApiFixed {

    /**
     * Create Data Table (raw body).
     *
     * @param body
     *            the raw request body (e.g. a Jackson {@code JsonNode})
     * @return the raw response body
     */
    @RequestLine("POST /v1/datatables")
    @Headers({ "Content-Type: application/json", "Accept: application/json" })
    Object createDatatable(Object body);

    /**
     * Update Data Table (raw body). Modifies fields of a data table, sending the request body exactly as provided.
     *
     * @param datatableName
     *            datatableName (required)
     * @param body
     *            the raw request body (e.g. a Jackson {@code JsonNode})
     * @return PutDataTablesResponse
     */
    @RequestLine("PUT /v1/datatables/{datatableName}")
    @Headers({ "Content-Type: application/json", "Accept: application/json" })
    PutDataTablesResponse updateDatatable(@Param("datatableName") String datatableName, Object body);

    /**
     * Update One to Many Data Table Entry (raw body and raw response).
     *
     * @param datatable
     *            datatable (required)
     * @param apptableId
     *            apptableId (required)
     * @param datatableId
     *            datatableId (required)
     * @param body
     *            the raw request body (e.g. a Jackson {@code JsonNode})
     * @return the raw response body
     */
    @RequestLine("PUT /v1/datatables/{datatable}/{apptableId}/{datatableId}?genericResultSet=false")
    @Headers({ "Content-Type: application/json", "Accept: application/json" })
    Object updateDatatableEntryOneToMany(@Param("datatable") String datatable, @Param("apptableId") Long apptableId,
            @Param("datatableId") Long datatableId, Object body);

    /**
     * Read Data Table Entries (raw response) controlling the {@code genericResultSet} flag.
     *
     * @param datatable
     *            datatable (required)
     * @param apptableId
     *            apptableId (required)
     * @param genericResultSet
     *            whether to return a generic result set (required)
     * @return the raw response body
     */
    @RequestLine("GET /v1/datatables/{datatable}/{apptableId}?genericResultSet={genericResultSet}")
    @Headers({ "Accept: application/json" })
    Object getDatatableEntries(@Param("datatable") String datatable, @Param("apptableId") Long apptableId,
            @Param("genericResultSet") Boolean genericResultSet);

    /**
     * Delete Data Table Entries (raw response).
     *
     * @param datatable
     *            datatable (required)
     * @param apptableId
     *            apptableId (required)
     * @return the raw response body
     */
    @RequestLine("DELETE /v1/datatables/{datatable}/{apptableId}?genericResultSet=true")
    @Headers({ "Accept: application/json" })
    Object deleteDatatableEntries(@Param("datatable") String datatable, @Param("apptableId") Long apptableId);
}
