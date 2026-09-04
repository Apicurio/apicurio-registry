package io.apicurio.registry.rest.wellknown;

import io.apicurio.registry.mcptools.rest.beans.McpCompatibleToolsResults;
import io.apicurio.registry.rest.v3.beans.AgentCard;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.rest.v3.beans.AiCatalog;
import io.apicurio.registry.rest.v3.beans.ArdExploreRequest;
import io.apicurio.registry.rest.v3.beans.ArdExploreResponse;
import io.apicurio.registry.rest.v3.beans.ArdSearchRequest;
import io.apicurio.registry.rest.v3.beans.ArdSearchResponse;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResults;
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
 * JAX-RS resource for well-known endpoints including A2A protocol and JSON Schemas.
 *
 * Per the A2A protocol specification, agents publish their Agent Card at
 * /.well-known/agent.json for discovery purposes.
 *
 * This resource also serves JSON Schemas for LLM artifact types at
 * /.well-known/schemas/{schemaType}/{version} for IDE autocompletion and validation.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 * @see <a href="https://json-schema.org/">JSON Schema</a>
 */
@Path("/.well-known")
public interface WellKnownResource {

    /**
     * Returns the Agent Card for this Apicurio Registry instance.
     * This endpoint enables A2A protocol discovery of the registry as an agent.
     *
     * @return the Agent Card JSON
     */
    @GET
    @Path("/agent.json")
    @Produces(MediaType.APPLICATION_JSON)
    AgentCard getAgentCard();

    /**
     * Returns the Agent Card for this Apicurio Registry instance.
     * Alias for compatibility with watsonx Orchestrate, which discovers agents
     * at /.well-known/agent-card.json by default.
     */
    @GET
    @Path("/agent-card.json")
    @Produces(MediaType.APPLICATION_JSON)
    AgentCard getAgentCardForOrchestrate();

    /**
     * Returns a specific registered Agent Card by group and artifact ID.
     * This enables proxying/serving of registered agent cards stored in the registry.
     *
     * @param groupId the group ID of the agent card artifact
     * @param artifactId the artifact ID of the agent card
     * @param version optional version (defaults to latest)
     * @return the Agent Card content
     */
    @GET
    @Path("/agents/{groupId}/{artifactId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getRegisteredAgentCard(
            @PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @QueryParam("version") String version);

    /**
     * Search for registered Agent Cards by various criteria.
     * This enables discovery of agents based on their capabilities and skills.
     *
     * @param name filter by agent name (partial match)
     * @param skill filter by skill ID (can be specified multiple times)
     * @param capability filter by capability (e.g., "streaming:true")
     * @param inputMode filter by input mode (e.g., "text", "image")
     * @param outputMode filter by output mode
     * @param offset pagination offset
     * @param limit pagination limit
     * @return search results containing matching agent cards
     */
    @GET
    @Path("/agents")
    @Produces(MediaType.APPLICATION_JSON)
    AgentSearchResults searchAgents(
            @QueryParam("name") String name,
            @QueryParam("skill") List<String> skills,
            @QueryParam("capability") List<String> capabilities,
            @QueryParam("inputMode") List<String> inputModes,
            @QueryParam("outputMode") List<String> outputModes,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("20") Integer limit);

    /**
     * Returns a specific registered MCP tool definition by group and artifact ID.
     *
     * @param groupId the group ID of the MCP tool artifact
     * @param artifactId the artifact ID of the MCP tool
     * @param version optional version (defaults to latest)
     * @return the MCP tool definition content
     */
    @GET
    @Path("/mcp-tools/{groupId}/{artifactId}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getRegisteredMcpTool(
            @PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @QueryParam("version") String version);

    /**
     * Search for registered MCP tool definitions by various criteria.
     *
     * @param name filter by tool name (partial match)
     * @param parameter filter by input parameter name
     * @param offset pagination offset
     * @param limit pagination limit
     * @return search results containing matching MCP tools
     */
    @GET
    @Path("/mcp-tools")
    @Produces(MediaType.APPLICATION_JSON)
    McpToolSearchResults searchMcpTools(
            @QueryParam("name") String name,
            @QueryParam("parameter") List<String> parameters,
            @QueryParam("offset") @DefaultValue("0") String offset,
            @QueryParam("limit") @DefaultValue("20") String limit);

