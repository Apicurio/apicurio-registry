package io.apicurio.registry.ccompat.rest.v7;

import io.apicurio.registry.ccompat.dto.ModeDto;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/platform/7.2.1/schema-registry/develop/api.html#free-up-artifactStore-space-in-the-registry-for-new-schemas">API specification</a> is owned by Confluent.
 *
 * We <b>DO NOT</b> support this endpoint. Fails with 404.
 *
 */
@Path("/apis/ccompat/v7/mode")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface ModeResource {

    // ----- Path: /mode -----

    @GET
    ModeDto getGlobalMode();


    @PUT
    ModeDto updateGlobalMode(
            @NotNull ModeDto request);
}
