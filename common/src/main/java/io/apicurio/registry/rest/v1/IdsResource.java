package io.apicurio.registry.rest.v1;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import io.apicurio.registry.rest.v1.beans.ArtifactMetaData;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/apis/registry/v1/ids")
public interface IdsResource {
  /**
   * Gets the content for an artifact version in the registry using its globally unique
   * identifier.
   *
   * This operation may fail for one of the following reasons:
   *
   * * No artifact version with this `globalId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{globalId}")
  @GET
  @Produces({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  Response getArtifactByGlobalId(@PathParam("globalId") long globalId);

  /**
   * Gets the metadata for an artifact version in the registry using its globally unique
   * identifier.  The returned metadata includes both generated (read-only) and editable
   * metadata (such as name and description).
   *
   * This operation may fail for one of the following reasons:
   *
   * * No artifact version with this `globalId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{globalId}/meta")
  @GET
  @Produces("application/json")
  ArtifactMetaData getArtifactMetaDataByGlobalId(@PathParam("globalId") long globalId);
}
