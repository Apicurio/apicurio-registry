package io.apicurio.registry.ccompat.rest.v7;


import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import java.util.List;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/platform/7.2.1/schema-registry/develop/api.html#exporters">API specification</a> is owned by Confluent.
 *
 * The contexts resource allows you to query the information or manipulate the lifecycle of schema contexts.
 *
 * We do not support this endpoint, if it's used, the default context will be returned.
 *
 */
@Path("/apis/ccompat/v7/contexts")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface ContextResource {

    @GET
    List<String> getContexts() throws Exception;
}
