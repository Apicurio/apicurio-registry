package io.apicurio.registry.rest.wellknown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.apicurio.registry.a2a.A2AConfig;
import io.apicurio.registry.a2a.A2AConstants;
import io.apicurio.registry.a2a.RegistryAgentCardBuilder;
import io.apicurio.registry.aicatalog.AiCatalogConfig;
import io.apicurio.registry.aicatalog.AiCatalogConstants;
import io.apicurio.registry.ard.ArdConfig;
import io.apicurio.registry.rest.v3.beans.AgentCapabilities;
import io.apicurio.registry.rest.v3.beans.AgentCard;
import io.apicurio.registry.rest.v3.beans.AgentInterface;
import io.apicurio.registry.rest.v3.beans.AgentSearchFilters;
import io.apicurio.registry.rest.v3.beans.AgentSearchRequest;
import io.apicurio.registry.rest.v3.beans.AgentSearchResult;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.rest.v3.beans.AiCatalog;
import io.apicurio.registry.rest.v3.beans.AiCatalogEntry;
import io.apicurio.registry.rest.v3.beans.AiCatalogHost;
import io.apicurio.registry.rest.v3.beans.ArdExploreRequest;
import io.apicurio.registry.rest.v3.beans.ArdExploreResponse;
import io.apicurio.registry.rest.v3.beans.ArdFacet;
import io.apicurio.registry.rest.v3.beans.ArdFacetBucket;
import io.apicurio.registry.rest.v3.beans.ArdFacetRequest;
import io.apicurio.registry.rest.v3.beans.ArdFacets;
import io.apicurio.registry.rest.v3.beans.ArdFilter;
import io.apicurio.registry.rest.v3.beans.ArdSearchQuery;
import io.apicurio.registry.rest.v3.beans.ArdSearchRequest;
import io.apicurio.registry.rest.v3.beans.ArdSearchResponse;
import io.apicurio.registry.rest.v3.beans.ArdSearchResultEntry;
import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.mcptools.McpToolsConfig;
import io.apicurio.registry.mcptools.rest.beans.McpCompatibleToolsResults;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResult;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResults;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

    private static final Logger log = LoggerFactory.getLogger(WellKnownResourceImpl.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final int MAX_VISIBILITY_FILTER_RESULTS = 10000;
    private static final String PROPERTIES_FIELD = "properties";

    /**
     * Maximum number of MCP-tool candidates evaluated per compatible-tools request.
     * Kept well below {@link #MAX_VISIBILITY_FILTER_RESULTS} because each candidate
     * requires a storage round-trip; raising this limit directly raises per-request cost.
     */
    private static final int MAX_COMPATIBLE_CANDIDATE_SCAN = 500;

    /**
     * Supported ARD {@code query.filter} / {@code filter=} expression keys. Any other key
     * results in a 400 response.
     */
    private static final Set<String> SUPPORTED_ARD_FILTER_KEYS = Set.of(
            "type", "tags", "capabilities", "publisher");

    /**
     * Supported ARD {@code POST /explore} facet field names. Any other field results in a
     * 400 response.
     */
    private static final Set<String> SUPPORTED_ARD_FACET_FIELDS = Set.of("type", "publisher");

    /**
     * The set of media types this registry actually emits in AI Catalog / ARD entries. Used
     * to validate {@code type} filter values.
     */
    private static final Set<String> RECOGNIZED_AI_CATALOG_TYPES = Set.of(
            AiCatalogConstants.MEDIA_TYPE_AGENT_CARD, AiCatalogConstants.MEDIA_TYPE_MCP_SERVER_CARD);

    private static final String ARD_FILTER_CLAUSE_SEPARATOR = " AND ";

    private static final int ARD_SEARCH_DEFAULT_PAGE_SIZE = 10;
    private static final int ARD_SEARCH_MAX_PAGE_SIZE = 100;
    private static final int ARD_AGENTS_DEFAULT_PAGE_SIZE = 20;
    private static final int ARD_AGENTS_MAX_PAGE_SIZE = 500;

    @Inject
    A2AConfig a2aConfig;

    @Inject
    McpToolsConfig mcpToolsConfig;

    @Inject
    AiCatalogConfig aiCatalogConfig;

    @Inject
    ArdConfig ardConfig;

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
    public AgentCard getAgentCardForOrchestrate() {
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

        // Agent Cards without an explicit visibility label fall back to the configured default
        // visibility (see resolveVisibility), so when that default is "public" they belong in
        // these results even though no label filter can match them. Resolve visibility in memory
        // in that case, as getEntitledAgents does. Otherwise keep the cheaper indexed label query.
        if (A2AConstants.VISIBILITY_PUBLIC
                .equals(a2aConfig.getDefaultVisibility().toLowerCase(Locale.ROOT))) {
            return publicAgentsByResolvedVisibility(filters, offset, limit);
        }

        filters.add(SearchFilter.ofLabel(A2AConstants.LABEL_AGENT_VISIBILITY, A2AConstants.VISIBILITY_PUBLIC));

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 500));

        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc, safeOffset, safeLimit, false);

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count((int) results.getCount())
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
                filters, OrderBy.createdOn, OrderDirection.desc, 0, MAX_VISIBILITY_FILTER_RESULTS, false);
        warnIfTruncated(results);

        // Filter by visibility on DTOs first (cheap), then paginate, then convert (expensive)
        List<SearchedArtifactDto> visible = filterDtosByVisibility(results.getArtifacts());

        int total = visible.size();
        int safeOffset = Math.max(0, Math.min(offset, total));
        int safeLimit = Math.max(0, Math.min(limit, 500));
        int toIndex = Math.min(safeOffset + safeLimit, total);
        List<SearchedArtifactDto> page = visible.subList(safeOffset, toIndex);

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : page) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count(total)
                .agents(agents)
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

        // Query matches artifact metadata name and artifactId (not Agent Card JSON content).
        // Full-text content search is tracked in #7230.
        if (!StringUtil.isEmpty(request.getQuery())) {
            filters.add(SearchFilter.ofPartialName(request.getQuery()));
        }

        AgentSearchFilters f = request.getFilters();
        if (f != null) {
            if (f.getSkills() != null) {
                for (String skill : f.getSkills()) {
                    filters.add(SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_SKILL + skill));
                }
            }
            if (f.getCapabilities() != null) {
                for (Map.Entry<String, Object> entry : f.getCapabilities().getAdditionalProperties().entrySet()) {
                    SearchFilter filter = SearchFilter.ofStructure(
                            A2AConstants.PREFIX_AGENT_CARD_CAPABILITY + entry.getKey());
                    if (!Boolean.TRUE.equals(entry.getValue())) {
                        filter = filter.negated();
                    }
                    filters.add(filter);
                }
            }
            if (f.getLabels() != null) {
                for (Map.Entry<String, Object> entry : f.getLabels().getAdditionalProperties().entrySet()) {
                    filters.add(SearchFilter.ofLabel(entry.getKey(), String.valueOf(entry.getValue())));
                }
            }
            if (f.getInputModes() != null) {
                for (String mode : f.getInputModes()) {
                    filters.add(SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_INPUT_MODE + mode));
                }
            }
            if (f.getOutputModes() != null) {
                for (String mode : f.getOutputModes()) {
                    filters.add(SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_OUTPUT_MODE + mode));
                }
            }
            if (f.getProtocolBindings() != null) {
                for (String binding : f.getProtocolBindings()) {
                    filters.add(SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_PROTOCOL_BINDING + binding));
                }
            }
        }

        int safeOffset = Math.max(0, request.getOffset() != null ? request.getOffset() : 0);
        int safeLimit = Math.max(1, Math.min(request.getLimit() != null ? request.getLimit() : 20, 500));

        if (a2aConfig.isEntitlementsEnabled()) {
            ArtifactSearchResultsDto results = storage.searchArtifacts(
                    filters, OrderBy.createdOn, OrderDirection.desc, 0, MAX_VISIBILITY_FILTER_RESULTS, false);
            warnIfTruncated(results);

            // Filter by visibility on DTOs first (cheap), then paginate, then convert (expensive)
            List<SearchedArtifactDto> visible = filterDtosByVisibility(results.getArtifacts());

            int total = visible.size();
            int fromIndex = Math.min(safeOffset, total);
            int toIndex = Math.min(fromIndex + safeLimit, total);
            List<SearchedArtifactDto> page = visible.subList(fromIndex, toIndex);

            List<AgentSearchResult> agents = new ArrayList<>();
            for (SearchedArtifactDto artifact : page) {
                agents.add(convertToAgentSearchResult(artifact));
            }

            return AgentSearchResults.builder()
                    .count(total)
                    .agents(agents)
                    .build();
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc, safeOffset, safeLimit, false);

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count((int) results.getCount())
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

        // Filter by name if provided. The name filter is documented as a partial match, so
        // wrap the value in wildcards unless the caller supplied their own.
        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofPartialName(name));
        }

        // Filter by skills (indexed as structured content: agent_card:skill:<id>)
        if (skills != null && !skills.isEmpty()) {
            for (String skill : skills) {
                filters.add(SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_SKILL + skill));
            }
        }

        // Filter by capabilities (indexed as structured content: agent_card:capability:<name>)
        if (capabilities != null && !capabilities.isEmpty()) {
            for (String capability : capabilities) {
                // Parse capability:value format (e.g., "streaming:true")
                String[] parts = capability.split(":", 2);
                String capKey = parts[0];
                String capValue = parts.length > 1 ? parts[1] : "true";
                SearchFilter filter = SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_CAPABILITY + capKey);
                if ("false".equals(capValue)) {
                    filter = filter.negated();
                }
                filters.add(filter);
            }
        }

        // Filter by input modes (indexed as structured content: agent_card:inputmode:<mode>)
        if (inputModes != null && !inputModes.isEmpty()) {
            for (String mode : inputModes) {
                filters.add(SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_INPUT_MODE + mode));
            }
        }

        // Filter by output modes (indexed as structured content: agent_card:outputmode:<mode>)
        if (outputModes != null && !outputModes.isEmpty()) {
            for (String mode : outputModes) {
                filters.add(SearchFilter.ofStructure(A2AConstants.PREFIX_AGENT_CARD_OUTPUT_MODE + mode));
            }
        }

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 500));

        if (isAuthEnabled() && a2aConfig.isEntitlementsEnabled()) {
            ArtifactSearchResultsDto results = storage.searchArtifacts(
                    filters, OrderBy.createdOn, OrderDirection.desc,
                    0, MAX_VISIBILITY_FILTER_RESULTS, false);
            warnIfTruncated(results);

            List<SearchedArtifactDto> visible = filterDtosByVisibility(results.getArtifacts());

            int total = visible.size();
            int fromIndex = Math.min(safeOffset, total);
            int toIndex = Math.min(fromIndex + safeLimit, total);
            List<SearchedArtifactDto> page = visible.subList(fromIndex, toIndex);

            List<AgentSearchResult> agents = new ArrayList<>();
            for (SearchedArtifactDto artifact : page) {
                agents.add(convertToAgentSearchResult(artifact));
            }

            return AgentSearchResults.builder()
                    .count(total)
                    .agents(agents)
                    .build();
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc, safeOffset, safeLimit, false);

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count((int) results.getCount())
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

            JsonNode root = mapper.readTree(stored.getContent().content());

            // Extract skills
            skills.addAll(extractAgentCardSkillIds(root));

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
            log.warn("Failed to parse Agent Card content for {}/{}: {}",
                    artifact.getGroupId(), artifact.getArtifactId(), e.getMessage());
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

    /**
     * Extracts the {@code id} of every entry in an Agent Card's {@code skills[]} array.
     * Shared by {@link #convertToAgentSearchResult(SearchedArtifactDto)} and the AI Catalog /
     * ARD entry-building helpers below.
     */
    private List<String> extractAgentCardSkillIds(JsonNode root) {
        List<String> skillIds = new ArrayList<>();
        JsonNode skillsNode = root.path("skills");
        if (skillsNode.isArray()) {
            for (JsonNode skill : skillsNode) {
                if (skill.has("id") && skill.get("id").isTextual()) {
                    skillIds.add(skill.get("id").asText());
                }
            }
        }
        return skillIds;
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
            String offset, String limit) {
        if (!mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("MCP tools support is disabled");
        }

        int safeOffset = Math.max(0, parsePaginationParam(offset, "offset", 0));
        int safeLimit = Math.max(1, Math.min(parsePaginationParam(limit, "limit", 20), 500));

        Set<SearchFilter> filters = new HashSet<>();

        filters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_TOOL));

        // The name filter is documented as a partial match, so wrap the value in wildcards
        // unless the caller supplied their own.
        if (!StringUtil.isEmpty(name)) {
            filters.add(SearchFilter.ofPartialName(name));
        }

        if (parameters != null && !parameters.isEmpty()) {
            // Parameter filtering is performed after artifact search by inspecting tool.getParameters()
            ArtifactSearchResultsDto results = storage.searchArtifacts(filters, OrderBy.createdOn,
                    OrderDirection.desc, 0, MAX_VISIBILITY_FILTER_RESULTS, false);

            List<McpToolSearchResult> matchingTools = new ArrayList<>();
            for (SearchedArtifactDto artifact : results.getArtifacts()) {
                McpToolSearchResult tool = convertToMcpToolSearchResult(artifact);
                if (tool.getParameters() != null && tool.getParameters().containsAll(parameters)) {
                    matchingTools.add(tool);
                }
            }

            int total = matchingTools.size();
            int fromIndex = Math.min(safeOffset, total);
            int toIndex = Math.min(fromIndex + safeLimit, total);
            List<McpToolSearchResult> page = matchingTools.subList(fromIndex, toIndex);

            return McpToolSearchResults.builder().count(total).tools(page).build();
        }

        ArtifactSearchResultsDto results = storage.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, safeOffset, safeLimit, false);

        List<McpToolSearchResult> tools = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            tools.add(convertToMcpToolSearchResult(artifact));
        }

        return McpToolSearchResults.builder().count((int) results.getCount()).tools(tools).build();
    }

    private int parsePaginationParam(String value, String name, int defaultValue) {
        if (StringUtil.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid " + name + ": must be an integer");
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.GroupAndArtifact, level = AuthorizedLevel.Read)
    public McpCompatibleToolsResults findCompatibleTools(String groupId, String artifactId,
            String version, Integer offset, Integer limit) {
        if (!mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("MCP tools support is disabled");
        }

        StoredArtifactVersionDto sourceArtifact = fetchMcpToolArtifact(groupId, artifactId, version);
        Map<String, String> sourceOutputProps = extractOutputProperties(sourceArtifact);

        if (sourceOutputProps.isEmpty()) {
            return McpCompatibleToolsResults.builder().count(0).tools(Collections.emptyList()).build();
        }

        String rawGroupId = new GroupId(groupId).getRawGroupIdWithNull();
        List<McpToolSearchResult> compatibleTools = findCompatibleCandidates(rawGroupId, artifactId, sourceOutputProps);

        return buildPaginatedCompatibleResults(compatibleTools, offset, limit);
    }

    private StoredArtifactVersionDto fetchMcpToolArtifact(String groupId, String artifactId, String version) {
        GroupId gid = new GroupId(groupId);
        String rawGroupId = gid.getRawGroupIdWithNull();
        GA ga = new GA(rawGroupId, artifactId);

        try {
            String versionExpression = StringUtil.isEmpty(version) ? "branch=latest" : version;
            GAV gav = VersionExpressionParser.parse(ga, versionExpression,
                    (g, branchId) -> storage.getBranchTip(g, branchId,
                            RetrievalBehavior.SKIP_DISABLED_LATEST));

            ArtifactVersionMetaDataDto metadata = storage.getArtifactVersionMetaData(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

            if (!ArtifactType.MCP_TOOL.equals(metadata.getArtifactType())) {
                throw new NotFoundException("Artifact is not an MCP tool definition");
            }

            return storage.getArtifactVersionContent(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());

        } catch (ArtifactNotFoundException | VersionNotFoundException e) {
            throw new NotFoundException("MCP tool not found: " + groupId + "/" + artifactId);
        }
    }

    private Map<String, String> extractOutputProperties(StoredArtifactVersionDto sourceArtifact) {
        Map<String, String> sourceOutputProps = new HashMap<>();
        try {
            JsonNode sourceRoot = mapper.readTree(sourceArtifact.getContent().content());
            JsonNode outputSchema = sourceRoot.path("outputSchema");
            if (outputSchema.isObject()) {
                JsonNode properties = outputSchema.path(PROPERTIES_FIELD);
                if (properties.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        sourceOutputProps.put(field.getKey(), extractJsonSchemaType(field.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse source MCP tool outputSchema: {}", e.getMessage());
        }
        return sourceOutputProps;
    }

    /**
     * Extracts the scalar JSON Schema type string from a property node.
     *
     * <p><b>Lenient by design:</b> Only the simple string form {@code {"type": "string"}} is
     * matched.  Array forms such as {@code {"type": ["string","null"]}} or schema composition
     * keywords ({@code oneOf}, {@code $ref}) return {@code null}, which causes
     * {@link #candidateAcceptsAllProperties} to skip the type comparison entirely and treat
     * the property as compatible.  This is intentional — MCP tool schemas are frequently
     * nullable or polymorphic, and a strict rejection would produce false-negatives.  Callers
     * that require strict type enforcement should perform their own JSON Schema validation.
     */
    private String extractJsonSchemaType(JsonNode node) {
        return node.has("type") && node.get("type").isTextual()
                ? node.get("type").asText() : null;
    }

    /**
     * Scans at most {@link #MAX_COMPATIBLE_CANDIDATE_SCAN} MCP tools and returns those whose
     * {@code inputSchema} accepts every property produced by the source tool's {@code outputSchema}.
     *
     * <p><b>Authorization note:</b> candidate identity and metadata are exposed at the same
     * read-level scope as {@code searchMcpTools}, which also returns all MCP tools visible
     * to the caller via {@link AuthorizedLevel#Read}.  No per-artifact visibility label is
     * applied because MCP tools (unlike A2A agents) do not carry an
     * {@code apicurio.agent.visibility} label; the caller's read-level authorization is the
     * sole gate for both endpoints.
     */
    private List<McpToolSearchResult> findCompatibleCandidates(String rawGroupId, String sourceArtifactId,
            Map<String, String> sourceOutputProps) {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_TOOL));

        ArtifactSearchResultsDto candidateResults = storage.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 0, MAX_COMPATIBLE_CANDIDATE_SCAN, false);

        if (candidateResults.getCount() >= MAX_COMPATIBLE_CANDIDATE_SCAN) {
            log.warn("Compatible-tools candidate scan reached the cap of {}; results beyond this"
                    + " limit are not evaluated. Consider raising MAX_COMPATIBLE_CANDIDATE_SCAN"
                    + " or implementing storage-side filtering.", MAX_COMPATIBLE_CANDIDATE_SCAN);
        }

        List<McpToolSearchResult> compatibleTools = new ArrayList<>();
        for (SearchedArtifactDto candidate : candidateResults.getArtifacts()) {
            Optional<JsonNode> compatibleRoot = tryGetCompatibleCandidateRoot(
                    candidate, rawGroupId, sourceArtifactId, sourceOutputProps);
            compatibleRoot.ifPresent(root ->
                    compatibleTools.add(convertToMcpToolSearchResultFromContent(candidate, root)));
        }
        return compatibleTools;
    }

    /**
     * Fetches and parses the candidate's latest content exactly once, checks whether its
     * {@code inputSchema} accepts all required output properties, and — if compatible —
     * returns the already-parsed {@link JsonNode} so the caller can reuse it for result
     * conversion without a second storage round-trip.
     *
     * @return the candidate's parsed content root when compatible; {@link Optional#empty()} otherwise
     */
    private Optional<JsonNode> tryGetCompatibleCandidateRoot(SearchedArtifactDto candidate,
            String rawGroupId, String sourceArtifactId, Map<String, String> sourceOutputProps) {
        if (sourceArtifactId.equals(candidate.getArtifactId())
                && isSameGroup(rawGroupId, candidate.getGroupId())) {
            return Optional.empty();
        }

        try {
            GA candidateGa = new GA(candidate.getGroupId(), candidate.getArtifactId());
            GAV candidateGav = VersionExpressionParser.parse(candidateGa, "branch=latest",
                    (g, branchId) -> storage.getBranchTip(g, branchId,
                            RetrievalBehavior.SKIP_DISABLED_LATEST));
            StoredArtifactVersionDto candidateStored = storage.getArtifactVersionContent(
                    candidateGav.getRawGroupIdWithNull(), candidateGav.getRawArtifactId(),
                    candidateGav.getRawVersionId());

            JsonNode candidateRoot = mapper.readTree(candidateStored.getContent().content());
            JsonNode candidateInputSchema = candidateRoot.path("inputSchema");
            if (candidateInputSchema.isObject()) {
                JsonNode candidateProps = candidateInputSchema.path(PROPERTIES_FIELD);
                if (candidateProps.isObject()
                        && candidateAcceptsAllProperties(candidateProps, sourceOutputProps)) {
                    return Optional.of(candidateRoot);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to evaluate compatibility for candidate MCP tool {}/{}: {}",
                    candidate.getGroupId(), candidate.getArtifactId(), e.getMessage());
        }
        return Optional.empty();
    }

    private boolean isSameGroup(String sourceGroupId, String candidateGroupId) {
        return (sourceGroupId == null && candidateGroupId == null)
                || (sourceGroupId != null && sourceGroupId.equals(candidateGroupId));
    }

    private boolean candidateAcceptsAllProperties(JsonNode candidateProps, Map<String, String> sourceOutputProps) {
        for (Map.Entry<String, String> entry : sourceOutputProps.entrySet()) {
            String reqProp = entry.getKey();
            String reqType = entry.getValue();

            if (!candidateProps.has(reqProp)) {
                return false;
            }
            if (reqType != null) {
                String candType = extractJsonSchemaType(candidateProps.get(reqProp));
                if (candType != null && !reqType.equals(candType)) {
                    return false;
                }
            }
        }
        return true;
    }

    private McpCompatibleToolsResults buildPaginatedCompatibleResults(List<McpToolSearchResult> compatibleTools,
            Integer offset, Integer limit) {
        int total = compatibleTools.size();
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        int fromIndex = Math.min(safeOffset, total);
        int toIndex = Math.min(fromIndex + safeLimit, total);
        List<McpToolSearchResult> page = compatibleTools.subList(fromIndex, toIndex);

        return McpCompatibleToolsResults.builder().count(total).tools(page).build();
    }

    /**
     * Converts a searched artifact DTO into an MCP tool search result by fetching and parsing
     * the latest version content to extract title and parameters.
     *
     * <p>Use {@link #convertToMcpToolSearchResultFromContent(SearchedArtifactDto, JsonNode)}
     * when the content has already been parsed (e.g. during compatible-tools scanning) to
     * avoid an extra storage round-trip.
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

            JsonNode root = mapper.readTree(stored.getContent().content());
            return convertToMcpToolSearchResultFromContent(artifact, root);
        } catch (Exception e) {
            log.warn("Failed to parse MCP tool content for {}/{}: {}",
                    artifact.getGroupId(), artifact.getArtifactId(), e.getMessage());
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

    /**
     * Builds an {@link McpToolSearchResult} from a {@link SearchedArtifactDto} and an
     * already-parsed content root, avoiding an extra storage fetch.
     *
     * <p>Called from {@link #findCompatibleCandidates} so that each compatible candidate
     * is converted using the {@link JsonNode} obtained during the compatibility check,
     * keeping the per-candidate storage cost to a single round-trip.
     */
    private McpToolSearchResult convertToMcpToolSearchResultFromContent(
            SearchedArtifactDto artifact, JsonNode root) {
        String title = null;
        List<String> parameters = new ArrayList<>();

        // Extract title
        if (root.has("title") && root.get("title").isTextual()) {
            title = root.get("title").asText();
        }

        // Extract parameter names from inputSchema
        JsonNode inputSchema = root.path("inputSchema");
        if (inputSchema.isObject()) {
            JsonNode properties = inputSchema.path(PROPERTIES_FIELD);
            if (properties.isObject()) {
                properties.fieldNames().forEachRemaining(parameters::add);
            }
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
    public Response getSchema(String schemaType, String version) {
        if (!a2aConfig.isEnabled() && !mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("Schema not found: " + schemaType + "/" + version);
        }

        // Validate and normalize the type
        String schemaResourcePath = getSchemaResourcePath(schemaType, version);
        if (schemaResourcePath == null) {
            throw new NotFoundException("Schema not found: " + schemaType + "/" + version);
        }

        try {
            String schemaContent = loadSchemaFromClasspath(schemaResourcePath);
            return Response.ok(schemaContent, "application/schema+json")
                    .header("Content-Disposition", "inline; filename=\"" + schemaType + "-" + version + ".json\"")
                    .header("Cache-Control", "public, max-age=86400")
                    .build();
        } catch (IOException e) {
            throw new NotFoundException("Schema not found: " + schemaType + "/" + version);
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AiCatalog getAiCatalog() {
        if (!aiCatalogConfig.isEnabled()) {
            throw new NotFoundException("AI Catalog support is disabled");
        }

        String baseUrl = getBaseUrl();
        String publisherDomain = resolvePublisherDomain();

        List<AiCatalogEntry> entries = new ArrayList<>();
        if (ardConfig.isEnabled()) {
            // Advertise this registry's own ARD search API so crawlers that ingest this
            // catalog can discover /ard/search without prior configuration (ARD spec §5.3).
            entries.add(buildSelfDescribingRegistryEntry(baseUrl, publisherDomain));
        }
        for (AiCatalogCandidate candidate : collectAiCatalogCandidates(baseUrl, publisherDomain, null)) {
            entries.add(candidate.entry);
        }

        return buildAiCatalog(publisherDomain, entries);
    }

    /**
     * Builds the self-describing catalog entry that advertises this registry's own ARD search
     * API. Per the ARD specification (&sect;5.3), a conforming client resolves a registry's
     * search base URL by locating a catalog entry whose {@code type} is
     * {@code application/ai-registry+json}; without this entry, a crawler that only ingests
     * {@code /.well-known/ai-catalog.json} has no way to learn that {@code /ard/search} exists.
     */
    private AiCatalogEntry buildSelfDescribingRegistryEntry(String baseUrl, String publisherDomain) {
        return AiCatalogEntry.builder()
                .identifier(buildAirIdentifier(publisherDomain, "system",
                        AiCatalogConstants.REGISTRY_SELF_ENTRY_NAME))
                .displayName(aiCatalogConfig.getHostName())
                .type(AiCatalogConstants.MEDIA_TYPE_AI_REGISTRY)
                .url(baseUrl + "/.well-known/ard/search")
                .description("ARD search API for this registry.")
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArdSearchResponse ardSearch(ArdSearchRequest request) {
        if (!ardConfig.isEnabled()) {
            throw new NotFoundException("ARD support is disabled");
        }

        ArdSearchQuery query = request == null ? null : request.getQuery();
        if (query == null || StringUtil.isEmpty(query.getText())) {
            throw new BadRequestException("ARD search requires a non-empty 'query.text'");
        }

        // "federation" is accepted for forward compatibility with ARD clients, but only
        // federation:none semantics are implemented (the registry always returns its own
        // results; see ADR-0004).
        Map<String, List<String>> filters = parseArdFilter(query.getFilter());

        String baseUrl = getBaseUrl();
        String publisherDomain = resolvePublisherDomain();

        List<AiCatalogCandidate> matched = new ArrayList<>();
        for (AiCatalogCandidate candidate : collectAiCatalogCandidates(baseUrl, publisherDomain, query.getText())) {
            if (matchesFilters(candidate, filters)) {
                matched.add(candidate);
            }
        }

        int total = matched.size();
        int pageSize = clamp(request.getPageSize() != null ? request.getPageSize() : ARD_SEARCH_DEFAULT_PAGE_SIZE,
                1, ARD_SEARCH_MAX_PAGE_SIZE);
        int offset = decodePageToken(request.getPageToken());
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<ArdSearchResultEntry> results = new ArrayList<>();
        for (AiCatalogCandidate candidate : matched.subList(fromIndex, toIndex)) {
            results.add(toArdSearchResultEntry(candidate.entry, baseUrl));
        }

        String nextPageToken = toIndex < total ? encodePageToken(toIndex) : null;

        return ArdSearchResponse.builder()
                .results(results)
                .pageToken(nextPageToken)
                .build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AiCatalog ardListAgents(String filter, String orderBy, Integer pageSize, String pageToken) {
        if (!ardConfig.isEnabled()) {
            throw new NotFoundException("ARD support is disabled");
        }

        // "orderBy" is currently a no-op: entries are already produced in a deterministic
        // (createdOn desc) order by the underlying storage query. The parameter is accepted
        // so ARD clients that always send it are not rejected.
        Map<String, List<String>> filters = parseArdAgentsFilter(filter);

        String baseUrl = getBaseUrl();
        String publisherDomain = resolvePublisherDomain();

        List<AiCatalogCandidate> matched = new ArrayList<>();
        for (AiCatalogCandidate candidate : collectAiCatalogCandidates(baseUrl, publisherDomain, null)) {
            if (matchesFilters(candidate, filters)) {
                matched.add(candidate);
            }
        }

        int total = matched.size();
        int safePageSize = clamp(pageSize != null ? pageSize : ARD_AGENTS_DEFAULT_PAGE_SIZE,
                1, ARD_AGENTS_MAX_PAGE_SIZE);
        int offset = decodePageToken(pageToken);
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(fromIndex + safePageSize, total);

        List<AiCatalogEntry> entries = new ArrayList<>();
        for (AiCatalogCandidate candidate : matched.subList(fromIndex, toIndex)) {
            entries.add(candidate.entry);
        }

        String nextPageToken = toIndex < total ? encodePageToken(toIndex) : null;

        AiCatalog catalog = buildAiCatalog(publisherDomain, entries);
        catalog.setNextPageToken(nextPageToken);
        return catalog;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ArdExploreResponse ardExplore(ArdExploreRequest request) {
        if (!ardConfig.isEnabled()) {
            throw new NotFoundException("ARD support is disabled");
        }

        if (request == null || request.getResultType() == null
                || request.getResultType().getFacets() == null
                || request.getResultType().getFacets().isEmpty()) {
            throw new BadRequestException("ARD explore requires 'resultType.facets'");
        }

        for (ArdFacetRequest facetRequest : request.getResultType().getFacets()) {
            if (facetRequest.getField() == null || !SUPPORTED_ARD_FACET_FIELDS.contains(facetRequest.getField())) {
                throw new BadRequestException("Unsupported ARD facet field: " + facetRequest.getField());
            }
        }

        String baseUrl = getBaseUrl();
        String publisherDomain = resolvePublisherDomain();

        String textFilter = null;
        Map<String, List<String>> filters = Collections.emptyMap();
        ArdSearchQuery query = request.getQuery();
        if (query != null) {
            textFilter = query.getText();
            filters = parseArdFilter(query.getFilter());
        }

        List<AiCatalogCandidate> matched = new ArrayList<>();
        for (AiCatalogCandidate candidate : collectAiCatalogCandidates(baseUrl, publisherDomain, textFilter)) {
            if (matchesFilters(candidate, filters)) {
                matched.add(candidate);
            }
        }

        ArdFacets facetsResult = new ArdFacets();
        for (ArdFacetRequest facetRequest : request.getResultType().getFacets()) {
            facetsResult.setAdditionalProperty(facetRequest.getField(), buildFacet(matched, facetRequest));
        }

        return ArdExploreResponse.builder()
                .resultType("facets")
                .facets(facetsResult)
                .build();
    }

    /**
     * A candidate AI Catalog entry paired with the originating artifact's labels, so ARD
     * filters that inspect labels (e.g. {@code tags}) can be evaluated without a second
     * storage round-trip.
     */
    private static final class AiCatalogCandidate {
        private final AiCatalogEntry entry;
        private final Map<String, String> labels;

        private AiCatalogCandidate(AiCatalogEntry entry, Map<String, String> labels) {
            this.entry = entry;
            this.labels = labels;
        }
    }

    private AiCatalog buildAiCatalog(String publisherDomain, List<AiCatalogEntry> entries) {
        return AiCatalog.builder()
                .specVersion(aiCatalogConfig.getSpecVersion())
                .host(AiCatalogHost.builder()
                        .displayName(aiCatalogConfig.getHostName())
                        .identifier(publisherDomain)
                        .build())
                .entries(entries)
                .build();
    }

    /**
     * Collects AI Catalog entries for all visible {@code AGENT_CARD} and {@code MCP_TOOL}
     * artifacts, optionally narrowed by a partial-name text filter applied at the storage
     * layer. Agent Card visibility labels are respected (mirroring {@code searchAgents} /
     * {@code getEntitledAgents}); MCP tools carry no visibility label, so read-level
     * authorization is the sole gate (mirroring {@code searchMcpTools}).
     */
    private List<AiCatalogCandidate> collectAiCatalogCandidates(String baseUrl, String publisherDomain,
            String textFilter) {
        List<AiCatalogCandidate> candidates = new ArrayList<>();

        Set<SearchFilter> agentFilters = new HashSet<>();
        agentFilters.add(SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));
        if (!StringUtil.isEmpty(textFilter)) {
            agentFilters.add(SearchFilter.ofPartialName(textFilter));
        }
        ArtifactSearchResultsDto agentResults = storage.searchArtifacts(
                agentFilters, OrderBy.createdOn, OrderDirection.desc, 0, MAX_VISIBILITY_FILTER_RESULTS, false);
        warnIfTruncated(agentResults);
        for (SearchedArtifactDto artifact : filterDtosByVisibility(agentResults.getArtifacts())) {
            candidates.add(buildAgentCandidate(artifact, baseUrl, publisherDomain));
        }

        Set<SearchFilter> toolFilters = new HashSet<>();
        toolFilters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_TOOL));
        if (!StringUtil.isEmpty(textFilter)) {
            toolFilters.add(SearchFilter.ofPartialName(textFilter));
        }
        ArtifactSearchResultsDto toolResults = storage.searchArtifacts(
                toolFilters, OrderBy.createdOn, OrderDirection.desc, 0, MAX_VISIBILITY_FILTER_RESULTS, false);
        warnIfTruncated(toolResults);
        for (SearchedArtifactDto artifact : toolResults.getArtifacts()) {
            candidates.add(buildToolCandidate(artifact, baseUrl, publisherDomain));
        }

        return candidates;
    }

    private AiCatalogCandidate buildAgentCandidate(SearchedArtifactDto artifact, String baseUrl,
            String publisherDomain) {
        JsonNode root = readLatestContent(artifact);
        String displayName = textOrDefault(root, "name", artifact.getName());
        String version = textOrDefault(root, "version", null);
        List<String> capabilities = extractAgentCardSkillIds(root);
        String groupSegment = groupIdSegment(artifact.getGroupId());

        AiCatalogEntry entry = AiCatalogEntry.builder()
                .identifier(buildAirIdentifier(publisherDomain, groupSegment, artifact.getArtifactId()))
                .displayName(displayName)
                .type(AiCatalogConstants.MEDIA_TYPE_AGENT_CARD)
                .url(baseUrl + "/.well-known/agents/" + groupSegment + "/" + artifact.getArtifactId())
                .description(artifact.getDescription())
                .capabilities(capabilities)
                .version(version)
                .build();
        return new AiCatalogCandidate(entry, artifact.getLabels());
    }

    private AiCatalogCandidate buildToolCandidate(SearchedArtifactDto artifact, String baseUrl,
            String publisherDomain) {
        JsonNode root = readLatestContent(artifact);
        String displayName = textOrDefault(root, "title", textOrDefault(root, "name", artifact.getName()));
        String version = textOrDefault(root, "version", null);
        String groupSegment = groupIdSegment(artifact.getGroupId());

        AiCatalogEntry entry = AiCatalogEntry.builder()
                .identifier(buildAirIdentifier(publisherDomain, groupSegment, artifact.getArtifactId()))
                .displayName(displayName)
                .type(AiCatalogConstants.MEDIA_TYPE_MCP_SERVER_CARD)
                .url(baseUrl + "/.well-known/mcp-tools/" + groupSegment + "/" + artifact.getArtifactId())
                .description(artifact.getDescription())
                .capabilities(Collections.emptyList())
                .version(version)
                .build();
        return new AiCatalogCandidate(entry, artifact.getLabels());
    }

    /**
     * Fetches and parses the latest version content for an artifact. Returns a
     * {@link MissingNode} (rather than throwing) on any failure so callers can safely chain
     * {@code .path(...)} lookups without null-checking.
     */
    private JsonNode readLatestContent(SearchedArtifactDto artifact) {
        try {
            GA ga = new GA(artifact.getGroupId(), artifact.getArtifactId());
            GAV gav = VersionExpressionParser.parse(ga, "branch=latest",
                    (g, branchId) -> storage.getBranchTip(g, branchId, RetrievalBehavior.SKIP_DISABLED_LATEST));
            StoredArtifactVersionDto stored = storage.getArtifactVersionContent(
                    gav.getRawGroupIdWithNull(), gav.getRawArtifactId(), gav.getRawVersionId());
            return mapper.readTree(stored.getContent().content());
        } catch (Exception e) {
            log.warn("Failed to parse content for {}/{}: {}",
                    artifact.getGroupId(), artifact.getArtifactId(), e.getMessage());
            return MissingNode.getInstance();
        }
    }

    private String textOrDefault(JsonNode root, String field, String fallback) {
        JsonNode node = root.path(field);
        return node.isTextual() ? node.asText() : fallback;
    }

    /**
     * Returns the URL/URN path segment for an artifact's group ID, using the same
     * {@code "default"} placeholder convention as the rest of the codebase (see
     * {@link GroupId#getRawGroupIdWithDefaultString()}) when the artifact belongs to the
     * default group (raw group ID {@code null}).
     */
    private String groupIdSegment(String rawGroupId) {
        return new GroupId(rawGroupId).getRawGroupIdWithDefaultString();
    }

    private String buildAirIdentifier(String publisherDomain, String groupSegment, String artifactId) {
        return AiCatalogConstants.URN_AIR_PREFIX + publisherDomain + ":" + groupSegment + ":" + artifactId;
    }

    /**
     * Resolves the {@code <publisher>} domain segment used in {@code urn:air:} identifiers
     * and as the AI Catalog host identifier. Uses the configured
     * {@code apicurio.ai-catalog.publisher-domain} when present; otherwise derives it from
     * the incoming request's host and port (the port is always included, per the AI Catalog
     * convention of identifying a specific registry deployment rather than just a hostname).
     */
    private String resolvePublisherDomain() {
        Optional<String> configured = aiCatalogConfig.getPublisherDomain();
        if (configured.isPresent() && !StringUtil.isEmpty(configured.get())) {
            return configured.get();
        }

        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (!StringUtil.isEmpty(forwardedHost)) {
            return forwardedHost;
        }

        String host = request.getServerName();
        int port = request.getServerPort();
        return port > 0 ? host + ":" + port : host;
    }

    /**
     * Parses an ARD {@code query.filter} map into a validated {@code key -> values}
     * structure. Unsupported keys and unsupported {@code type} values result in a
     * {@link BadRequestException}.
     */
    private Map<String, List<String>> parseArdFilter(ArdFilter filter) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (filter == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : filter.getAdditionalProperties().entrySet()) {
            String key = entry.getKey();
            if (!SUPPORTED_ARD_FILTER_KEYS.contains(key)) {
                throw new BadRequestException("Unsupported ARD filter key: " + key);
            }
            List<String> values = toStringList(entry.getValue());
            values.forEach(value -> validateFilterValue(key, value));
            result.put(key, values);
        }
        return result;
    }

    /**
     * Parses the {@code GET /ard/agents} EBNF-ish {@code filter} query parameter, e.g.
     * {@code "type=application/a2a-agent-card+json"}, optionally joining multiple clauses
     * with {@code " AND "}.
     */
    private Map<String, List<String>> parseArdAgentsFilter(String filter) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (StringUtil.isEmpty(filter)) {
            return result;
        }
        for (String clause : filter.split(ARD_FILTER_CLAUSE_SEPARATOR)) {
            String trimmed = clause.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new BadRequestException("Invalid ARD filter clause: " + trimmed);
            }
            String key = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            if (!SUPPORTED_ARD_FILTER_KEYS.contains(key)) {
                throw new BadRequestException("Unsupported ARD filter key: " + key);
            }
            validateFilterValue(key, value);
            result.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
        } else if (value != null) {
            values.add(String.valueOf(value));
        }
        return values;
    }

    private void validateFilterValue(String key, String value) {
        if ("type".equals(key) && !isRecognizedType(value)) {
            throw new BadRequestException("Unsupported ARD filter value for 'type': " + value);
        }
    }

    private boolean isRecognizedType(String value) {
        for (String recognized : RECOGNIZED_AI_CATALOG_TYPES) {
            if (recognized.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFilters(AiCatalogCandidate candidate, Map<String, List<String>> filters) {
        for (Map.Entry<String, List<String>> entry : filters.entrySet()) {
            if (!matchesFilterKey(candidate, entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Evaluates a single ARD filter key against a candidate entry. Values within a key are
     * OR-ed together; {@link #matchesFilters} AND-s across keys.
     */
    private boolean matchesFilterKey(AiCatalogCandidate candidate, String key, List<String> values) {
        switch (key) {
            case "type":
                return values.stream().anyMatch(value -> candidate.entry.getType() != null
                        && candidate.entry.getType().contains(value));
            case "publisher":
                return values.stream()
                        .anyMatch(value -> value.equals(publisherOf(candidate.entry.getIdentifier())));
            case "capabilities":
                return values.stream().anyMatch(value -> candidate.entry.getCapabilities() != null
                        && candidate.entry.getCapabilities().contains(value));
            case "tags":
                return values.stream().anyMatch(value -> matchesTag(candidate.labels, value));
            default:
                return false;
        }
    }

    /**
     * Extracts the {@code <publisher>} segment from a
     * {@code urn:air:<publisher>:<group>:<artifact>} identifier.
     */
    private String publisherOf(String identifier) {
        if (identifier == null || !identifier.startsWith(AiCatalogConstants.URN_AIR_PREFIX)) {
            return "";
        }
        String rest = identifier.substring(AiCatalogConstants.URN_AIR_PREFIX.length());
        int idx = rest.indexOf(':');
        return idx >= 0 ? rest.substring(0, idx) : rest;
    }

    /**
     * Matches a {@code tags} filter value against an artifact's labels. A value containing
     * {@code "="} is matched as an exact {@code key=value} label match; otherwise the value
     * is matched as a label-key presence check.
     */
    private boolean matchesTag(Map<String, String> labels, String value) {
        if (labels == null || labels.isEmpty()) {
            return false;
        }
        int eq = value.indexOf('=');
        if (eq > 0) {
            String labelKey = value.substring(0, eq);
            String labelValue = value.substring(eq + 1);
            return labelValue.equals(labels.get(labelKey));
        }
        return labels.containsKey(value);
    }

    /**
     * Converts an {@link AiCatalogEntry} into an ARD search result entry. Every result
     * reaching this point has already satisfied the mandatory text query AND every requested
     * filter (this increment applies boolean AND matching, not fuzzy/semantic ranking - see
     * ADR-0004 step 2), so every result is, by definition, a 100% match of the requested
     * criteria.
     */
    private ArdSearchResultEntry toArdSearchResultEntry(AiCatalogEntry entry, String baseUrl) {
        return ArdSearchResultEntry.builder()
                .identifier(entry.getIdentifier())
                .displayName(entry.getDisplayName())
                .type(entry.getType())
                .url(entry.getUrl())
                .description(entry.getDescription())
                .tags(entry.getTags())
                .capabilities(entry.getCapabilities())
                .version(entry.getVersion())
                .updatedAt(entry.getUpdatedAt())
                .score(100)
                .source(baseUrl)
                .build();
    }

    /**
     * Decodes an opaque ARD pagination token (base64 of the offset as a decimal string) into
     * an offset. An empty/null token decodes to offset 0.
     */
    private int decodePageToken(String pageToken) {
        if (StringUtil.isEmpty(pageToken)) {
            return 0;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(pageToken);
            return Integer.parseInt(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid ARD pageToken");
        }
    }

    private String encodePageToken(int offset) {
        return Base64.getEncoder().encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * Builds a single ARD facet by counting distinct values of the requested field across
     * the matched candidates, sorting by descending count, applying the optional
     * {@code limit}/{@code minCount}, and rolling any overflow buckets into
     * {@code otherCount}.
     */
    private ArdFacet buildFacet(List<AiCatalogCandidate> matched, ArdFacetRequest facetRequest) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AiCatalogCandidate candidate : matched) {
            String value = facetValue(candidate, facetRequest.getField());
            if (value != null) {
                counts.merge(value, 1, Integer::sum);
            }
        }

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int limit = facetRequest.getLimit() != null ? facetRequest.getLimit() : Integer.MAX_VALUE;
        int minCount = facetRequest.getMinCount() != null ? facetRequest.getMinCount() : 0;

        List<ArdFacetBucket> buckets = new ArrayList<>();
        int otherCount = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            if (entry.getValue() < minCount) {
                continue;
            }
            if (buckets.size() < limit) {
                buckets.add(ArdFacetBucket.builder().value(entry.getKey()).count(entry.getValue()).build());
            } else {
                otherCount += entry.getValue();
            }
        }

        return ArdFacet.builder().buckets(buckets).otherCount(otherCount).build();
    }

    private String facetValue(AiCatalogCandidate candidate, String field) {
        if ("type".equals(field)) {
            return candidate.entry.getType();
        }
        if ("publisher".equals(field)) {
            return publisherOf(candidate.entry.getIdentifier());
        }
        return null;
    }

    private boolean isAuthEnabled() {
        return authConfig.isOidcAuthEnabled() || authConfig.isBasicAuthEnabled();
    }

    private void warnIfTruncated(ArtifactSearchResultsDto results) {
        if (results.getCount() >= MAX_VISIBILITY_FILTER_RESULTS) {
            log.warn("Agent visibility filtering may be incomplete: total agent count ({}) "
                    + "reached the in-memory limit of {}. Results beyond this limit are not included.",
                    results.getCount(), MAX_VISIBILITY_FILTER_RESULTS);
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

    /**
     * Filters artifact DTOs by visibility rules without performing expensive content conversion.
     * When no auth is enabled, all artifacts are returned. Otherwise, visibility is determined
     * by the {@code apicurio.agent.visibility} label (falling back to the configured default).
     */
    private List<SearchedArtifactDto> filterDtosByVisibility(List<SearchedArtifactDto> artifacts) {
        if (!isAuthEnabled()) {
            return new ArrayList<>(artifacts);
        }

        boolean isAuthenticated = !securityIdentity.isAnonymous();
        boolean isAdmin = isAuthenticated && adminOverride.isAdmin();
        String currentUser = isAuthenticated
                ? securityIdentity.getPrincipal().getName() : null;

        List<SearchedArtifactDto> result = new ArrayList<>();
        for (SearchedArtifactDto artifact : artifacts) {
            String visibility = resolveVisibility(artifact.getLabels());
            if ("public".equals(visibility)) {
                result.add(artifact);
            } else if (!isAuthenticated) {
                continue;
            } else if ("entitled".equals(visibility)) {
                result.add(artifact);
            } else if ("private".equals(visibility)) {
                String owner = artifact.getOwner();
                if (isAdmin || (owner != null && owner.equals(currentUser))) {
                    result.add(artifact);
                }
            } else {
                log.warn("Unrecognized visibility '{}' for artifact {}/{}, treating as private",
                        visibility, artifact.getGroupId(), artifact.getArtifactId());
                String owner = artifact.getOwner();
                if (isAdmin || (owner != null && owner.equals(currentUser))) {
                    result.add(artifact);
                }
            }
        }
        return result;
    }

    /**
     * Returns public agents by resolving each artifact's effective visibility in memory, rather
     * than filtering on the {@code apicurio.agent.visibility} label. Used when the configured
     * default visibility is {@code public}, because artifacts relying on that default carry no
     * visibility label and so cannot be matched by a label search filter.
     */
    private AgentSearchResults publicAgentsByResolvedVisibility(Set<SearchFilter> filters,
            Integer offset, Integer limit) {
        ArtifactSearchResultsDto results = storage.searchArtifacts(
                filters, OrderBy.createdOn, OrderDirection.desc, 0, MAX_VISIBILITY_FILTER_RESULTS, false);
        warnIfTruncated(results);

        // Filter by resolved visibility on DTOs first (cheap), then paginate, then convert (expensive)
        List<SearchedArtifactDto> visible = new ArrayList<>();
        for (SearchedArtifactDto artifact : results.getArtifacts()) {
            if (A2AConstants.VISIBILITY_PUBLIC.equals(resolveVisibility(artifact.getLabels()))) {
                visible.add(artifact);
            }
        }

        int total = visible.size();
        int safeOffset = Math.max(0, Math.min(offset, total));
        int safeLimit = Math.max(0, Math.min(limit, 500));
        int toIndex = Math.min(safeOffset + safeLimit, total);

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : visible.subList(safeOffset, toIndex)) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count(total)
                .agents(agents)
                .build();
    }

    /**
     * Returns the effective visibility for an artifact. If the {@code apicurio.agent.visibility}
     * label is not set, falls back to the configured default visibility.
     * <p>
     * The label key is matched case-insensitively. Labels reach this method from the serialized
     * {@code labels} column, which preserves the case they were supplied with, whereas the
     * {@code artifact_labels} table used by label search filters is lowercased on insert. Matching
     * exactly here would let a card labelled {@code Apicurio.Agent.Visibility=private} resolve to
     * the configured default instead of to {@code private}.
     */
    private String resolveVisibility(Map<String, String> labels) {
        if (labels != null) {
            for (Map.Entry<String, String> label : labels.entrySet()) {
                if (A2AConstants.LABEL_AGENT_VISIBILITY.equalsIgnoreCase(label.getKey())
                        && label.getValue() != null) {
                    return label.getValue().toLowerCase(Locale.ROOT);
                }
            }
        }
        return a2aConfig.getDefaultVisibility().toLowerCase(Locale.ROOT);
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