    /**
     * Returns all registered MCP tools whose {@code inputSchema} can accept the output
     * produced by the given source tool's {@code outputSchema}.
     *
     * <p>Two tools are considered compatible when every property declared in the source
     * tool's {@code outputSchema.properties} is also present in the candidate tool's
     * {@code inputSchema.properties} with the same JSON Schema type. This models the
     * pipeline chaining contract: the candidate tool can consume what the source tool
     * produces.</p>
     *
     * <p>If the source tool has no {@code outputSchema}, an empty result is returned.
     * The source tool itself is never included in the results.</p>
     *
     * @param groupId    the group ID of the source MCP tool artifact
     * @param artifactId the artifact ID of the source MCP tool
     * @param version    optional version expression (defaults to latest)
     * @param offset     pagination offset
     * @param limit      pagination limit
     * @return the compatible MCP tools
     */
    @GET
    @Path("/mcp-tools/{groupId}/{artifactId}/compatible")
    @Produces(MediaType.APPLICATION_JSON)
    McpCompatibleToolsResults findCompatibleTools(
            @PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @QueryParam("version") String version,
            @QueryParam("offset") @DefaultValue("0") Integer offset,
            @QueryParam("limit") @DefaultValue("20") Integer limit);

    /**
     * Returns the JSON Schema for a specific LLM artifact type.
     * This enables IDE autocompletion and validation for PROMPT_TEMPLATE, MODEL_SCHEMA,
     * and MCP_TOOL artifacts.
     *
     * Supported types:
     * - prompt-template (versions: v1)
     * - model-schema (versions: v1)
     * - mcp-tool (versions: v1)
     *
     * @param schemaType the schema type (e.g., "prompt-template", "model-schema", "mcp-tool")
     * @param version the schema version (e.g., "v1")
     * @return the JSON Schema
     */
    @GET
    @Path("/schemas/{schemaType}/{version}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getSchema(
            @PathParam("schemaType") String schemaType,
            @PathParam("version") String version);

    /**
     * Returns the AI Catalog (ai-catalog.io) document for this registry instance, projecting
     * all visible Agent Card and MCP tool artifacts into AI Catalog entries.
     *
     * @return the AI Catalog document
     */
    @GET
    @Path("/ai-catalog.json")
    @Produces(MediaType.APPLICATION_JSON)
    AiCatalog getAiCatalog();

    /**
     * Returns the ARD (Agentic Resource Discovery) manifest document for this registry
     * instance. This is the ARD v0.91 normative discovery path; the payload is identical to
     * {@link #getAiCatalog()}.
     *
     * @return the AI Catalog document
     */
    @GET
    @Path("/ard.json")
    @Produces(MediaType.APPLICATION_JSON)
    AiCatalog getArdManifest();

    /**
     * ARD (Agentic Resource Discovery) search endpoint. Returns AI Catalog entries matching
     * the requested text query and structured filters.
     *
     * @param request the ARD search request
     * @return the ARD search response
     */
    @POST
    @Path("/ard/search")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ArdSearchResponse ardSearch(ArdSearchRequest request);

    /**
     * ARD deterministic agent/tool listing endpoint, with optional filter, ordering, and
     * pagination.
     *
     * @param filter EBNF-ish filter expression (e.g. {@code type=<value>})
     * @param orderBy optional ordering hint (currently a no-op)
     * @param pageSize page size
     * @param pageToken opaque pagination token
     * @return the AI Catalog document containing the (possibly filtered/paginated) entries
     */
    @GET
    @Path("/ard/agents")
    @Produces(MediaType.APPLICATION_JSON)
    AiCatalog ardListAgents(
            @QueryParam("filter") String filter,
            @QueryParam("orderBy") String orderBy,
            @QueryParam("pageSize") @DefaultValue("20") Integer pageSize,
            @QueryParam("pageToken") String pageToken);

    /**
     * ARD facet exploration endpoint.
     *
     * @param request the ARD explore request
     * @return the ARD explore response containing the requested facets
     */
    @POST
    @Path("/ard/explore")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ArdExploreResponse ardExplore(ArdExploreRequest request);
}
