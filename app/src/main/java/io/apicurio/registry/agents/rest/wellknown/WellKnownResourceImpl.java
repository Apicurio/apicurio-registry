package io.apicurio.registry.agents.rest.wellknown;

import io.apicurio.registry.agents.mcptools.rest.beans.McpCompatibleToolsResults;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v3.beans.AgentCard;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.rest.v3.beans.AiCatalog;
import io.apicurio.registry.rest.v3.beans.ArdAgentsResponse;
import io.apicurio.registry.rest.v3.beans.ArdExploreRequest;
import io.apicurio.registry.rest.v3.beans.ArdExploreResponse;
import io.apicurio.registry.rest.v3.beans.ArdSearchRequest;
import io.apicurio.registry.rest.v3.beans.ArdSearchResponse;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResults;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * JAX-RS facade for the {@code /.well-known} discovery endpoints. Authorization is declared
 * here; the work is done by the discovery services in this package.
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class WellKnownResourceImpl implements WellKnownResource {

    @Inject
    AgentCardDiscovery agentCardDiscovery;

    @Inject
    AgentSchemaProvider agentSchemaProvider;

    @Inject
    AiCatalogDiscovery aiCatalogDiscovery;

    @Inject
    McpToolDiscovery mcpToolDiscovery;

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public AgentCard getAgentCard() {
        return agentCardDiscovery.getAgentCard();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public AgentCard getAgentCardForOrchestrate() {
        return agentCardDiscovery.getAgentCardForOrchestrate();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getRegisteredAgentCard(String groupId, String artifactId, String version) {
        return agentCardDiscovery.getRegisteredAgentCard(groupId, artifactId, version);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public AgentSearchResults searchAgents(String name, List<String> skills, List<String> capabilities,
            List<String> inputModes, List<String> outputModes, Integer offset, Integer limit) {
        return agentCardDiscovery.searchAgents(name, skills, capabilities, inputModes, outputModes, offset, limit);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getRegisteredMcpTool(String groupId, String artifactId, String version) {
        return mcpToolDiscovery.getRegisteredMcpTool(groupId, artifactId, version);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public McpToolSearchResults searchMcpTools(String name, List<String> parameters,
            String offset, String limit) {
        return mcpToolDiscovery.searchMcpTools(name, parameters, offset, limit);
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public McpCompatibleToolsResults findCompatibleTools(String groupId, String artifactId,
            String version, Integer offset, Integer limit) {
        return mcpToolDiscovery.findCompatibleTools(groupId, artifactId, version, offset, limit);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public Response getSchema(String schemaType, String version) {
        return agentSchemaProvider.getSchema(schemaType, version);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AiCatalog getAiCatalog() {
        return aiCatalogDiscovery.getAiCatalog();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AiCatalog getArdManifest() {
        return aiCatalogDiscovery.getArdManifest();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArdSearchResponse ardSearch(ArdSearchRequest request) {
        return aiCatalogDiscovery.ardSearch(request);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArdAgentsResponse ardListAgents(String filter, String orderBy, Integer pageSize, String pageToken) {
        return aiCatalogDiscovery.ardListAgents(filter, orderBy, pageSize, pageToken);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArdExploreResponse ardExplore(ArdExploreRequest request) {
        return aiCatalogDiscovery.ardExplore(request);
    }
}
