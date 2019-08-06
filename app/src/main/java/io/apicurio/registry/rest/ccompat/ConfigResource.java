package io.apicurio.registry.rest.ccompat;

import io.apicurio.registry.dto.ConfigDto;

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
@Path("/config")
@Consumes({RestConstants.JSON, RestConstants.SR})
@Produces({RestConstants.JSON, RestConstants.SR})
public class ConfigResource extends AbstractResource {

    @Path("/{subject}")
    @PUT
    public ConfigDto updateSubjectLevelConfig(
        @PathParam("subject") String subject,
        @Context HttpHeaders headers,
        @NotNull ConfigDto request) {
        return request;
    }

    @Path("/{subject}")
    @GET
    public ConfigDto getSubjectLevelConfig(@PathParam("subject") String subject) {
        return null;
    }

    @PUT
    public ConfigDto updateTopLevelConfig(
        @Context HttpHeaders headers,
        @NotNull ConfigDto request) {
        return request;
    }

    @GET
    public ConfigDto getTopLevelConfig() {
        return null;
    }
}
