package io.apicurio.registry.rest;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletionStage;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/artifacts")
public interface ArtifactsResource {
  /**
   * Set the artifact state of the latest artifact.
   * <p>
   * This operation can fail for the following reasons:
   * <p>
   * * Artifact cannot transition to this state (HTTP error `400`)
   * * No artifact with the `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/state/{state}")
  @PUT
  @Consumes("application/json")
  void updateArtifactState(@PathParam("artifactId") String artifactId, @PathParam("state") ArtifactState state);

  /**
   * Set the artifact state.
   * <p>
   * This operation can fail for the following reasons:
   * <p>
   * * Artifact cannot transition to this state (HTTP error `400`)
   * * No artifact with the `artifactId` and `version` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/state/{state}/{version}")
  @PUT
  @Consumes("application/json")
  void updateArtifactState(@PathParam("artifactId") String artifactId, @PathParam("state") ArtifactState state, @PathParam("version") Integer version);

  /**
   * Gets the metadata for an artifact in the registry.  The returned metadata will include
   * both generated (read-only) and editable metadata (such as name and description).
   * <p>
   * This operation can fail for the following reasons:
   * <p>
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/meta")
  @GET
  @Produces("application/json")
  ArtifactMetaData getArtifactMetaData(@PathParam("artifactId") String artifactId);

  /**
   * Updates the editable parts of the artifact's metadata.  Not all metadata fields can
   * be updated.  For example, `createdOn` and `createdBy` are both read-only properties.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with the `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/meta")
  @PUT
  @Consumes("application/json")
  void updateArtifactMetaData(@PathParam("artifactId") String artifactId, EditableMetaData data);

  /**
   * Gets the metadata for an artifact that matches the raw content.  Searches the registry
   * for a version of the given artifact matching the content provided in the body of the
   * POST.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with the `artifactId` exists (HTTP error `404`)
   * * No artifact version matching the provided content exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/meta")
  @POST
  @Produces("application/json")
  @Consumes({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  ArtifactMetaData getArtifactMetaDataByContent(@PathParam("artifactId") String artifactId,
      InputStream data);

  /**
   * Returns information about a single rule configured for an artifact.  This is useful
   * when you want to know what the current configuration settings are for a specific rule.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No rule with this name/type is configured for this artifact (HTTP error `404`)
   * * Invalid rule type (HTTP error `400`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/rules/{rule}")
  @GET
  @Produces("application/json")
  Rule getArtifactRuleConfig(@PathParam("rule") RuleType rule,
      @PathParam("artifactId") String artifactId);

  /**
   * Updates the configuration of a single rule for the artifact.  The configuration data
   * is specific to each rule type, so the configuration of the `COMPATIBILITY` rule 
   * will be in a different format from the configuration of the `VALIDITY` rule.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No rule with this name/type is configured for this artifact (HTTP error `404`)
   * * Invalid rule type (HTTP error `400`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/rules/{rule}")
  @PUT
  @Produces("application/json")
  @Consumes("application/json")
  Rule updateArtifactRuleConfig(@PathParam("rule") RuleType rule,
      @PathParam("artifactId") String artifactId, Rule data);

  /**
   * Deletes a rule from the artifact.  This results in the rule no longer applying for
   * this artifact.  If this is the only rule configured for the artifact, this is the 
   * same as deleting **all** rules, and the globally configured rules will now apply to
   * this artifact.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No rule with this name/type is configured for this artifact (HTTP error `404`)
   * * Invalid rule type (HTTP error `400`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/rules/{rule}")
  @DELETE
  void deleteArtifactRule(@PathParam("rule") RuleType rule,
      @PathParam("artifactId") String artifactId);

  /**
   * Retrieves a single version of the artifact content.  Both the `artifactId` and the
   * unique `version` number must be provided.  The `Content-Type` of the response will 
   * depend on the artifact type.  In most cases, this will be `application/json`, but 
   * for some types it may be different (for example, `PROTOBUF`).
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No version with this `version` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions/{version}")
  @GET
  @Produces({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  Response getArtifactVersion(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);

  /**
   * Deletes a single version of the artifact.  Both the `artifactId` and the unique `version`
   * are needed.  If this is the only version of the artifact, this operation is the same as 
   * deleting the entire artifact.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No version with this `version` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions/{version}")
  @DELETE
  void deleteArtifactVersion(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);

  /**
   * Retrieves the metadata for a single version of the artifact.  The version metadata is 
   * a subset of the artifact metadata and only includes the metadata that is specific to
   * the version (for example, this doesn't include `modifiedOn`).
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No version with this `version` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions/{version}/meta")
  @GET
  @Produces("application/json")
  VersionMetaData getArtifactVersionMetaData(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);

  /**
   * Updates the user-editable portion of the artifact version's metadata.  Only some of 
   * the metadata fields are editable by the user.  For example, `description` is editable, 
   * but `createdOn` is not.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No version with this `version` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions/{version}/meta")
  @PUT
  @Consumes("application/json")
  void updateArtifactVersionMetaData(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId, EditableMetaData data);

  /**
   * Deletes the user-editable metadata properties of the artifact version.  Any properties
   * that are not user-editable will be preserved.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No version with this `version` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions/{version}/meta")
  @DELETE
  void deleteArtifactVersionMetaData(@PathParam("version") Integer version,
      @PathParam("artifactId") String artifactId);

  /**
   * Returns a list of all rules configured for the artifact.  The set of rules determines
   * how the content of an artifact can evolve over time.  If no rules are configured for
   * an artifact, the set of globally configured rules will be used.  If no global rules 
   * are defined, there are no restrictions on content evolution.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/rules")
  @GET
  @Produces("application/json")
  List<RuleType> listArtifactRules(@PathParam("artifactId") String artifactId);

  /**
   * Adds a rule to the list of rules that get applied to the artifact when adding new
   * versions.  All configured rules must pass to successfully add a new artifact version.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * Rule (named in the request body) is unknown (HTTP error `400`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/rules")
  @POST
  @Consumes("application/json")
  void createArtifactRule(@PathParam("artifactId") String artifactId, Rule data);

  /**
   * Deletes all of the rules configured for the artifact.  After this is done, the global
   * rules will apply to the artifact again.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}/rules")
  @DELETE
  void deleteArtifactRules(@PathParam("artifactId") String artifactId);

  /**
   * Creates a new artifact by posting the artifact content.  The body of the request should
   * be the raw content of the artifact.  This will typically be in JSON format for *most*
   * of the supported types, but may be in another format for a few (for example, `PROTOBUF`).
   *
   * The registry will attempt to figure out what kind of artifact is being added from the
   * following supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   *
   * Alternatively, the artifact type can be indicated by explicitly specifying the type using 
   * the `X-Registry-ArtifactType` HTTP request header or by including a hint in the request's 
   * `Content-Type`.  For example:
   *
   * ```
   * Content-Type: application/json; artifactType=AVRO
   * ```
   *
   * This operation may fail for one of the following reasons:
   *
   * * An invalid `ArtifactType` was indicated (HTTP error `400`)
   * * The content violates one of the configured global rules (HTTP error `409`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @POST
  @Produces("application/json")
  @Consumes({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  CompletionStage<ArtifactMetaData> createArtifact(
      @HeaderParam("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType,
      @HeaderParam("X-Registry-ArtifactId") String xRegistryArtifactId, InputStream data);

  /**
   * Returns the latest version of the artifact in its raw form.  The `Content-Type` of the
   * response will depend on the artifact type.  In most cases, this will be `application/json`, 
   * but for some types it may be different (for example, `PROTOBUF`).
   *
   * This operation may fail for one of the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}")
  @GET
  @Produces({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  Response getLatestArtifact(@PathParam("artifactId") String artifactId);

  /**
   * Updates an artifact by uploading new content.  The body of the request should
   * be the raw content of the artifact.  This will typically be in JSON format for *most*
   * of the supported types, but may be in another format for a few (for example, `PROTOBUF`).
   *
   * The registry will attempt to figure out what kind of artifact is being added from the
   * following supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   *
   * Alternatively, the artifact type can be indicated by explicitly specifying the type using 
   * the `X-Registry-ArtifactType` HTTP request header or by including a hint in the request's 
   * `Content-Type`.  For example:
   *
   * ```
   * Content-Type: application/json; artifactType=AVRO
   * ```
   *
   * The update could fail for a number of reasons including:
   *
   * * No artifact with the `artifactId` exists (HTTP error `404`)
   * * The new content violates one of the rules configured for the artifact (HTTP error `409`)
   * * The provided artifact type is not recognized (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   * When successful, this creates a new version of the artifact, making it the most recent
   * (and therefore official) version of the artifact.
   */
  @Path("/{artifactId}")
  @PUT
  @Produces("application/json")
  @Consumes({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  CompletionStage<ArtifactMetaData> updateArtifact(@PathParam("artifactId") String artifactId,
      @HeaderParam("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, InputStream data);

  /**
   * Deletes an artifact completely, resulting in all versions of the artifact also being
   * deleted.  This may fail for one of the following reasons:
   *
   * * No artifact with the `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   */
  @Path("/{artifactId}")
  @DELETE
  void deleteArtifact(@PathParam("artifactId") String artifactId);

  /**
   * Returns a list of all version numbers for the artifact.
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions")
  @GET
  @Produces("application/json")
  List<Long> listArtifactVersions(@PathParam("artifactId") String artifactId);

  /**
   * Creates a new version of the artifact by uploading new content.  The configured rules for
   * the artifact will be applied, and if they all pass, the new content will be added as the 
   * most recent version of the artifact.  If any of the rules fail, an error will be returned.
   *
   * The body of the request should be the raw content of the new artifact version.  This 
   * will typically be in JSON format for *most* of the supported types, but may be in another 
   * format for a few (for example, `PROTOBUF`).
   *
   * The registry will attempt to figure out what kind of artifact is being added from the
   * following supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   *
   * Alternatively, the artifact type can be indicated be explicitly specifying the type 
   * using the `X-Registry-ArtifactType` HTTP request header or by including a hint in the 
   * request's `Content-Type`.
   *
   * For example:
   *
   * ```
   * Content-Type: application/json; artifactType=AVRO
   * ```
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * The new content violates one of the rules configured for the artifact (HTTP error `409`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions")
  @POST
  @Produces("application/json")
  @Consumes({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  CompletionStage<VersionMetaData> createArtifactVersion(@PathParam("artifactId") String artifactId,
      @HeaderParam("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, InputStream data);

  /**
   * Tests whether an update to the artifact's content *would* succeed for the provided content.
   * Ultimately, this will apply any rules configured for the artifact against the given content
   * to determine whether the rules would pass or fail, but without actually updating the artifact
   * content.
   *
   * The body of the request should be the raw content of the artifact.  This will typically be 
   * in JSON format for *most* of the supported types, but may be in another format for a few 
   * (for example, `PROTOBUF`).
   *
   * The registry will attempt to figure out what kind of artifact is being added from the
   * following supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   *
   * Alternatively, the artifact type can be indicated by explicitly specifying the type using 
   * the `X-Registry-ArtifactType` HTTP request header or by including a hint in the request's 
   * `Content-Type`.  For example:
   *
   * ```
   * Content-Type: application/json; artifactType=AVRO
   * ```
   *
   * The update could fail for a number of reasons including:
   *
   * * No artifact with the `artifactId` exists (HTTP error `404`)
   * * The new content violates one of the rules configured for the artifact (HTTP error `409`)
   * * The provided artifact type is not recognized (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   * When successful, this operation simply returns a *No Content* response.
   */
  @Path("/{artifactId}/test")
  @PUT
  @Consumes({"application/json", "application/x-protobuf", "application/x-protobuffer"})
  void testUpdateArtifact(@PathParam("artifactId") String artifactId,
      @HeaderParam("X-Registry-ArtifactType") ArtifactType xRegistryArtifactType, InputStream data);
}
