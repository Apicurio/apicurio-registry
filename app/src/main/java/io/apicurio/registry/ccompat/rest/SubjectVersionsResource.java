package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.RegisterSchemaRequest;
import io.apicurio.registry.ccompat.dto.RegisterSchemaResponse;
import io.apicurio.registry.ccompat.dto.Schema;

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
@Path("/confluent/subjects/{subject}/versions")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class SubjectVersionsResource extends AbstractResource {

    @GET
    @Path("/{version}")
    public Schema getSchemaByVersion(
        @PathParam("subject") String subject,
        @PathParam("version") String version) throws Exception {

        return facade.getSchema(subject, version);
    }

    @GET
    @Path("/{version}/content")
    public String getSchemaOnly(
        @PathParam("subject") String subject,
        @PathParam("version") String version) throws Exception {

        return getSchemaByVersion(subject, version).getSchema();
    }

    @GET
    public List<Integer> listVersions(@PathParam("subject") String subject) throws Exception {
        return facade.listVersions(subject);
    }

    @POST
    public void register(
        @Suspended AsyncResponse response,
        @Context HttpHeaders headers,
        @PathParam("subject") String subject,
        @NotNull RegisterSchemaRequest request) throws Exception {

        int id = facade.registerSchema(subject, request.getId(), request.getVersion(), request.getSchema());

        RegisterSchemaResponse registerSchemaResponse = new RegisterSchemaResponse();
        registerSchemaResponse.setId(id);
        response.resume(registerSchemaResponse);
    }

    @DELETE
    @Path("/{version}")
    public void deleteSchemaVersion(
        @Suspended AsyncResponse response,
        @Context HttpHeaders headers,
        @PathParam("subject") String subject,
        @PathParam("version") String version) throws Exception {

        response.resume(facade.deleteSchema(subject, version));
    }
}
