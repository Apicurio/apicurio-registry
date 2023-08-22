package io.apicurio.registry.rest.v1;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import io.apicurio.registry.rest.v1.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v1.beans.SearchOver;
import io.apicurio.registry.rest.v1.beans.SortOrder;
import io.apicurio.registry.rest.v1.beans.VersionSearchResults;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/apis/registry/v1/search")
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
