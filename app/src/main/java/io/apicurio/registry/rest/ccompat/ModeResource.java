package io.apicurio.registry.rest.ccompat;

import io.apicurio.registry.dto.ModeDto;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

/**
 * @author Ales Justin
 */
@Path("/mode")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class ModeResource extends AbstractResource {

    @Path("/{subject}")
    @PUT
    public ModeDto updateMode(
        @PathParam("subject") String subject,
        @Context HttpHeaders headers,
        @NotNull ModeDto request
    ) {
        return request;
    }

    @Path("/{subject}")
    @GET
    public ModeDto getMode(@PathParam("subject") String subject) {
        return null;
    }

    @PUT
    public ModeDto updateTopLevelMode(
        @Context HttpHeaders headers,
        @NotNull ModeDto request) {
        return updateMode(null, headers, request);
    }

    @GET
    public ModeDto getTopLevelMode() {
        return getMode(null);
    }
}
