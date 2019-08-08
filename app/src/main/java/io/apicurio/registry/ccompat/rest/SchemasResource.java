package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.SchemaString;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * @author Ales Justin
 */
@Path("/confluent/schemas")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class SchemasResource extends AbstractResource {

    @GET
    @Path("/ids/{id}")
    public SchemaString getSchema(@PathParam("id") Integer id) throws Exception {
        String schema = facade.getSchema(id);
        if (schema == null) {
            Errors.schemaNotFound(id);
        }
        return new SchemaString(schema);
    }
}
