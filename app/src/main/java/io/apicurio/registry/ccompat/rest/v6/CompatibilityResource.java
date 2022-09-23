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

import io.apicurio.registry.ccompat.dto.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.dto.SchemaContent;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/5.5.0/schema-registry/develop/api.html#id1">API specification</a> is owned by Confluent.
 *
 * The compatibility resource allows the user to test schemas for compatibility against specific versions of a subject’s schema.
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Path("/apis/ccompat/v6/compatibility")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface CompatibilityResource {

    // ----- Path: /compatibility/subjects/{subject}/versions/{version} -----

    /**
     * Test input schema against a particular version of a subject’s schema for compatibility.
     * Note that the compatibility level applied for the check
     * is the configured compatibility level for the subject (GET /config/(string: subject)).
     * If this subject’s compatibility level was never changed,
     * then the global compatibility level applies (GET /config).
     *
     * @param subject Subject of the schema version against which compatibility is to be tested
     * @param version Version of the subject’s schema against which compatibility is to be tested.
     *                Valid values for versionId are between [1,2^31-1] or the string "latest".
     *                "latest" checks compatibility of the input schema with the last registered schema under the specified subject
     *
     * Status Codes:
     *     404 Not Found
     *         Error code 40401 – Subject not found
     *         Error code 40402 – Version not found
     *     422 Unprocessable Entity
     *         Error code 42201 – Invalid schema
     *         Error code 42202 – Invalid version
     *     500 Internal Server Error
     *         Error code 50001 – Error in the backend data store
     */
    @POST
    @Path("/subjects/{subject}/versions/{version}")
    CompatibilityCheckResponse testCompatibilityBySubjectName(
            @PathParam("subject") String subject,
            @PathParam("version") String version,
            @NotNull SchemaContent request) throws Exception;

}
