package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.ModeDto;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import static io.apicurio.registry.ccompat.rest.ContentTypes.*;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/5.4.1/schema-registry/develop/api.html#free-up-storage-space-in-the-registry-for-new-schemas">API specification</a> is owned by Confluent.
 *
 * We <b>DO NOT</b> support this endpoint. Fails with 404.
 *
 * @author Ales Justin
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Path("/ccompat/mode")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({COMPAT_SCHEMA_REGISTRY_V1})
public interface ModeResource {

    // ----- Path: /mode -----

    @GET
    ModeDto getGlobalMode();


    @PUT
    ModeDto updateGlobalMode(
            @NotNull ModeDto request);
}
