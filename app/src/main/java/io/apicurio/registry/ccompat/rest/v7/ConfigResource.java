package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.registry.ccompat.dto.CompatibilityLevelDto;
import io.apicurio.registry.ccompat.dto.CompatibilityLevelParamDto;
import io.apicurio.registry.rest.Headers;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.HeaderParam;

import static io.apicurio.registry.ccompat.rest.ContentTypes.*;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/5.5.0/schema-registry/develop/api.html#config">API
 * specification</a> is owned by Confluent. The config resource allows you to inspect the cluster-level
 * configuration values as well as subject overrides.
 */
@Path("/apis/ccompat/v7/config")
@Consumes({ JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST })
@Produces({ JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST })
public interface ConfigResource {

    // ----- Path: /config -----

    /**
     * Get global compatibility level. Response: - compatibility (string) – Global compatibility level. Will
     * be one of BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE, NONE
     * Status Codes: 500 Internal Server Error Error code 50001 – Error in the backend data store
     */
    @GET
    CompatibilityLevelParamDto getGlobalCompatibilityLevel();

    /**
     * Update global compatibility level. Request: - compatibility (string) – New global compatibility level.
     * Must be one of BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE, NONE
     * Status Codes: 422 Unprocessable Entity Error code 42203 – Invalid compatibility level 500 Internal
     * Server Error Error code 50001 – Error in the backend data store
     */
    @PUT
    CompatibilityLevelDto updateGlobalCompatibilityLevel(@NotNull CompatibilityLevelDto request);

    // ----- Path: /config/{subject} -----

    /**
     * Get compatibility level for a subject.
     *
     * @param subject (string) – Name of the subject Request: - compatibility (string) – Compatibility level
     *            for the subject. Will be one of BACKWARD, BACKWARD_TRANSITIVE, FORWARD, FORWARD_TRANSITIVE,
     *            FULL, FULL_TRANSITIVE, NONE Status Codes: 404 Not Found – Subject not found 500 Internal
     *            Server Error – Error code 50001 – Error in the backend data store
     */
    @Path("/{subject}")
    @GET
    CompatibilityLevelParamDto getSubjectCompatibilityLevel(@PathParam("subject") String subject,
            @QueryParam("defaultToGlobal") Boolean defaultToGlobal,
            @HeaderParam(Headers.GROUP_ID) String groupId);

    /**
     * Update compatibility level for the specified subject.
     *
     * @param subject (string) – Name of the subject Request: - compatibility (string) – New compatibility
     *            level for the subject. Must be one of BACKWARD, BACKWARD_TRANSITIVE, FORWARD,
     *            FORWARD_TRANSITIVE, FULL, FULL_TRANSITIVE, NONE Status Codes: 422 Unprocessable Entity –
     *            Error code 42203 – Invalid compatibility level 500 Internal Server Error – Error code 50001
     *            – Error in the backend data store Error code 50003 – Error while forwarding the request to
     *            the primary
     */
    @Path("/{subject}")
    @PUT
    CompatibilityLevelDto updateSubjectCompatibilityLevel(@PathParam("subject") String subject,
            @NotNull CompatibilityLevelDto request, @HeaderParam(Headers.GROUP_ID) String groupId);

    /**
     * Deletes the specified subject-level compatibility level config and reverts to the global default.
     *
     * @param subject (string) – Name of the subject Status Codes: 422 Unprocessable Entity – Error code 42203
     *            – Invalid compatibility level 500 Internal Server Error – Error code 50001 – Error in the
     *            backend data store Error code 50003 – Error while forwarding the request to the primary
     */
    @Path("/{subject}")
    @DELETE
    CompatibilityLevelParamDto deleteSubjectCompatibility(@PathParam("subject") String subject,
            @HeaderParam(Headers.GROUP_ID) String groupId);
}
