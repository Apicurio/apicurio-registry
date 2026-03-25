package io.apicurio.registry.rest.wellknown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.a2a.A2AConfig;
import io.apicurio.registry.a2a.RegistryAgentCardBuilder;
import io.apicurio.registry.a2a.rest.beans.AgentCapabilities;
import io.apicurio.registry.a2a.rest.beans.AgentCard;
import io.apicurio.registry.a2a.rest.beans.AgentSearchResult;
import io.apicurio.registry.a2a.rest.beans.AgentSearchResults;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
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
import java.util.Set;

/**
 * Implementation of the A2A well-known endpoint resource.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class WellKnownResourceImpl implements WellKnownResource {

    @Inject
    A2AConfig a2aConfig;

    @Inject
    RegistryAgentCardBuilder agentCardBuilder;

    @Inject
    @Current
    RegistryStorage storage;

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

            if (!ArtifactType.AGENT_CARD.matches(metadata.getArtifactType())) {
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
        filters.add(SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD.value()));

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
                .skills(skills)
                .capabilities(AgentCapabilities.builder()
                        .streaming(streaming)
                        .pushNotifications(pushNotifications)
                        .build())
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public Response getSchema(String type, String version) {
        if (!a2aConfig.isEnabled()) {
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
