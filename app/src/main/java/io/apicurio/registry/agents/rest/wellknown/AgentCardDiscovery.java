package io.apicurio.registry.agents.rest.wellknown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.agents.a2a.A2AConfig;
import io.apicurio.registry.agents.a2a.RegistryAgentCardBuilder;
import io.apicurio.registry.agents.aicatalog.AiCatalogConstants;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.rest.v3.beans.AgentCapabilities;
import io.apicurio.registry.rest.v3.beans.AgentCard;
import io.apicurio.registry.rest.v3.beans.AgentInterface;
import io.apicurio.registry.rest.v3.beans.AgentSearchResult;
import io.apicurio.registry.rest.v3.beans.AgentSearchResults;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A Agent Card endpoints: the registry's own card, registered cards and agent search.
 */
@ApplicationScoped
public class AgentCardDiscovery {

    private static final Logger log = LoggerFactory.getLogger(AgentCardDiscovery.class);

    static final ObjectMapper mapper = new ObjectMapper();

    @Inject
    A2AConfig a2aConfig;

    @Inject
    RegistryAgentCardBuilder agentCardBuilder;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    AiCatalogDiscovery aiCatalogDiscovery;

    @Inject
    WellKnownRequestSupport wellKnownRequestSupport;

    public AgentCard getAgentCard() {
        if (!a2aConfig.isEnabled()) {
            throw new NotFoundException("A2A support is disabled");
        }

        String baseUrl = wellKnownRequestSupport.getBaseUrl();
        return agentCardBuilder.build(baseUrl);
    }

    public AgentCard getAgentCardForOrchestrate() {
        return getAgentCard();
    }

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

    public AgentSearchResults searchAgents(String name, List<String> skills, List<String> capabilities,
            List<String> inputModes, List<String> outputModes, Integer offset, Integer limit) {
        if (!a2aConfig.isEnabled()) {
            throw new NotFoundException("A2A support is disabled");
        }

        String baseUrl = wellKnownRequestSupport.getBaseUrl();
        String publisherDomain = wellKnownRequestSupport.resolvePublisherDomain();

        Set<SearchFilter> structureFilters = new HashSet<>();
        addStructureFilters(structureFilters, "skill", skills);
        addStructureFilters(structureFilters, "inputmode", inputModes);
        addStructureFilters(structureFilters, "outputmode", outputModes);
        if (capabilities != null) {
            for (String capability : capabilities) {
                requireNonBlankStructuredFilter("capability", capability);
                String[] parts = capability.split(":", 2);
                requireNonBlankStructuredFilter("capability", parts[0]);
                if (parts.length == 2 && !"true".equals(parts[1]) && !"false".equals(parts[1])) {
                    throw new BadRequestException("Capability filter must use true or false");
                }
                SearchFilter filter = SearchFilter.ofStructure("agent_card:capability:" + parts[0]);
                structureFilters.add(parts.length == 2 && "false".equals(parts[1]) ? filter.negated() : filter);
            }
        }
        // Delegate candidate collection (including the single, shared visibility-filtering
        // implementation) to the same core that backs the AI Catalog / ARD endpoints.
        // Structured skill/capability/input-mode/output-mode filters have no equivalent in
        // AiCatalogEntry, so they are evaluated afterwards against each surviving candidate's
        // Agent Card content.
        List<SearchedArtifactDto> matched = new ArrayList<>();
        for (AiCatalogDiscovery.AiCatalogCandidate candidate : aiCatalogDiscovery.collectAiCatalogCandidates(baseUrl, publisherDomain, name, structureFilters)) {
            if (!AiCatalogConstants.MEDIA_TYPE_AGENT_CARD.equals(candidate.entry.getType())) {
                continue;
            }
            if (matchesAgentStructuredFilters(candidate.artifact, skills, capabilities, inputModes, outputModes)) {
                matched.add(candidate.artifact);
            }
        }

        int total = matched.size();
        int safeOffset = Math.max(0, Math.min(offset, total));
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int toIndex = Math.min(safeOffset + safeLimit, total);
        List<SearchedArtifactDto> page = matched.subList(safeOffset, toIndex);

        List<AgentSearchResult> agents = new ArrayList<>();
        for (SearchedArtifactDto artifact : page) {
            agents.add(convertToAgentSearchResult(artifact));
        }

        return AgentSearchResults.builder()
                .count(total)
                .agents(agents)
                .build();
    }

    /**
     * Evaluates the {@code skill}/{@code capability}/{@code inputMode}/{@code outputMode}
     * structured filters accepted by {@link #searchAgents} against an artifact's Agent Card
     * content. All requested values must match (AND semantics), mirroring the previous
     * per-filter storage-index semantics. A {@code capability} value may be suffixed with
     * {@code :false} to require the capability be absent/disabled (default {@code true}).
     */
    private boolean matchesAgentStructuredFilters(SearchedArtifactDto artifact, List<String> skills,
            List<String> capabilities, List<String> inputModes, List<String> outputModes) {
        boolean hasSkills = skills != null && !skills.isEmpty();
        boolean hasCapabilities = capabilities != null && !capabilities.isEmpty();
        boolean hasInputModes = inputModes != null && !inputModes.isEmpty();
        boolean hasOutputModes = outputModes != null && !outputModes.isEmpty();
        if (!hasSkills && !hasCapabilities && !hasInputModes && !hasOutputModes) {
            return true;
        }

        JsonNode root = aiCatalogDiscovery.readLatestContent(artifact);

        if (hasSkills) {
            List<String> skillIds = extractAgentCardSkillIds(root);
            for (String skill : skills) {
                if (!skillIds.contains(skill)) {
                    return false;
                }
            }
        }

        if (hasCapabilities) {
            JsonNode capabilitiesNode = root.path("capabilities");
            for (String capability : capabilities) {
                String[] parts = capability.split(":", 2);
                String capKey = parts[0];
                String capValue = parts.length > 1 ? parts[1] : "true";
                boolean expected = !"false".equals(capValue);
                if (capabilitiesNode.path(capKey).asBoolean(false) != expected) {
                    return false;
                }
            }
        }

        if (hasInputModes && !containsAllTextValues(root.path("defaultInputModes"), inputModes)) {
            return false;
        }

        if (hasOutputModes && !containsAllTextValues(root.path("defaultOutputModes"), outputModes)) {
            return false;
        }

        return true;
    }

    /**
     * Returns {@code true} if every value in {@code required} is present among the textual
     * elements of {@code arrayNode}.
     */
    private boolean containsAllTextValues(JsonNode arrayNode, List<String> required) {
        List<String> values = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode value : arrayNode) {
                if (value.isTextual()) {
                    values.add(value.asText());
                }
            }
        }
        return values.containsAll(required);
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
    List<String> extractAgentCardSkillIds(JsonNode root) {
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

    private void addStructureFilters(Set<SearchFilter> filters, String kind, List<String> values) {
        if (values != null) {
            for (String value : values) {
                requireNonBlankStructuredFilter(kind, value);
                filters.add(SearchFilter.ofStructure("agent_card:" + kind + ":" + value));
            }
        }
    }

    private void requireNonBlankStructuredFilter(String parameter, String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Structured filter '" + parameter + "' must not be blank");
        }
    }
}
