/*
 * Copyright 2022 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.ccompat.rest.v6;

import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.Schema;
import io.apicurio.registry.ccompat.dto.SchemaId;
import io.apicurio.registry.ccompat.dto.SchemaInfo;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import java.util.List;

import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST;
import static io.apicurio.registry.ccompat.rest.ContentTypes.COMPAT_SCHEMA_REGISTRY_V1;
import static io.apicurio.registry.ccompat.rest.ContentTypes.JSON;
import static io.apicurio.registry.ccompat.rest.ContentTypes.OCTET_STREAM;

/**
 * Note:
 * <p/>
 * This <a href="https://docs.confluent.io/5.5.0/schema-registry/develop/api.html#subjects">API specification</a> is owned by Confluent.
 *
 *
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Path("/apis/ccompat/v6/subjects/{subject}/versions")
@Consumes({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
@Produces({JSON, OCTET_STREAM, COMPAT_SCHEMA_REGISTRY_V1, COMPAT_SCHEMA_REGISTRY_STABLE_LATEST})
public interface SubjectVersionsResource {

    // ----- Path: /subjects/{subject}/versions -----

    /**
     * Get a list of versions registered under the specified subject.
     * <p/>
     * Parameters:
     *
     * <ul>
     *   <li>subject (string) – the name of the subject</li>
     * </ul>
     *
     * Response JSON Array of Objects:
     * <ul>
     *   <li>version (int) – version of the schema registered under this subject</li>
     * </ul>
     *
     * Status Codes:
     * <ul>
     *   <li>404 Not Found
     *     <ul>
     *       <li>Error code 40401 – Subject not found</li>
     *     </ul>
     *   </li>
     *   <li>500 Internal Server Error
     *     <ul>
     *       <li>Error code 50001 – Error in the backend datastore</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    @GET
    List<Integer> listVersions(@PathParam("subject") String subject) throws Exception;

    /**
     * Register a new schema under the specified subject. If successfully registered,
     * this returns the unique identifier of this schema in the registry.
     * The returned identifier should be used to retrieve this schema from the schemas resource
     * and <b>is different from the schema’s version</b> which is associated with the subject.
     * If the same schema is registered under a different subject,
     * the same identifier will be returned. However, the version of the schema
     * may be different under different subjects.
     *
     * A schema should be compatible with the previously registered schema or schemas (if there are any) as per the configured compatibility level. The configured compatibility level can be obtained by issuing a GET http:get:: /config/(string: subject). If that returns null, then GET http:get:: /config
     *
     * When there are multiple instances of Schema Registry running in the same cluster, the schema registration request will be forwarded to one of the instances designated as the primary. If the primary is not available, the client will get an error code indicating that the forwarding has failed.
     * Parameters:
     *
     *     subject (string) – Subject under which the schema will be registered
     *
     * Request JSON Object:
     *
     *
     *     schema – The schema string
     *
     * Status Codes:
     *
     *     409 Conflict – Incompatible schema
     *     422 Unprocessable Entity –
     *         Error code 42201 – Invalid schema
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend data store
     *         Error code 50002 – Operation timed out
     *         Error code 50003 – Error while forwarding the request to the primary
     */
    @POST
    @Authorized(style=AuthorizedStyle.ArtifactOnly)
    SchemaId register(
            @PathParam("subject") String subject,
            @NotNull SchemaInfo request) throws Exception;


    // ----- Path: /subjects/{subject}/versions/{version} -----


    /**
     * Get a specific version of the schema registered under this subject
     * Parameters:
     *
     *     subject (string) – Name of the subject
     *     version (versionId) – Version of the schema to be returned. Valid values for versionId are between [1,2^31-1] or the string “latest”. “latest” returns the last registered schema under the specified subject. Note that there may be a new latest schema that gets registered right after this request is served.
     *
     * Response JSON Object:
     *
     *
     *     subject (string) – Name of the subject that this schema is registered under
     *     globalId (int) – Globally unique identifier of the schema
     *     version (int) – Version of the returned schema
     *     schema (string) – The schema string
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40401 – Subject not found
     *         Error code 40402 – Version not found
     *     422 Unprocessable Entity –
     *         Error code 42202 – Invalid version
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend data store
     */
    @GET
    @Path("/{version}")
    Schema getSchemaByVersion(
            @PathParam("subject") String subject,
            @PathParam("version") String version) throws Exception;

    /**
     * Deletes a specific version of the schema registered under this subject.
     * This only deletes the version and the schema ID remains intact
     * making it still possible to decode data using the schema ID.
     * This API is recommended to be used only in development environments
     * or under extreme circumstances where-in, its required to delete
     * a previously registered schema for compatibility purposes
     * or re-register previously registered schema.
     *
     * Parameters:
     *
     *     subject (string) – Name of the subject
     *     version (versionId) – Version of the schema to be deleted. Valid values for versionId are between [1,2^31-1] or the string “latest”. “latest” deletes the last registered schema under the specified subject. Note that there may be a new latest schema that gets registered right after this request is served.
     *
     * Response JSON Object:
     *
     *
     *     int – Version of the deleted schema
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40401 – Subject not found
     *         Error code 40402 – Version not found
     *     422 Unprocessable Entity –
     *         Error code 42202 – Invalid version
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend data store
     */
    @DELETE
    @Path("/{version}")
    @Authorized(style=AuthorizedStyle.ArtifactOnly)
    int deleteSchemaVersion(
            @PathParam("subject") String subject,
            @PathParam("version") String version) throws Exception;


    // ----- Path: /subjects/{subject}/versions/{version}/schema -----


    /**
     * Get the schema for the specified version of this subject. The unescaped schema only is returned.
     * Parameters:
     *
     *     subject (string) – Name of the subject
     *     version (versionId) – Version of the schema to be returned. Valid values for versionId are between [1,2^31-1] or the string “latest”. “latest” returns the last registered schema under the specified subject. Note that there may be a new latest schema that gets registered right after this request is served.
     *
     * Response JSON Object:
     *
     *
     *     schema (string) – The schema string (unescaped)
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40401 – Subject not found
     *         Error code 40402 – Version not found
     *     422 Unprocessable Entity –
     *         Error code 42202 – Invalid version
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend data store
     */
    @GET
    @Path("/{version}/schema")
    String getSchemaOnly(
            @PathParam("subject") String subject,
            @PathParam("version") String version) throws Exception;

    // ----- Path: /subjects/{subject}/versions/{version}/referencedby -----

    /**
     * Get a list of IDs of schemas that reference the schema with the given subject and version.
     *
     * Parameters:
     *
     *     subject (string) – the name of the subject
     *     version (versionId) – Version of the schema to be returned.
     *     Valid values for versionId are between [1,2^31-1] or the string “latest”.
     *     “latest” returns the last registered schema under the specified subject.
     *     Note that there may be a new latest schema that gets registered right after this request is served.
     *
     * Response JSON Array of Objects:
     *
     *      id (int) – Globally unique identifier of the schema
     *
     *
     * Status Codes:
     *
     *     404 Not Found –
     *         Error code 40401 – Subject not found
     *     500 Internal Server Error –
     *         Error code 50001 – Error in the backend datastore
     */
    @GET
    @Path("/{version}/referencedby")
    List<Long> getSchemasReferencedBy(
            @PathParam("subject") String subject, @PathParam("version") String version) throws Exception;


}
