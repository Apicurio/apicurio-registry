package io.apicurio.registry.rest.wellknown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.a2a.A2AConfig;
import io.apicurio.registry.a2a.RegistryAgentCardBuilder;
import io.apicurio.registry.a2a.rest.beans.AgentCapabilities;
import io.apicurio.registry.a2a.rest.beans.AgentCard;
import io.apicurio.registry.a2a.rest.beans.AgentInterface;
import io.apicurio.registry.a2a.rest.beans.AgentSearchFilters;
import io.apicurio.registry.a2a.rest.beans.AgentSearchRequest;
import io.apicurio.registry.a2a.rest.beans.AgentSearchResult;
import io.apicurio.registry.a2a.rest.beans.AgentSearchResults;
import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.mcptools.McpToolsConfig;
import io.apicurio.registry.mcptools.rest.beans.McpToolSearchResult;
import io.apicurio.registry.mcptools.rest.beans.McpToolSearchResults;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.StringUtil;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of the well-known endpoint resource for A2A agents and MCP tools.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 * @see <a href="https://spec.modelcontextprotocol.io/specification/server/tools/">MCP Tools</a>
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class WellKnownResourceImpl implements WellKnownResource {

    @Inject
    A2AConfig a2aConfig;

    @Inject
    McpToolsConfig mcpToolsConfig;

    @Inject
    RegistryAgentCardBuilder agentCardBuilder;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    AdminOverride adminOverride;

    @Inject
    AuthConfig authConfig;

    @Context
    HttpServletRequest request;

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public AgentCard getAgentCard() {
        if (!a2aConfig.isEnabled()) {
            throw new NotFoundException("A2A support is disabled");
        }

        String baseUrl = getBaseUrl();
        return agentCardBuilder.build(baseUrl);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public AgentCard getAgentCardV1() {
        return getAgentCard();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public AgentSearchResults getPublicAgents(Integer offset, Integer limit) {
        if (!a2aConfig.isEnabled() || !a2aConfig.isPublicDiscoveryEnabled()) {
            throw new NotFoundException("Public agent discovery is disabled");
        }

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));
        filters.add(SearchFilter.ofLabel("apicurio.agent.visibility", "public"));

        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc, offset, limit);

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count(results.getCount())
                .agents(agents)
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentSearchResults getEntitledAgents(Integer offset, Integer limit) {
        if (!a2aConfig.isEnabled() || !a2aConfig.isEntitlementsEnabled()) {
            throw new NotFoundException("Agent entitlements endpoint is disabled");
        }

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));

        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc, 0, Integer.MAX_VALUE);

        List<AgentSearchResult> agents = filterByVisibility(results.getArtifacts());

        int total = agents.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(fromIndex + limit, total);
        List<AgentSearchResult> page = agents.subList(fromIndex, toIndex);

        return AgentSearchResults.builder()
                .count((long) total)
                .agents(page)
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentSearchResults searchAgentsAdvanced(AgentSearchRequest request) {
        if (!a2aConfig.isEnabled()) {
            throw new NotFoundException("A2A support is disabled");
        }

        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));

        if (!StringUtil.isEmpty(request.getQuery())) {
            filters.add(SearchFilter.ofName(request.getQuery()));
        }

        AgentSearchFilters f = request.getFilters();
        if (f != null) {
            if (f.getSkills() != null) {
                for (String skill : f.getSkills()) {
                    filters.add(SearchFilter.ofStructure("agent_card:skill:" + skill));
                }
            }
            if (f.getCapabilities() != null) {
                for (Map.Entry<String, Boolean> entry : f.getCapabilities().entrySet()) {
                    SearchFilter filter = SearchFilter.ofStructure(
                            "agent_card:capability:" + entry.getKey());
                    if (!Boolean.TRUE.equals(entry.getValue())) {
                        filter = filter.negated();
                    }
                    filters.add(filter);
                }
            }
            if (f.getLabels() != null) {
                for (Map.Entry<String, String> entry : f.getLabels().entrySet()) {
                    filters.add(SearchFilter.ofLabel(entry.getKey(), entry.getValue()));
                }
            }
            if (f.getInputModes() != null) {
                for (String mode : f.getInputModes()) {
                    filters.add(SearchFilter.ofStructure("agent_card:inputmode:" + mode));
                }
            }
            if (f.getOutputModes() != null) {
                for (String mode : f.getOutputModes()) {
                    filters.add(SearchFilter.ofStructure("agent_card:outputmode:" + mode));
                }
            }
            if (f.getProtocolBindings() != null) {
                for (String binding : f.getProtocolBindings()) {
                    filters.add(SearchFilter.ofStructure("agent_card:protocolbinding:" + binding));
                }
            }
        }

        if (a2aConfig.isEntitlementsEnabled()) {
            ArtifactSearchResultsDto results = storage.searchArtifacts(
                    filters, OrderBy.createdOn, OrderDirection.desc, 0, Integer.MAX_VALUE);

            List<AgentSearchResult> agents = filterByVisibility(results.getArtifacts());

            int total = agents.size();
            int fromIndex = Math.min(request.getOffset(), total);
            int toIndex = Math.min(fromIndex + request.getLimit(), total);
            List<AgentSearchResult> page = agents.subList(fromIndex, toIndex);

            return AgentSearchResults.builder()
                    .count((long) total)
                    .agents(page)
                    .build();
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc,
                request.getOffset(), request.getLimit());

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count(results.getCount())
                .agents(agents)
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getRegisteredAgentCard(String groupId, String artifactId, String version) {
        if (!a2aConfig.isEnabled()) {
            throw new NotFoundException("A2A support is disabled");
        }

        GroupId gid = new GroupId(groupId);
        String rawGroupId = gid.getRawGroupIdWithNull();
        GA ga = new GA(rawGroupId, artifactId);

        try {
            // Resolve version expression (or default to "latest" branch)
            String versionExpression = StringUtil.isEmpty(version) ? "branch=latest" : version;
            GAV gav = VersionExpressionParser.parse(ga, versionExpression,
                    (g, branchId) -> storage.getBranchTip(g, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));

            // Get artifact content
            StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

            // Get metadata to verify artifact type
            ArtifactVersionMetaDataDto metadata = storage.getArtifactVersionMetaData(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

            if (!ArtifactType.AGENT_CARD.equals(metadata.getArtifactType())) {
                throw new NotFoundException("Artifact is not an Agent Card");
            }

            return Response.ok(artifact.getContent().content(), "application/json").build();

        } catch (ArtifactNotFoundException | VersionNotFoundException e) {
            throw new NotFoundException("Agent Card not found: " + groupId + "/" + artifactId);
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentSearchResults searchAgents(String name, List<String> skills, List<String> capabilities,
            List<String> inputModes, List<String> outputModes, Integer offset, Integer limit) {
        if (!a2aConfig.isEnabled()) {
            throw new NotFoundException("A2A support is disabled");
        }

        Set<SearchFilter> filters = new HashSet<>();

        // Always filter by AGENT_CARD artifact type
        filters.add(SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));

        // Filter by name if provided
        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofName(name));
        }

        // Filter by skills (indexed as structured content: agent_card:skill:<id>)
        if (skills != null && !skills.isEmpty()) {
            for (String skill : skills) {
                filters.add(SearchFilter.ofStructure("agent_card:skill:" + skill));
            }
        }

        // Filter by capabilities (indexed as structured content: agent_card:capability:<name>)
        if (capabilities != null && !capabilities.isEmpty()) {
            for (String capability : capabilities) {
                // Parse capability:value format (e.g., "streaming:true")
                String[] parts = capability.split(":", 2);
                String capKey = parts[0];
                String capValue = parts.length > 1 ? parts[1] : "true";
                SearchFilter filter = SearchFilter.ofStructure("agent_card:capability:" + capKey);
                if ("false".equals(capValue)) {
                    filter = filter.negated();
                }
                filters.add(filter);
            }
        }

        // Filter by input modes (indexed as structured content: agent_card:inputmode:<mode>)
        if (inputModes != null && !inputModes.isEmpty()) {
            for (String mode : inputModes) {
                filters.add(SearchFilter.ofStructure("agent_card:inputmode:" + mode));
            }
        }

        // Filter by output modes (indexed as structured content: agent_card:outputmode:<mode>)
        if (outputModes != null && !outputModes.isEmpty()) {
            for (String mode : outputModes) {
                filters.add(SearchFilter.ofStructure("agent_card:outputmode:" + mode));
            }
        }

        // Execute search
        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc, offset, limit);

        // Convert to agent search results
        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count(results.getCount())
                .agents(agents)
                .build();
    }

    /**
     * Converts a searched artifact DTO into an agent search result by fetching and parsing the latest
     * version content to extract skills and capabilities.
     */
    private AgentSearchResult convertToAgentSearchResult(SearchedArtifactDto artifact) {
        List<String> skills = new ArrayList<>();
        List<AgentInterface> supportedInterfaces = new ArrayList<>();
        boolean streaming = false;
        boolean pushNotifications = false;

        // Fetch and parse the latest version content to extract skills and capabilities
        try {
            GA ga = new GA(artifact.getGroupId(), artifact.getArtifactId());
            GAV gav = VersionExpressionParser.parse(ga, "branch=latest",
                    (g, branchId) -> storage.getBranchTip(g, branchId,
                            RetrievalBehavior.SKIP_DISABLED_LATEST));
            StoredArtifactVersionDto stored = storage.getArtifactVersionContent(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(stored.getContent().content());

            // Extract skills
            JsonNode skillsNode = root.path("skills");
            if (skillsNode.isArray()) {
                for (JsonNode skill : skillsNode) {
                    if (skill.has("id") && skill.get("id").isTextual()) {
                        skills.add(skill.get("id").asText());
                    }
                }
            }

            // Extract supportedInterfaces
            JsonNode interfacesNode = root.path("supportedInterfaces");
            if (interfacesNode.isArray()) {
                for (JsonNode iface : interfacesNode) {
                    AgentInterface agentInterface = AgentInterface.builder()
                            .url(iface.has("url") ? iface.get("url").asText() : null)
                            .protocolBinding(iface.has("protocolBinding") ? iface.get("protocolBinding").asText() : null)
                            .protocolVersion(iface.has("protocolVersion") ? iface.get("protocolVersion").asText() : null)
                            .build();
                    supportedInterfaces.add(agentInterface);
                }
            }

            // Extract capabilities
            JsonNode capabilitiesNode = root.path("capabilities");
            if (capabilitiesNode.isObject()) {
                streaming = capabilitiesNode.path("streaming").asBoolean(false);
                pushNotifications = capabilitiesNode.path("pushNotifications").asBoolean(false);
            }
        } catch (Exception e) {
            // If content parsing fails, return result with empty skills/capabilities
        }

        return AgentSearchResult.builder()
                .groupId(artifact.getGroupId())
                .artifactId(artifact.getArtifactId())
                .name(artifact.getName())
                .description(artifact.getDescription())
                .owner(artifact.getOwner())
                .createdOn(artifact.getCreatedOn().getTime())
                .supportedInterfaces(supportedInterfaces)
                .skills(skills)
                .capabilities(AgentCapabilities.builder()
                        .streaming(streaming)
                        .pushNotifications(pushNotifications)
                        .build())
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public Response getRegisteredMcpTool(String groupId, String artifactId, String version) {
        if (!mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("MCP tools support is disabled");
        }

        GroupId gid = new GroupId(groupId);
        String rawGroupId = gid.getRawGroupIdWithNull();
        GA ga = new GA(rawGroupId, artifactId);

        try {
            String versionExpression = StringUtil.isEmpty(version) ? "branch=latest" : version;
            GAV gav = VersionExpressionParser.parse(ga, versionExpression,
                    (g, branchId) -> storage.getBranchTip(g, branchId,
                            RetrievalBehavior.SKIP_DISABLED_LATEST));

            StoredArtifactVersionDto artifact = storage.getArtifactVersionContent(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

            ArtifactVersionMetaDataDto metadata = storage.getArtifactVersionMetaData(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

            if (!ArtifactType.MCP_TOOL.equals(metadata.getArtifactType())) {
                throw new NotFoundException("Artifact is not an MCP tool definition");
            }

            return Response.ok(artifact.getContent().content(), "application/json").build();

        } catch (ArtifactNotFoundException | VersionNotFoundException e) {
            throw new NotFoundException(
                    "MCP tool not found: " + groupId + "/" + artifactId);
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public McpToolSearchResults searchMcpTools(String name, List<String> parameters,
            Integer offset, Integer limit) {
        if (!mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("MCP tools support is disabled");
        }

        Set<SearchFilter> filters = new HashSet<>();

        filters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_TOOL));

        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofName(name));
        }

        if (parameters != null && !parameters.isEmpty()) {
            for (String parameter : parameters) {
                filters.add(SearchFilter.ofStructure("mcp_tool:parameter:" + parameter));
            }
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, offset, limit);

        List<McpToolSearchResult> tools = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            tools.add(convertToMcpToolSearchResult(artifact));
        }

        return McpToolSearchResults.builder().count(results.getCount()).tools(tools).build();
    }

    /**
     * Converts a searched artifact DTO into an MCP tool search result by fetching and parsing
     * the latest version content to extract title and parameters.
     */
    private McpToolSearchResult convertToMcpToolSearchResult(SearchedArtifactDto artifact) {
        String title = null;
        List<String> parameters = new ArrayList<>();

        try {
            GA ga = new GA(artifact.getGroupId(), artifact.getArtifactId());
            GAV gav = VersionExpressionParser.parse(ga, "branch=latest",
                    (g, branchId) -> storage.getBranchTip(g, branchId,
                            RetrievalBehavior.SKIP_DISABLED_LATEST));
            StoredArtifactVersionDto stored = storage.getArtifactVersionContent(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(stored.getContent().content());

            // Extract title
            if (root.has("title") && root.get("title").isTextual()) {
                title = root.get("title").asText();
            }

            // Extract parameter names from inputSchema
            JsonNode inputSchema = root.path("inputSchema");
            if (inputSchema.isObject()) {
                JsonNode properties = inputSchema.path("properties");
                if (properties.isObject()) {
                    properties.fieldNames().forEachRemaining(parameters::add);
                }
            }
        } catch (Exception e) {
            // If content parsing fails, return result with empty metadata
        }

        return McpToolSearchResult.builder()
                .groupId(artifact.getGroupId())
                .artifactId(artifact.getArtifactId())
                .name(artifact.getName())
                .title(title)
                .description(artifact.getDescription())
                .owner(artifact.getOwner())
                .createdOn(artifact.getCreatedOn().getTime())
                .parameters(parameters)
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public Response getSchema(String type, String version) {
        if (!a2aConfig.isEnabled() && !mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("Schema not found: " + type + "/" + version);
        }

        // Validate and normalize the type
        String schemaResourcePath = getSchemaResourcePath(type, version);
        if (schemaResourcePath == null) {
            throw new NotFoundException("Schema not found: " + type + "/" + version);
        }

        try {
            String schemaContent = loadSchemaFromClasspath(schemaResourcePath);
            return Response.ok(schemaContent, "application/schema+json")
                    .header("Content-Disposition", "inline; filename=\"" + type + "-" + version + ".json\"")
                    .header("Cache-Control", "public, max-age=86400")
                    .build();
        } catch (IOException e) {
            throw new NotFoundException("Schema not found: " + type + "/" + version);
        }
    }

    private String getSchemaResourcePath(String type, String version) {
        // Only allow known schema types and versions
        if ("prompt-template".equals(type) && "v1".equals(version)) {
            return "schemas/prompt-template-v1.json";
        } else if ("model-schema".equals(type) && "v1".equals(version)) {
            return "schemas/model-schema-v1.json";
        } else if ("mcp-tool".equals(type) && "v1".equals(version)) {
            return "schemas/mcp-tool-v1.json";
        }
        return null;
    }

    private String loadSchemaFromClasspath(String resourcePath) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<AgentSearchResult> filterByVisibility(List<SearchedArtifactDto> artifacts) {
        boolean authEnabled = authConfig.isOidcAuthEnabled()
                || authConfig.isBasicAuthEnabled();

        if (!authEnabled) {
            List<AgentSearchResult> result = new ArrayList<>();
            for (SearchedArtifactDto artifact : artifacts) {
                result.add(convertToAgentSearchResult(artifact));
            }
            return result;
        }

        boolean isAuthenticated = securityIdentity != null && !securityIdentity.isAnonymous();
        boolean isAdmin = adminOverride.isAdmin();
        String currentUser = isAuthenticated
                ? securityIdentity.getPrincipal().getName() : null;

        List<AgentSearchResult> result = new ArrayList<>();
        for (SearchedArtifactDto artifact : artifacts) {
            String visibility = getVisibilityLabel(artifact.getLabels());
            if ("public".equals(visibility)) {
                result.add(convertToAgentSearchResult(artifact));
            } else if (!isAuthenticated) {
                continue;
            } else if ("private".equals(visibility)) {
                if (isAdmin || currentUser.equals(artifact.getOwner())) {
                    result.add(convertToAgentSearchResult(artifact));
                }
            } else {
                result.add(convertToAgentSearchResult(artifact));
            }
        }
        return result;
    }

    private String getVisibilityLabel(Map<String, String> labels) {
        if (labels == null) {
            return null;
        }
        return labels.get("apicurio.agent.visibility");
    }

    private String getBaseUrl() {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        // Check for X-Forwarded headers (common in load balancers/proxies)
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String forwardedHost = request.getHeader("X-Forwarded-Host");

        if (!StringUtil.isEmpty(forwardedProto)) {
            scheme = forwardedProto;
        }
        if (!StringUtil.isEmpty(forwardedHost)) {
            host = forwardedHost;
            port = -1; // Assume standard port when using forwarded host
        }

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(host);

        if (port > 0 && port != 80 && port != 443) {
            url.append(":").append(port);
        }

        return url.toString();
    }
}
