package io.apicurio.registry.rest.wellknown;

import io.apicurio.registry.a2a.rest.beans.AgentCard;
import io.apicurio.registry.a2a.rest.beans.AgentSearchResults;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * JAX-RS resource for A2A protocol well-known endpoints.
 *
 * Per the A2A protocol specification, agents publish their Agent Card at
 * /.well-known/agent.json for discovery purposes.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
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
}
