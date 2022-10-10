/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaContent;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.List;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/platform/7.2.1/schema-registry/develop/api.html">API specification</a> is owned by Confluent.
 *
 * @author Carles Arnal
 */
@Path("/apis/ccompat/v7/subjects")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface SubjectsResource {

    // ----- Path: /subjects -----

    /**
     * Get a list of registered subjects.
     * @param subjectPrefix (string) Add ?subjectPrefix= (as an empty string) at the end of this request to list subjects in the default context. If this flag is not included, GET /subjects returns all subjects across all contexts.
     * @param deleted (boolean) Add ?deleted=true at the end of this request to list both current and soft-deleted subjects. The default is false. If this flag is not included, only current subjects are listed (not those that have been soft-deleted). Hard and soft delete are explained below in the description of the delete API.
     *
     * Response JSON Array of Objects:
     *
     *
     *     name (string) – Subject
     *
     * Status Codes:
     *
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend datastore
     */
    @GET
    List<String> listSubjects(@QueryParam("subjectPrefix") String subjectPrefix, @QueryParam("deleted") Boolean deleted);

    // ----- Path: /subjects/{subject} -----

    /**
     * Check if a schema has already been registered under the specified subject.
     * If so, this returns the schema string along with its globally unique identifier,
     * its version under this subject and the subject name.
     * Parameters:
     *
     *  @param subject (string) – Subject under which the schema will be registered.
     *  @param normalize (boolean) - Add ?normalize=true at the end of this request to normalize the schema. The default is false.
     *
     * Response JSON Object:
     *
     *
     *     subject (string) – Name of the subject that this schema is registered under
     *     globalId (int) – Globally unique identifier of the schema
     *     version (int) – Version of the returned schema
     *     schema (string) – The schema string
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40401 – Subject not found
     *         Error code 40403 – Schema not found
     *     500 Internal Server Error – Internal server error
     */
    @POST
    @Path("/{subject}")
    Schema findSchemaByContent(
            @PathParam("subject") String subject,
            @NotNull SchemaContent request, @QueryParam("normalize") Boolean normalize) throws Exception;

    /**
     * Deletes the specified subject and its associated compatibility level if registered.
     * It is recommended to use this API only when a topic needs to be recycled or in development environment.
     *
     * Parameters:
     *
     *   @param subject (string) – the name of the subject
     *   @param permanent (boolean) –  Add ?permanent=true at the end of this request to specify a hard delete of the subject, which removes all associated metadata including the schema ID.
     *                                The default is false. If the flag is not included, a soft delete is performed.
     *
     * Response JSON Array of Objects:
     *
     *     version (int) – version of the schema deleted under this subject
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40401 – Subject not found
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend datastore
     */
    @DELETE
    @Path("/{subject}")
    List<Integer> deleteSubject(
            @PathParam("subject") String subject, @QueryParam("permanent") Boolean permanent) throws Exception;
}
