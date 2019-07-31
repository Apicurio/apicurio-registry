package io.apicurio.registry.rest;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;
import java.lang.Integer;
import java.lang.String;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Request;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/artifacts")
public interface ArtifactsResource {
  @POST
  @Produces("application/json")
  @Consumes({"application/json", "application/x-yaml"})
  ArtifactMetaData createArtifact(Request data);

  /**
   * Returns the latest version of the artifact.
   */
  @Path("/{artifactId}")
  @GET
  @Produces({"application/json", "application/x-yaml"})
  void getLatestArtifact(@PathParam("artifactId") String artifactId);

  /**
   * Updates an artifact by uploading new content.  The update could fail for a number
   * of reasons including:
   *
   * * No artifact with the `artifactId` exists
   * * The new content violates one of the rules configured for the artifact
   *
   * When successful, this creates a new version of the artifact, making it the most recent
   * (and therefore official) version of the artifact.
   */
  @Path("/{artifactId}")
  @PUT
  @Produces("application/json")
  @Consumes({"application/json", "application/x-yaml"})
  ArtifactMetaData updateArtifact(@PathParam("artifactId") String artifactId, Request data);

  @Path("/{artifactId}")
  @DELETE
  void deleteArtifact(@PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/versions")
  @GET
  @Produces("application/json")
  List<Integer> listArtifactVersions(@PathParam("artifactId") String artifactId);

  /**
   * Creates a new version of the artifact by uploading new content.  The configured rules for
   * the artifact will be applied, and if they all pass then the new content will be added
   * as the most recent version of the artifact.  If any of the rules fail then an error 
   * will be returned.
   */
  @Path("/{artifactId}/versions")
  @POST
  @Produces("application/json")
  @Consumes({"application/json", "application/x-yaml"})
  VersionMetaData createArtifactVersion(@PathParam("artifactId") String artifactId, Request data);

  @Path("/{artifactId}/versions/{version}")
  @GET
  @Produces({"application/json", "application/x-yaml"})
  void getArtifactVersion(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/versions/{version}")
  @DELETE
  void deleteArtifactVersion(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/rules")
  @GET
  @Produces("application/json")
  List<Rule> listArtifactRules(@PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/rules")
  @POST
  @Consumes("application/json")
  void createArtifactRule(@PathParam("artifactId") String artifactId, Rule data);

  /**
   * Deletes all of the rules configured for the artifact.  After this is done, the global
   * rules will once again apply to the artifact.
   */
  @Path("/{artifactId}/rules")
  @DELETE
  void deleteArtifactRules(@PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/rules/{rule}")
  @GET
  @Produces("application/json")
  Rule getArtifactRuleConfig(@PathParam("rule") String rule,
      @PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/rules/{rule}")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  Rule updateArtifactRuleConfig(@PathParam("rule") String rule,
      @PathParam("artifactId") String artifactId, Rule data);

  @Path("/{artifactId}/rules/{rule}")
  @DELETE
  void deleteArtifactRule(@PathParam("rule") String rule,
      @PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/meta")
  @GET
  @Produces("application/json")
  ArtifactMetaData getArtifactMetaData(@PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/meta")
  @PUT
  @Consumes("application/json")
  void updateArtifactMetaData(@PathParam("artifactId") String artifactId, EditableMetaData data);

  @Path("/{artifactId}/versions/{version}/meta")
  @GET
  @Produces("application/json")
  VersionMetaData getArtifactVersionMetaData(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);

  @Path("/{artifactId}/versions/{version}/meta")
  @PUT
  @Consumes("application/json")
  void updateArtifactVersionMetaData(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId, EditableMetaData data);

  @Path("/{artifactId}/versions/{version}/meta")
  @DELETE
  void deleteArtifactVersionMetaData(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);
}
