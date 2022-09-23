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

package io.apicurio.registry.ccompat.rest.v6;

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
import java.util.List;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/5.5.0/schema-registry/develop/api.html#subjects">API specification</a> is owned by Confluent.
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Path("/apis/ccompat/v6/subjects")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface SubjectsResource {

    // ----- Path: /subjects -----


    /**
     * Get a list of registered subjects.
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
    // TODO Possibly costly operation
    @GET
    List<String> listSubjects();


    // ----- Path: /subjects/{subject} -----

    /**
     * Check if a schema has already been registered under the specified subject.
     * If so, this returns the schema string along with its globally unique identifier,
     * its version under this subject and the subject name.
     * Parameters:
     *
     *     subject (string) – Subject under which the schema will be registered
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
            @NotNull SchemaContent request) throws Exception;



    /**
     * Deletes the specified subject and its associated compatibility level if registered.
     * It is recommended to use this API only when a topic needs to be recycled or in development environment.
     *
     * Parameters:
     *
     *     subject (string) – the name of the subject
     *
     * Response JSON Array of Objects:
     *
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
            @PathParam("subject") String subject) throws Exception;
}
