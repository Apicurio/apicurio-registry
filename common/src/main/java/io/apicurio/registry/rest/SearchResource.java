package io.apicurio.registry.rest;

import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
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
      @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit,
      @QueryParam("over") SearchOver over, @QueryParam("order") SortOrder order);

  /**
   * Searches for versions of a specific artifact.  This is typically used to get a listing
   * of all versions of an Artifact (for example in a user interface).
   */
  @Path("/artifacts/{artifactId}/versions")
  @GET
  @Produces("application/json")
  VersionSearchResults searchVersions(@PathParam("artifactId") String artifactId,
      @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit);
}
