package io.apicurio.registry.rest.wellknown;

import io.apicurio.registry.a2a.rest.beans.AgentCard;
import io.apicurio.registry.a2a.rest.beans.AgentSearchRequest;
import io.apicurio.registry.a2a.rest.beans.AgentSearchResults;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.mcptools.rest.beans.McpToolSearchResults;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Exposes well-known endpoints under the {@code /apis/registry/v3/well-known} path so that SDK
 * clients (Go, Java, Python, TypeScript) generated from the OpenAPI spec can reach them using
 * the standard v3 base URL.
 *
 * <p>All calls delegate to {@link WellKnownResourceImpl}, which also serves the canonical
 * {@code /.well-known/} paths for A2A protocol compliance.
 */
@Path("/apis/registry/v3/well-known")
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class V3WellKnownResource {

    @Inject
    WellKnownResourceImpl delegate;

    @GET
    @Path("/agent.json")
    @Produces(MediaType.APPLICATION_JSON)
    public AgentCard getAgentCard() {
        return delegate.getAgentCard();
    }

    @GET
    @Path("/a2a")
    @Produces(MediaType.APPLICATION_JSON)
    public AgentCard getAgentCardV1() {
        return delegate.getAgentCardV1();
    }

    @GET
    @Path("/agent-card.json")
    @Produces(MediaType.APPLICATION_JSON)
    public AgentCard getAgentCardForOrchestrate() {
        return delegate.getAgentCardForOrchestrate();
    }

    @GET
    @Path("/agents/{groupId}/{artifactId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRegisteredAgentCard(
            @PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @QueryParam("version") String version) {
        return delegate.getRegisteredAgentCard(groupId, artifactId, version);
    }

    @GET
    @Path("/agents/public")
    @Produces(MediaType.APPLICATION_JSON)
    public AgentSearchResults getPublicAgents(
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("20") Integer limit) {
        return delegate.getPublicAgents(offset, limit);
    }

    @GET
    @Path("/agents/entitled")
    @Produces(MediaType.APPLICATION_JSON)
    public AgentSearchResults getEntitledAgents(
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("20") Integer limit) {
        return delegate.getEntitledAgents(offset, limit);
    }

    @POST
    @Path("/agents/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public AgentSearchResults searchAgentsAdvanced(AgentSearchRequest request) {
        return delegate.searchAgentsAdvanced(request);
    }

    @GET
    @Path("/agents")
    @Produces(MediaType.APPLICATION_JSON)
    public AgentSearchResults searchAgents(
            @QueryParam("name") String name,
            @QueryParam("skill") List<String> skills,
            @QueryParam("capability") List<String> capabilities,
            @QueryParam("inputMode") List<String> inputModes,
            @QueryParam("outputMode") List<String> outputModes,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("20") Integer limit) {
        return delegate.searchAgents(name, skills, capabilities, inputModes, outputModes, offset, limit);
    }

    @GET
    @Path("/mcp-tools/{groupId}/{artifactId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRegisteredMcpTool(
            @PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @QueryParam("version") String version) {
        return delegate.getRegisteredMcpTool(groupId, artifactId, version);
    }

    @GET
    @Path("/mcp-tools")
    @Produces(MediaType.APPLICATION_JSON)
    public McpToolSearchResults searchMcpTools(
            @QueryParam("name") String name,
            @QueryParam("parameter") List<String> parameters,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("20") Integer limit) {
        return delegate.searchMcpTools(name, parameters, offset, limit);
    }

    @GET
    @Path("/schemas/{schemaType}/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSchema(
            @PathParam("schemaType") String schemaType,
            @PathParam("version") String version) {
        return delegate.getSchema(schemaType, version);
    }
}
