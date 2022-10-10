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

import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelParamDto;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
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
 * This <a href="https://docs.confluent.io/5.5.0/schema-registry/develop/api.html#config">API specification</a> is owned by Confluent.
 *
 * The config resource allows you to inspect the cluster-level configuration values as well as subject overrides.
 *
 * @author Carles Arnal
 */
@Path("/apis/ccompat/v7/config")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface ConfigResource {


    // ----- Path: /config -----

    /**
     * Get global compatibility level.
     *
     * Response:
     *     - compatibility (string) – Global compatibility level. Will be one of
     *         BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE, NONE
     *
     * Status Codes:
     *     500 Internal Server Error
     *         Error code 50001 – Error in the backend data store
     */
    @GET
    CompatibilityLevelParamDto getGlobalCompatibilityLevel();


    /**
     * Update global compatibility level.
     *
     * Request:
     *     - compatibility (string) – New global compatibility level. Must be one of
     *         BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE, NONE
     *
     * Status Codes:
     *     422 Unprocessable Entity
     *         Error code 42203 – Invalid compatibility level
     *     500 Internal Server Error
     *         Error code 50001 – Error in the backend data store
     */
    @PUT
    CompatibilityLevelDto updateGlobalCompatibilityLevel(
            @NotNull CompatibilityLevelDto request);


    // ----- Path: /config/{subject} -----


    /**
     * Get compatibility level for a subject.
     *
     * @param subject (string) – Name of the subject
     *
     * Request:
     *     - compatibility (string) – Compatibility level for the subject. Will be one of
     *       BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE, NONE
     *
     * Status Codes:
     *     404 Not Found – Subject not found
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend data store
     */
    @Path("/{subject}")
    @GET
    CompatibilityLevelParamDto getSubjectCompatibilityLevel(@PathParam("subject") String subject);

    /**
     * Update compatibility level for the specified subject.
     *
     * @param subject (string) – Name of the subject
     *
     * Request:
     *     - compatibility (string) – New compatibility level for the subject. Must be one of
     *       BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE, NONE
     *
     * Status Codes:
     *     422 Unprocessable Entity –
     *         Error code 42203 – Invalid compatibility level
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend data store
     *         Error code 50003 – Error while forwarding the request to the primary
     */
    @Path("/{subject}")
    @PUT
    CompatibilityLevelDto updateSubjectCompatibilityLevel(
            @PathParam("subject") String subject,
            @NotNull CompatibilityLevelDto request);

    /**
     * Deletes the specified subject-level compatibility level config and reverts to the global default.
     *
     * @param subject (string) – Name of the subject
     *
     * Status Codes:
     *     422 Unprocessable Entity –
     *         Error code 42203 – Invalid compatibility level
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend data store
     *         Error code 50003 – Error while forwarding the request to the primary
     */
    @Path("/{subject}")
    @DELETE
    CompatibilityLevelParamDto deleteSubjectCompatibility(
            @PathParam("subject") String subject);
}
