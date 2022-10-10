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

import io.apicurio.registry.ccompat.dto.SchemaInfo;
import io.apicurio.registry.ccompat.dto.SubjectVersion;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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
 * This <a href="https://docs.confluent.io/platform/7.2.1/schema-registry/develop/api.html#schemas">API specification</a> is owned by Confluent.
 *
 * @author Carles Arnal
 */
@Path("/apis/ccompat/v7/schemas")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface SchemasResource {

    // ----- Path: /schemas/ids/{globalId} -----

    /**
     * Get the schema string identified by the input ID.
     *
     * Parameters:
     *
     * @param id (int) – the globally unique identifier of the schema
     * @param subject (string) - add ?subject=<someSubjectName> at the end of this request to look for the subject in all contexts starting with the default context,
     *                           and return the schema with the id from that context.
     *
     * Response JSON Object:
     *
     *     schema (string) – Schema string identified by the ID
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40403 – Schema not found
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend datastore
     */
    @GET
    @Path("/ids/{id}")
    SchemaInfo getSchema(@PathParam("id") int id, @QueryParam("subject") String subject);

    // ----- Path: /schemas/types -----

    /**
     * Get the schema types that are registered with Schema Registry.
     *
     *
     *
     * Response JSON Object:
     *
     *      schema (string) – Schema types currently available on Schema Registry.
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40403 – Schema not found
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend datastore
     */
    @GET
    @Path("types")
    List<String> getRegisteredTypes();

    // ----- PATH: /schemas/ids/{int: id}/versions -----

    /**
     * Get the subject-version pairs identified by the input ID.
     *
     * Parameters:
     *
     * @param id (int) – the globally unique identifier of the schema
     *
     * Response JSON Array of Objects:
     *
     *      subject (string) – Name of the subject
     *      version (int) – Version of the returned schema
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40403 – Schema not found
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend datastore
     */
    @GET
    @Path("/ids/{id}/versions")
    List<SubjectVersion> getSubjectVersions(@PathParam("id") int id);
}
