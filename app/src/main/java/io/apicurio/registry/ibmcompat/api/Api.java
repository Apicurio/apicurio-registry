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
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.metrics.RestMetricsResponseFilteredNameBinding;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.List;

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

@Path("/apis/ibmcompat/v1")
@RestMetricsResponseFilteredNameBinding
@RestMetricsApply
@Counted(name = REST_REQUEST_COUNT, description = REST_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_COUNT})
@ConcurrentGauge(name = REST_CONCURRENT_REQUEST_COUNT, description = REST_CONCURRENT_REQUEST_COUNT_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_CONCURRENT_REQUEST_COUNT})
@Timed(name = REST_REQUEST_RESPONSE_TIME, description = REST_REQUEST_RESPONSE_TIME_DESC, tags = {"group=" + REST_GROUP_TAG, "metric=" + REST_REQUEST_RESPONSE_TIME}, unit = MILLISECONDS)
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
