package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.registry.ccompat.dto.ModeDto;
import io.apicurio.registry.rest.Headers;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href= "https://docs.confluent.io/platform/7.2.1/schema-registry/develop/api.html#mode">API
 * specification</a> is owned by Confluent.
 * <p/>
 * The mode resource allows you to get and set the mode of the Schema Registry. When in READWRITE mode, the
 * registry allows read and write operations. When in READONLY mode, the registry allows only read operations.
 * When in IMPORT mode, the registry allows importing schemas with pre-existing IDs.
 */
@Path("/apis/ccompat/v7/mode")
@Consumes({ JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST })
@Produces({ JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST })
public interface ModeResource {

    // ----- Path: /mode -----

    /**
     * Get the current global mode for the Schema Registry.
     *
     * @return The current mode (READWRITE, READONLY, or IMPORT)
     */
    @GET
    ModeDto getGlobalMode();

    /**
     * Update the global mode for the Schema Registry.
     *
     * @param request The new mode
     * @param force When true, force the mode change even if there are existing schemas (required for IMPORT
     *            mode)
     * @return The new mode
     */
    @PUT
    ModeDto updateGlobalMode(@NotNull ModeDto request, @QueryParam("force") Boolean force);

    /**
     * Delete the global mode setting and revert to READWRITE.
     *
     * @return The previous mode
     */
    @DELETE
    ModeDto deleteGlobalMode();

    // ----- Path: /mode/{subject} -----

    /**
     * Get the mode for a specific subject.
     *
     * @param subject The name of the subject
     * @param defaultToGlobal If true, return the global mode if the subject doesn't have a specific mode
     * @param groupId Optional group ID header for multi-tenancy
     * @return The mode for the subject
     */
    @GET
    @Path("/{subject}")
    ModeDto getSubjectMode(@PathParam("subject") String subject,
            @QueryParam("defaultToGlobal") Boolean defaultToGlobal,
            @HeaderParam(Headers.GROUP_ID) String groupId);

    /**
     * Update the mode for a specific subject.
     *
     * @param subject The name of the subject
     * @param request The new mode
     * @param force When true, force the mode change
     * @param groupId Optional group ID header for multi-tenancy
     * @return The new mode
     */
    @PUT
    @Path("/{subject}")
    ModeDto updateSubjectMode(@PathParam("subject") String subject, @NotNull ModeDto request,
            @QueryParam("force") Boolean force, @HeaderParam(Headers.GROUP_ID) String groupId);

    /**
     * Delete the mode setting for a specific subject and revert to global.
     *
     * @param subject The name of the subject
     * @param groupId Optional group ID header for multi-tenancy
     * @return The previous mode
     */
    @DELETE
    @Path("/{subject}")
    ModeDto deleteSubjectMode(@PathParam("subject") String subject,
            @HeaderParam(Headers.GROUP_ID) String groupId);
}
