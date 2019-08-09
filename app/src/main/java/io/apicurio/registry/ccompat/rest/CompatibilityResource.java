package io.apicurio.registry.ccompat.rest;

import io.apicurio.registry.ccompat.dto.CompatibilityCheckResponse;
import io.apicurio.registry.ccompat.dto.RegisterSchemaRequest;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;

/**
 * @author Ales Justin
 */
@Path("/confluent/compatibility")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class CompatibilityResource extends AbstractResource {

    @POST
    @Path("/subjects/{subject}/versions/{version}")
    public void testCompatabilityBySubjectName(
        @Suspended AsyncResponse response,
        @HeaderParam("Content-Type") String contentType,
        @HeaderParam("Accept") String accept,
        @PathParam("subject") String subject,
        @PathParam("version") String version,
        @NotNull RegisterSchemaRequest request) throws Exception {

        // TODO - headers?
        boolean isCompatible = facade.testCompatibility(subject, version, request);
        CompatibilityCheckResponse result = new CompatibilityCheckResponse();
        result.setIsCompatible(isCompatible);
        response.resume(result);
    }
}
