package io.apicurio.registry.ibmcompat.api;

import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_CONCURRENT_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_GROUP_TAG;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_COUNT_DESC;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME;
import static io.apicurio.registry.metrics.MetricIDs.REST_REQUEST_RESPONSE_TIME_DESC;
import static org.eclipse.microprofile.metrics.MetricUnits.MILLISECONDS;

import java.util.List;

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

import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;

import io.apicurio.registry.ibmcompat.model.AnyOfStateModificationEnabledModification;
import io.apicurio.registry.ibmcompat.model.NewSchema;
import io.apicurio.registry.ibmcompat.model.NewSchemaVersion;
import io.apicurio.registry.ibmcompat.model.Schema;
import io.apicurio.registry.ibmcompat.model.SchemaInfo;
import io.apicurio.registry.ibmcompat.model.SchemaListItem;
import io.apicurio.registry.metrics.RestMetricsApply;
import io.apicurio.registry.storage.ArtifactNotFoundException;

@Path("/ibmcompat")
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
    throws ArtifactNotFoundException {
        service.apiSchemasPost(response, schema, verify);
    }

    @DELETE
    @Path("/schemas/{schemaid}")
    @Produces({"application/json"})
    public Response apiSchemasSchemaidDelete(@PathParam("schemaid") String schemaid)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidDelete(schemaid);
    }

    @GET
    @Path("/schemas/{schemaid}")
    @Produces({"application/json"})
    public SchemaInfo apiSchemasSchemaidGet(@PathParam("schemaid") String schemaid)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidGet(schemaid);
    }

    @PATCH
    @Path("/schemas/{schemaid}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public Response apiSchemasSchemaidPatch(@PathParam("schemaid") String schemaid, @NotNull @Valid List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidPatch(schemaid, anyOfStateModificationEnabledModification);
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
    throws ArtifactNotFoundException {
        service.apiSchemasSchemaidVersionsPost(response, schemaid, schema, verify);
    }

    @DELETE
    @Path("/schemas/{schemaid}/versions/{versionnum}")
    @Produces({"application/json"})
    public Response apiSchemasSchemaidVersionsVersionnumDelete(@PathParam("schemaid") String schemaid, @PathParam("versionnum") int versionnum)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidVersionsVersionnumDelete(schemaid, versionnum);
    }

    @GET
    @Path("/schemas/{schemaid}/versions/{versionnum}")
    @Produces({"application/json", "application/vnd.apache.avro+json"})
    public Schema apiSchemasSchemaidVersionsVersionnumGet(@PathParam("schemaid") String schemaid, @PathParam("versionnum") int versionnum)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidVersionsVersionnumGet(schemaid, versionnum);
    }

    @PATCH
    @Path("/schemas/{schemaid}/versions/{versionnum}")
    @Consumes({"application/json"})
    @Produces({"application/json"})
    public Response apiSchemasSchemaidVersionsVersionnumPatch(@PathParam("schemaid") String schemaid, @PathParam("versionnum") int versionnum, @NotNull @Valid List<AnyOfStateModificationEnabledModification> anyOfStateModificationEnabledModification)
    throws ArtifactNotFoundException {
        return service.apiSchemasSchemaidVersionsVersionnumPatch(schemaid, versionnum, anyOfStateModificationEnabledModification);
    }
}
