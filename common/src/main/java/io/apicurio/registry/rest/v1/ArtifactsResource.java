package io.apicurio.registry.rest.v1;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import io.apicurio.registry.rest.v1.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v1.beans.EditableMetaData;
import io.apicurio.registry.rest.v1.beans.IfExistsType;
import io.apicurio.registry.rest.v1.beans.Rule;
import io.apicurio.registry.rest.v1.beans.UpdateState;
import io.apicurio.registry.rest.v1.beans.VersionMetaData;
import io.apicurio.registry.types.RuleType;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/apis/registry/v1/artifacts")
public interface ArtifactsResource {
  /**
   * Returns a list of IDs of all artifacts in the registry as a flat list.  Typically the
   * server is configured to limit the number of artifact IDs returned in the case where
   * a large number of artifacts exist.  In this case the result of this call may be
   * non deterministic.  The default limit is typically 1000 artifacts.
   */
  @GET
  @Produces("application/json")
  List<String> listArtifacts();

  /**
   * Creates a new artifact by posting the artifact content.  The body of the request should
   * be the raw content of the artifact.  This is typically in JSON format for *most* of the
   * supported types, but may be in another format for a few (for example, `PROTOBUF`).
   *
   * The registry attempts to figure out what kind of artifact is being added from the
   * following supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * Kafka Connect (`KCONNECT`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   * * Web Services Description Language (`WSDL`)
   * * XML Schema (`XSD`)
   *
   * Alternatively, you can explicitly specify the artifact type using the `X-Registry-ArtifactType`
   * HTTP request header, or include a hint in the request's `Content-Type`.  For example:
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
  @Consumes({"*/*"})
  ArtifactMetaData createArtifact(
      @HeaderParam("X-Registry-ArtifactType") String xRegistryArtifactType,
      @HeaderParam("X-Registry-ArtifactId") String xRegistryArtifactId,
      @DefaultValue("FAIL") @QueryParam("ifExists") IfExistsType ifExists,
      @QueryParam("canonical") Boolean canonical,
      InputStream data);

