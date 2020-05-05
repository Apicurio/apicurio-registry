package io.apicurio.registry.rest;

import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/search")
public interface SearchResource {
  /**
   * Returns a paginated list of all artifacts that match the provided search criteria.
   *
   */
  @Path("/artifacts")
  @GET
  @Produces("application/json")
  ArtifactSearchResults searchArtifacts(@QueryParam("search") String search,
      @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit);
}
