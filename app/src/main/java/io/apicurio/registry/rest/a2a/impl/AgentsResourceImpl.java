package io.apicurio.registry.rest.a2a.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.rest.a2a.AgentsResource;
import io.apicurio.registry.rest.a2a.beans.AgentCard;
import io.apicurio.registry.rest.a2a.beans.AgentSearchResults;
import io.apicurio.registry.rest.a2a.beans.AgentSortBy;
import io.apicurio.registry.rest.a2a.beans.CreateAgent;
import io.apicurio.registry.rest.a2a.beans.SearchedAgent;
import io.apicurio.registry.rest.a2a.beans.SortOrder;
import io.apicurio.registry.rest.a2a.beans.UpdateAgent;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.util.ArtifactIdGenerator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Implementation of the A2A Agents REST API for managing A2A Agent Cards.
 *
 * Agent Cards are stored as artifacts of type AGENT_CARD in a dedicated group named "agents".
 * This is a separate API from the core registry API, designed for compatibility with the
 * A2A (Agent-to-Agent) protocol specification.
 */
@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class AgentsResourceImpl implements AgentsResource {

    private static final String AGENTS_GROUP_ID = "agents";

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rulesService;

    @Inject
    ArtifactIdGenerator idGenerator;

    @Inject
    ObjectMapper objectMapper;

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentSearchResults listAgents(Integer offset, Integer limit, SortOrder order, AgentSortBy orderby) {
        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofGroupId(AGENTS_GROUP_ID),
                SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));

        OrderBy orderByField = orderby != null ? mapAgentSortByToOrderBy(orderby) : OrderBy.createdOn;
        OrderDirection orderDir = order != null && order == SortOrder.asc ? OrderDirection.asc
                : OrderDirection.desc;

        ArtifactSearchResultsDto searchResults = storage.searchArtifacts(filters, orderByField, orderDir,
                offset != null ? offset : 0, limit != null ? limit : 20);

        return toAgentSearchResults(searchResults);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public AgentCard createAgent(CreateAgent data) {
        String agentId = data.getAgentId();
        if (agentId == null || agentId.isBlank()) {
            agentId = idGenerator.generate();
        }

        AgentCard content = data.getContent();
        String jsonContent;
        try {
            jsonContent = objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize agent card", e);
        }

        TypedContent typedContent = TypedContent.create(ContentHandle.create(jsonContent),
                ContentTypes.APPLICATION_JSON);

        // Apply validation rules
        rulesService.applyRules(AGENTS_GROUP_ID, agentId, ArtifactType.AGENT_CARD, typedContent,
                RuleApplicationType.CREATE, Collections.emptyList(), Collections.emptyMap());

        // Create the artifact
        String version = data.getVersion() != null ? data.getVersion() : "1";

        EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder()
                .name(content.getName()).description(content.getDescription()).build();

        EditableVersionMetaDataDto versionMetaData = EditableVersionMetaDataDto.builder().name(content.getName())
                .description(content.getDescription()).build();

        ContentWrapperDto contentDto = ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                .content(ContentHandle.create(jsonContent)).references(Collections.emptyList()).build();

        Pair<?, ArtifactVersionMetaDataDto> result = storage.createArtifact(AGENTS_GROUP_ID, agentId,
                ArtifactType.AGENT_CARD, artifactMetaData, version, contentDto, versionMetaData,
                Collections.emptyList(), false, false, null);

        log.info("Agent registered: groupId={}, agentId={}, version={}", AGENTS_GROUP_ID, agentId,
                result.getRight().getVersion());

        return content;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentSearchResults searchAgents(String skill, String capability, String tag, Integer offset,
            Integer limit) {
        // Get all agents and filter in memory for now
        // In the future, this could be optimized with database-level JSON queries
        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofGroupId(AGENTS_GROUP_ID),
                SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));

        ArtifactSearchResultsDto allResults = storage.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 0, 1000);

        List<SearchedAgent> matchingAgents = new ArrayList<>();

        for (SearchedArtifactDto artifact : allResults.getArtifacts()) {
            try {
                // Get latest version using branch tip
                GAV latestGAV = storage.getBranchTip(new GA(AGENTS_GROUP_ID, artifact.getArtifactId()),
                        BranchId.LATEST, RetrievalBehavior.SKIP_DISABLED_LATEST);
                StoredArtifactVersionDto versionContent = storage.getArtifactVersionContent(AGENTS_GROUP_ID,
                        artifact.getArtifactId(), latestGAV.getRawVersionId());

                String contentStr = versionContent.getContent().content();
                AgentCard agentCard = objectMapper.readValue(contentStr, AgentCard.class);

                boolean matches = matchesFilters(agentCard, skill, capability, tag);
                if (matches) {
                    SearchedAgent agent = SearchedAgent.builder().agentId(artifact.getArtifactId())
                            .name(agentCard.getName()).description(agentCard.getDescription())
                            .createdOn(artifact.getCreatedOn()).modifiedOn(artifact.getModifiedOn()).build();
                    matchingAgents.add(agent);
                }
            } catch (Exception e) {
                log.warn("Failed to parse agent card for artifact {}: {}", artifact.getArtifactId(),
                        e.getMessage());
            }
        }

        // Apply pagination
        int startIndex = offset != null ? offset : 0;
        int endIndex = Math.min(startIndex + (limit != null ? limit : 20), matchingAgents.size());

        List<SearchedAgent> pagedAgents = startIndex < matchingAgents.size()
                ? matchingAgents.subList(startIndex, endIndex)
                : Collections.emptyList();

        return AgentSearchResults.builder().count(matchingAgents.size()).agents(pagedAgents).build();
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentCard getAgent(String agentId) {
        // Get latest version using branch tip
        GAV latestGAV = storage.getBranchTip(new GA(AGENTS_GROUP_ID, agentId), BranchId.LATEST,
                RetrievalBehavior.SKIP_DISABLED_LATEST);
        StoredArtifactVersionDto content = storage.getArtifactVersionContent(AGENTS_GROUP_ID, agentId,
                latestGAV.getRawVersionId());

        try {
            return objectMapper.readValue(content.getContent().content(), AgentCard.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse agent card", e);
        }
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Write)
    public AgentCard updateAgent(String agentId, UpdateAgent data) {
        AgentCard content = data.getContent();
        String jsonContent;
        try {
            jsonContent = objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize agent card", e);
        }

        TypedContent typedContent = TypedContent.create(ContentHandle.create(jsonContent),
                ContentTypes.APPLICATION_JSON);

        // Apply validation and compatibility rules
        rulesService.applyRules(AGENTS_GROUP_ID, agentId, ArtifactType.AGENT_CARD, typedContent,
                RuleApplicationType.UPDATE, Collections.emptyList(), Collections.emptyMap());

        // Get the artifact type (needed for createArtifactVersion)
        storage.getArtifactMetaData(AGENTS_GROUP_ID, agentId);

        // Create a new version
        String version = data.getVersion();

        EditableVersionMetaDataDto versionMetaData = EditableVersionMetaDataDto.builder().name(content.getName())
                .description(content.getDescription()).build();

        ContentWrapperDto contentDto = ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                .content(ContentHandle.create(jsonContent)).references(Collections.emptyList()).build();

        storage.createArtifactVersion(AGENTS_GROUP_ID, agentId, version, ArtifactType.AGENT_CARD, contentDto,
                versionMetaData, Collections.emptyList(), false, false, null);

        // Update artifact metadata
        EditableArtifactMetaDataDto artifactMetaData = EditableArtifactMetaDataDto.builder()
                .name(content.getName()).description(content.getDescription()).build();
        storage.updateArtifactMetaData(AGENTS_GROUP_ID, agentId, artifactMetaData);

        log.info("Agent updated: groupId={}, agentId={}", AGENTS_GROUP_ID, agentId);

        return content;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public void deleteAgent(String agentId) {
        storage.deleteArtifact(AGENTS_GROUP_ID, agentId);
        log.info("Agent deleted: groupId={}, agentId={}", AGENTS_GROUP_ID, agentId);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public AgentSearchResults getAgentVersions(String agentId, Integer offset, Integer limit) {
        Set<SearchFilter> filters = Set.of(
                SearchFilter.ofGroupId(AGENTS_GROUP_ID),
                SearchFilter.ofArtifactId(agentId));

        VersionSearchResultsDto versionsResult = storage.searchVersions(filters, OrderBy.createdOn,
                OrderDirection.desc, offset != null ? offset : 0, limit != null ? limit : 20);

        List<SearchedAgent> agents = new ArrayList<>();
        for (var version : versionsResult.getVersions()) {
            try {
                StoredArtifactVersionDto content = storage.getArtifactVersionContent(AGENTS_GROUP_ID, agentId,
                        version.getVersion());
                AgentCard agentCard = objectMapper.readValue(content.getContent().content(), AgentCard.class);

                SearchedAgent agent = SearchedAgent.builder().agentId(agentId).name(agentCard.getName())
                        .description(agentCard.getDescription()).createdOn(version.getCreatedOn())
                        .modifiedOn(version.getCreatedOn()).build();
                agents.add(agent);
            } catch (Exception e) {
                log.warn("Failed to parse agent card for version {}: {}", version.getVersion(), e.getMessage());
            }
        }

        return AgentSearchResults.builder().count((int) versionsResult.getCount()).agents(agents).build();
    }

    private boolean matchesFilters(AgentCard agentCard, String skill, String capability, String tag) {
        // If no filters specified, match all
        if (skill == null && capability == null && tag == null) {
            return true;
        }

        // Check skill filter
        if (skill != null && agentCard.getSkills() != null) {
            boolean skillMatch = agentCard.getSkills().stream()
                    .anyMatch(s -> skill.equals(s.getId()) || skill.equals(s.getName()));
            if (!skillMatch) {
                return false;
            }
        } else if (skill != null) {
            return false;
        }

        // Check capability filter
        if (capability != null && agentCard.getCapabilities() != null) {
            boolean capMatch = false;
            var caps = agentCard.getCapabilities();
            if ("streaming".equalsIgnoreCase(capability) && Boolean.TRUE.equals(caps.getStreaming())) {
                capMatch = true;
            } else if ("pushNotifications".equalsIgnoreCase(capability)
                    && Boolean.TRUE.equals(caps.getPushNotifications())) {
                capMatch = true;
            } else if ("stateTransitionHistory".equalsIgnoreCase(capability)
                    && Boolean.TRUE.equals(caps.getStateTransitionHistory())) {
                capMatch = true;
            }
            if (!capMatch) {
                return false;
            }
        } else if (capability != null) {
            return false;
        }

        // Check tag filter
        if (tag != null && agentCard.getSkills() != null) {
            boolean tagMatch = agentCard.getSkills().stream().filter(s -> s.getTags() != null)
                    .anyMatch(s -> s.getTags().contains(tag));
            if (!tagMatch) {
                return false;
            }
        } else if (tag != null) {
            return false;
        }

        return true;
    }

    private OrderBy mapAgentSortByToOrderBy(AgentSortBy sortBy) {
        return switch (sortBy) {
            case agentId -> OrderBy.artifactId;
            case name -> OrderBy.name;
            case createdOn -> OrderBy.createdOn;
            case modifiedOn -> OrderBy.modifiedOn;
        };
    }

    private AgentSearchResults toAgentSearchResults(ArtifactSearchResultsDto searchResults) {
        List<SearchedAgent> agents = new ArrayList<>();

        for (SearchedArtifactDto artifact : searchResults.getArtifacts()) {
            SearchedAgent agent = SearchedAgent.builder().agentId(artifact.getArtifactId())
                    .name(artifact.getName()).description(artifact.getDescription())
                    .createdOn(artifact.getCreatedOn()).modifiedOn(artifact.getModifiedOn()).build();
            agents.add(agent);
        }

        return AgentSearchResults.builder().count((int) searchResults.getCount()).agents(agents).build();
    }
}
