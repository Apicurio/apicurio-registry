package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.SchemaContent;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import static io.apicurio.registry.ccompat.rest.ContentTypes.*;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/5.4.1/schema-registry/develop/api.html#schemas">API specification</a> is owned by Confluent.
 *
 * @author Ales Justin
 * @author Jakub Senko <jsenko@redhat.com>
 */
@Path("/ccompat/schemas")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({COMPAT_SCHEMA_REGISTRY_V1})
public interface SchemasResource {

    // ----- Path: /schemas/ids/{globalId} -----

    /**
     * Get the schema string identified by the input ID.
     *
     * Parameters:
     *
     * @param id (int) – the globally unique identifier of the schema
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
    SchemaContent getSchema(@PathParam("id") int id);
}
