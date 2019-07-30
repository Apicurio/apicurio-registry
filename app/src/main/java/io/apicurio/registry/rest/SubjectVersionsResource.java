package io.apicurio.registry.rest;

import io.apicurio.registry.rest.dto.RegisterSchemaRequest;
import io.apicurio.registry.rest.dto.Schema;

import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

/**
 * @author Ales Justin
 */
@Path("/subjects/{subject}/versions")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class SubjectVersionsResource extends AbstractResource {

    @GET
    @Path("/{version}")
    public Schema getSchemaByVersion(
        @PathParam("subject") String subject,
        @PathParam("version") String version) {

        return null;
    }

    @GET
    @Path("/{version}/schema")
    public String getSchemaOnly(
        @PathParam("subject") String subject,
        @PathParam("version") String version) {
        return getSchemaByVersion(subject, version).getSchema();
    }

    @GET
    public List<Integer> listVersions(@PathParam("subject") String subject) {
        return null;
    }

    @POST
    public void register(
        @Suspended AsyncResponse response,
        @Context HttpHeaders headers,
        @PathParam("subject") String subjectName,
        @NotNull RegisterSchemaRequest request) {
    }

    @DELETE
    @Path("/{version}")
    public void deleteSchemaVersion(
        @Suspended AsyncResponse response,
        @Context HttpHeaders headers,
        @PathParam("subject") String subject,
        @PathParam("version") String version) {
    }
}
