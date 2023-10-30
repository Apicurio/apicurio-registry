/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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
package io.apicurio.registry.ibmcompat.api;

import io.apicurio.registry.ibmcompat.api.impl.ApiUtil;
import io.apicurio.registry.ibmcompat.model.NewSchema;
import io.apicurio.registry.ibmcompat.model.NewSchemaVersion;
import io.apicurio.registry.ibmcompat.model.Schema;
import io.apicurio.registry.ibmcompat.model.SchemaInfo;
import io.apicurio.registry.ibmcompat.model.SchemaListItem;
import io.apicurio.registry.ibmcompat.model.SchemaModificationPatch;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.util.List;

@Path("/apis/ibmcompat/v1")
public class Api {

    @Inject
    ApiService service;

    @GET
    @Path("/schemas")
    @Produces({"application/json"})
    public List<SchemaListItem> apiSchemasGet(@Min(0) @DefaultValue("0") @QueryParam("page") int page, @Min(1) @DefaultValue("100") @QueryParam("per_page") int perPage)
    throws ArtifactNotFoundException {
        return service.apiSchemasGet(page, perPage);
    }

    @POST
    @Path("/schemas")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public void apiSchemasPost(
        @Suspended AsyncResponse response,
        @NotNull @Valid NewSchema schema,
        @DefaultValue("false") @QueryParam("verify") boolean verify,
        @Context SecurityContext securityContext
    )
    throws ArtifactNotFoundException, ArtifactAlreadyExistsException {
        service.apiSchemasPost(response, schema, verify);
    }

    @DELETE
    @Path("/schemas/{schemaid}")
    @Produces({"application/json"})
    public Response apiSchemasSchemaidDelete(@PathParam("schemaid") String schemaid)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidDelete(ApiUtil.normalizeSchemaID(schemaid));
    }

    @GET
    @Path("/schemas/{schemaid}")
    @Produces({"application/json"})
    public SchemaInfo apiSchemasSchemaidGet(@PathParam("schemaid") String schemaid)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidGet(ApiUtil.normalizeSchemaID(schemaid));
    }

    @PATCH
    @Path("/schemas/{schemaid}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public Response apiSchemasSchemaidPatch(@PathParam("schemaid") String schemaid, @NotNull @Valid List<SchemaModificationPatch> schemaModificationPatches)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidPatch(ApiUtil.normalizeSchemaID(schemaid), schemaModificationPatches);
    }

    @POST
    @Path("/schemas/{schemaid}/versions")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public void apiSchemasSchemaidVersionsPost(
        @Suspended AsyncResponse response,
        @PathParam("schemaid") @NotNull String schemaid,
        @NotNull @Valid NewSchemaVersion schema,
        @DefaultValue("false") @QueryParam("verify") boolean verify
    )
    throws ArtifactNotFoundException, ArtifactAlreadyExistsException {
        service.apiSchemasSchemaidVersionsPost(response, ApiUtil.normalizeSchemaID(schemaid), schema, verify);
    }

    @DELETE
    @Path("/schemas/{schemaid}/versions/{versionnum}")
    @Produces({"application/json"})
    public Response apiSchemasSchemaidVersionsVersionnumDelete(@PathParam("schemaid") String schemaid, @PathParam("versionnum") int versionnum)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidVersionsVersionnumDelete(ApiUtil.normalizeSchemaID(schemaid), versionnum);
    }

    @GET
    @Path("/schemas/{schemaid}/versions/{versionnum}")
    @Produces({"application/json", "application/vnd.apache.avro+json"})
    public Schema apiSchemasSchemaidVersionsVersionnumGet(@PathParam("schemaid") String schemaid, @PathParam("versionnum") int versionnum)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidVersionsVersionnumGet(ApiUtil.normalizeSchemaID(schemaid), versionnum);
    }

    @PATCH
    @Path("/schemas/{schemaid}/versions/{versionnum}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public Response apiSchemasSchemaidVersionsVersionnumPatch(@PathParam("schemaid") String schemaid, @PathParam("versionnum") int versionnum, @NotNull @Valid List<SchemaModificationPatch> schemaModificationPatches)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidVersionsVersionnumPatch(ApiUtil.normalizeSchemaID(schemaid), versionnum, schemaModificationPatches);
    }
}