  /**
   * Returns the latest version of the artifact in its raw form.  The `Content-Type` of the
   * response depends on the artifact type.  In most cases, this is `application/json`, but
   * for some types it may be different (for example, `PROTOBUF`).
   *
   * This operation may fail for one of the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}")
  @GET
  @Produces({"application/json", "application/x-protobuf", "application/x-protobuffer", "application/xml", "application/graphql"})
  Response getLatestArtifact(@PathParam("artifactId") String artifactId);

  /**
   * Updates an artifact by uploading new content.  The body of the request should
   * be the raw content of the artifact.  This is typically in JSON format for *most*
   * of the supported types, but may be in another format for a few (for example, `PROTOBUF`).
   *
   * The registry attempts to figure out what kind of artifact is being added from the
   * following supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * Kafka Connect (`KCONNECT`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   * * Web Services Description Language (`WSDL`)
   * * XML Schema (`XSD`)
   *
   * Alternatively, you can specify the artifact type using the `X-Registry-ArtifactType`
   * HTTP request header, or include a hint in the request's `Content-Type`.  For example:
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
  @Consumes({"*/*"})
  ArtifactMetaData updateArtifact(@PathParam("artifactId") String artifactId,
      @HeaderParam("X-Registry-ArtifactType") String xRegistryArtifactType, InputStream data);

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
   * Updates the state of the artifact.  For example, you can use this to mark the latest
   * version of an artifact as `DEPRECATED`.  The operation changes the state of the latest
   * version of the artifact.  If multiple versions exist, only the most recent is changed.
   *
   * The following state changes are supported:
   *
   * * Enabled -&gt; Disabled
   * * Enabled -&gt; Deprecated
   * * Enabled -&gt; Deleted
   * * Disabled -&gt; Enabled
   * * Disabled -&gt; Deleted
   * * Disabled -&gt; Deprecated
   * * Deprecated -&gt; Deleted
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * Artifact cannot transition to the given state (HTTP error `400`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/state")
  @PUT
  @Consumes("application/json")
  void updateArtifactState(@PathParam("artifactId") String artifactId, UpdateState data);

  /**
   * Gets the metadata for an artifact in the registry.  The returned metadata includes
   * both generated (read-only) and editable metadata (such as name and description).
   *
   * This operation can fail for the following reasons:
   *
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
  @Consumes({"*/*"})
  VersionMetaData getArtifactVersionMetaDataByContent(@PathParam("artifactId") String artifactId,
          @QueryParam("canonical") Boolean canonical, InputStream data);

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
   * the artifact are applied, and if they all pass, the new content is added as the most recent
   * version of the artifact.  If any of the rules fail, an error is returned.
   *
   * The body of the request should be the raw content of the new artifact version.  This
   * is typically in JSON format for *most* of the supported types, but may be in another
   * format for a few (for example, `PROTOBUF`).
   *
   * The registry attempts to figure out what kind of artifact is being added from the
   * following supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * Kafka Connect (`KCONNECT`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   * * Web Services Description Language (`WSDL`)
   * * XML Schema (`XSD`)
   *
   * Alternatively, you can explicitly specify the artifact type using the `X-Registry-ArtifactType`
   * HTTP request header, or by including a hint in the request's `Content-Type`.
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
  @Consumes({"*/*"})
  VersionMetaData createArtifactVersion(@PathParam("artifactId") String artifactId,
      @HeaderParam("X-Registry-ArtifactType") String xRegistryArtifactType, InputStream data);

  /**
   * Retrieves a single version of the artifact content.  Both the `artifactId` and the
   * unique `version` number must be provided.  The `Content-Type` of the response depends
   * on the artifact type.  In most cases, this is `application/json`, but for some types
   * it may be different (for example, `PROTOBUF`).
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
  @Produces({"application/json", "application/x-protobuf", "application/x-protobuffer", "application/xml", "application/graphql"})
  Response getArtifactVersion(@PathParam("artifactId") String artifactId, @PathParam("version") Integer version);

  /**
   * Updates the state of a specific version of an artifact.  For example, you can use
   * this operation to disable a specific version.
   *
   * The following state changes are supported:
   *
   * * Enabled -&gt; Disabled
   * * Enabled -&gt; Deprecated
   * * Enabled -&gt; Deleted
   * * Disabled -&gt; Enabled
   * * Disabled -&gt; Deleted
   * * Disabled -&gt; Deprecated
   * * Deprecated -&gt; Deleted
   *
   * This operation can fail for the following reasons:
   *
   * * No artifact with this `artifactId` exists (HTTP error `404`)
   * * No version with this `version` exists (HTTP error `404`)
   * * Artifact version cannot transition to the given state (HTTP error `400`)
   * * A server error occurred (HTTP error `500`)
   *
   */
  @Path("/{artifactId}/versions/{version}/state")
  @PUT
  @Consumes("application/json")
  void updateArtifactVersionState(@PathParam("artifactId") String artifactId, @PathParam("version") Integer version, UpdateState data);

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
  VersionMetaData getArtifactVersionMetaData(@PathParam("artifactId") String artifactId, @PathParam("version") Integer version);

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
  void updateArtifactVersionMetaData(@PathParam("artifactId") String artifactId, @PathParam("version") Integer version, EditableMetaData data);

  /**
   * Deletes the user-editable metadata properties of the artifact version.  Any properties
   * that are not user-editable are preserved.
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
  void deleteArtifactVersionMetaData(@PathParam("artifactId") String artifactId, @PathParam("version") Integer version);

  /**
   * Returns a list of all rules configured for the artifact.  The set of rules determines
   * how the content of an artifact can evolve over time.  If no rules are configured for
   * an artifact, the set of globally configured rules are used.  If no global rules
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
   * rules apply to the artifact again.
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
  Rule getArtifactRuleConfig(@PathParam("artifactId") String artifactId, @PathParam("rule") RuleType rule);

  /**
   * Updates the configuration of a single rule for the artifact.  The configuration data
   * is specific to each rule type, so the configuration of the `COMPATIBILITY` rule
   * is in a different format from the configuration of the `VALIDITY` rule.
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
  Rule updateArtifactRuleConfig(@PathParam("artifactId") String artifactId, @PathParam("rule") RuleType rule, Rule data);

  /**
   * Deletes a rule from the artifact.  This results in the rule no longer applying for
   * this artifact.  If this is the only rule configured for the artifact, this is the
   * same as deleting **all** rules, and the globally configured rules now apply to
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
  void deleteArtifactRule(@PathParam("artifactId") String artifactId, @PathParam("rule") RuleType rule);

  /**
   * Tests whether an update to the artifact's content *would* succeed for the provided content.
   * Ultimately, this applies any rules configured for the artifact against the given content
   * to determine whether the rules would pass or fail, but without actually updating the artifact
   * content.
   *
   * The body of the request should be the raw content of the artifact.  This is typically in
   * JSON format for *most* of the supported types, but may be in another format for a few
   * (for example, `PROTOBUF`).
   *
   * The registry attempts to figure out what kind of artifact is being added from the following
   * supported list:
   *
   * * Avro (`AVRO`)
   * * Protobuf (`PROTOBUF`)
   * * Protobuf File Descriptor (`PROTOBUF_FD`)
   * * JSON Schema (`JSON`)
   * * Kafka Connect (`KCONNECT`)
   * * OpenAPI (`OPENAPI`)
   * * AsyncAPI (`ASYNCAPI`)
   * * GraphQL (`GRAPHQL`)
   *
   * Alternatively, you can explicitly specify the artifact type using the `X-Registry-ArtifactType`
   * HTTP request header, or by including a hint in the request's `Content-Type`.  For example:
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
  @Consumes({"*/*"})
  void testUpdateArtifact(@PathParam("artifactId") String artifactId,
      @HeaderParam("X-Registry-ArtifactType") String xRegistryArtifactType, InputStream data);
}
