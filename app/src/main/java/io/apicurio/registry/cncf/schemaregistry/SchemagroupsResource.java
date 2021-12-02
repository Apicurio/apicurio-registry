package io.apicurio.registry.cncf.schemaregistry;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.cncf.schemaregistry.beans.SchemaGroup;
import io.apicurio.registry.cncf.schemaregistry.beans.SchemaId;

/**
 * A JAX-RS interface.  An implementation of this interface must be provided.
 */
@Path("/apis/cncf/v0/schemagroups")
public interface SchemagroupsResource {
  /**
   * Get all schema groups in namespace.
   */
  @GET
  @Produces("application/json")
  List<String> getGroups();

  /**
   * Get schema group description in registry namespace.
   */
  @Path("/{group-id}")
  @GET
  @Produces("application/json")
  SchemaGroup getGroup(@PathParam("group-id") String groupId);

  /**
   * Create schema group with specified format in registry namespace.
   */
  @Path("/{group-id}")
  @PUT
  @Consumes("application/json")
  @Authorized(style=AuthorizedStyle.GroupOnly)
  void createGroup(@PathParam("group-id") String groupId, SchemaGroup data);

  /**
   * Delete schema group in schema registry namespace.
   */
  @Path("/{group-id}")
  @DELETE
  @Authorized(style=AuthorizedStyle.GroupOnly)
  void deleteGroup(@PathParam("group-id") String groupId);

  /**
   * Returns schema by group id.
   */
  @Path("/{group-id}/schemas")
  @GET
  @Produces("application/json")
  List<String> getSchemasByGroup(@PathParam("group-id") String groupId);

  /**
   * Deletes all schemas under specified group id.
   */
  @Path("/{group-id}/schemas")
  @DELETE
  @Authorized(style=AuthorizedStyle.GroupOnly)
  void deleteSchemasByGroup(@PathParam("group-id") String groupId);

  /**
   * Get latest version of schema.
   */
  @Path("/{group-id}/schemas/{schema-id}")
  @GET
  @Produces("application/json;format=avro")
  Response getLatestSchema(@PathParam("group-id") String groupId,
      @PathParam("schema-id") String schemaId);

  /**
   * Register schema. If schema of specified name does not exist in specified group, schema is created at version 1. If schema of specified name exists already in specified group, schema is created at latest version + 1. If schema with identical content already exists, existing schema's ID is returned.
   *
   */
  @Path("/{group-id}/schemas/{schema-id}")
  @POST
  @Produces({"application/json;format=avro", "application/json;format=protobuf"})
  @Consumes("application/json;format=avro")
  @Authorized
  SchemaId createSchema(@PathParam("group-id") String groupId,
      @PathParam("schema-id") String schemaId, InputStream data);

  @Path("/{group-id}/schemas/{schema-id}")
  @DELETE
  @Authorized
  void deleteSchema(@PathParam("group-id") String groupId, @PathParam("schema-id") String schemaId);

  /**
   * Get list of versions for specified schema
   */
  @Path("/{group-id}/schemas/{schema-id}/versions")
  @GET
  @Produces("application/json;format=avro")
  List<Integer> getSchemaVersions(@PathParam("group-id") String groupId,
      @PathParam("schema-id") String schemaId);

  @Path("/{group-id}/schemas/{schema-id}/versions/{version-number}")
  @GET
  @Produces("application/json;format=avro")
  Response getSchemaVersion(@PathParam("group-id") String groupId,
      @PathParam("schema-id") String schemaId, @PathParam("version-number") Integer versionNumber);

  @Path("/{group-id}/schemas/{schema-id}/versions/{version-number}")
  @DELETE
  @Authorized
  void deleteSchemaVersion(@PathParam("group-id") String groupId,
      @PathParam("schema-id") String schemaId, @PathParam("version-number") Integer versionNumber);
}
