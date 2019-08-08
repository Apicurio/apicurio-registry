package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.RegisterSchemaRequest;

import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

/**
 * @author Ales Justin
 */
@Path("/confluent/subjects")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class SubjectsResource extends AbstractResource {

    @POST
    @Path("/{subject}")
    public void findSchemaWithSubject(
        @Suspended AsyncResponse response,
        @PathParam("subject") String subject,
        @QueryParam("deleted") boolean checkDeletedSchema,
        @NotNull RegisterSchemaRequest request) throws Exception {

        checkSubject(subject);

        response.resume(facade.findSchemaWithSubject(subject, checkDeletedSchema, request.getSchema()));
    }

    @GET
    public Set<String> listSubjects() {
        return facade.listSubjects();
    }

    @DELETE
    @Path("/{subject}")
    public void deleteSubject(
        @Suspended AsyncResponse response,
        @Context HttpHeaders headers,
        @PathParam("subject") String subject) throws Exception {

        checkSubject(subject);

        response.resume(facade.deleteSubject(subject));
    }

}
