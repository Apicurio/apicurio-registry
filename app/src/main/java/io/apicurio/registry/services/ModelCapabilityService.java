package io.apicurio.registry.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rest.v3.beans.ModelInfo;
import io.apicurio.registry.rest.v3.beans.ModelSearchResults;
import io.apicurio.registry.rest.v3.beans.ModelSortBy;
import io.apicurio.registry.rest.v3.beans.SortOrder;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service for querying AI/ML model schemas by capabilities and metadata.
 */
@ApplicationScoped
public class ModelCapabilityService {

    private static final String MODEL_SCHEMA_TYPE = "MODEL_SCHEMA";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Search for MODEL_SCHEMA artifacts matching the given criteria.
     *
     * @param offset           Number of results to skip
     * @param limit            Maximum number of results to return
     * @param order            Sort order (asc/desc)
     * @param orderBy          Field to sort by
     * @param provider         Filter by provider (e.g., "openai", "anthropic")
     * @param capabilities     Filter by required capabilities
     * @param minContextWindow Minimum context window size
     * @param maxContextWindow Maximum context window size
     * @param groupId          Filter by group ID
     * @param name             Filter by model name
     * @return Search results containing matching models
     */
    public ModelSearchResults searchModels(BigInteger offset, BigInteger limit, SortOrder order,
                                            ModelSortBy orderBy, String provider, List<String> capabilities,
                                            Long minContextWindow, Long maxContextWindow,
                                            String groupId, String name) {

        // Build search filters for MODEL_SCHEMA artifacts
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(MODEL_SCHEMA_TYPE));

        if (groupId != null && !groupId.isEmpty()) {
            filters.add(SearchFilter.ofGroupId(groupId));
        }

        if (name != null && !name.isEmpty()) {
            filters.add(SearchFilter.ofName(name));
        }

        // Determine ordering
        OrderBy orderByField = OrderBy.createdOn;
        if (orderBy != null) {
            orderByField = switch (orderBy) {
                case name -> OrderBy.name;
                case createdOn -> OrderBy.createdOn;
                case modifiedOn -> OrderBy.modifiedOn;
                default -> OrderBy.createdOn;
            };
        }

        OrderDirection orderDir = order == SortOrder.asc ? OrderDirection.asc : OrderDirection.desc;

        // Search for all MODEL_SCHEMA artifacts
        // Note: We need to fetch more than requested because we'll filter by content fields
        int searchLimit = limit != null ? limit.intValue() * 10 : 200;
        int searchOffset = 0; // Start from beginning to apply content filtering

        ArtifactSearchResultsDto searchResults = storage.searchArtifacts(filters, orderByField, orderDir,
                searchOffset, searchLimit);

        // Process each result and extract model metadata from content
        List<ModelInfo> modelInfos = new ArrayList<>();

        for (SearchedArtifactDto artifact : searchResults.getArtifacts()) {
            try {
                ModelInfo modelInfo = extractModelInfo(artifact);
                if (modelInfo != null && matchesFilters(modelInfo, provider, capabilities,
                        minContextWindow, maxContextWindow)) {
                    modelInfos.add(modelInfo);
                }
            } catch (Exception e) {
                log.debug("Failed to extract model info for artifact {}/{}: {}",
                        artifact.getGroupId(), artifact.getArtifactId(), e.getMessage());
            }
        }

        // Apply sorting for content-based fields (provider, contextWindow)
        if (orderBy != null && (orderBy == ModelSortBy.provider || orderBy == ModelSortBy.contextWindow)) {
            sortByContentField(modelInfos, orderBy, order);
        }

        // Apply pagination
        int effectiveOffset = offset != null ? offset.intValue() : 0;
        int effectiveLimit = limit != null ? limit.intValue() : 20;

        int totalCount = modelInfos.size();
        int endIndex = Math.min(effectiveOffset + effectiveLimit, totalCount);
        int startIndex = Math.min(effectiveOffset, totalCount);

        List<ModelInfo> pagedResults = modelInfos.subList(startIndex, endIndex);

        return ModelSearchResults.builder()
                .count(totalCount)
                .models(pagedResults)
                .build();
    }

    /**
     * Extract ModelInfo from an artifact by reading its content.
     */
    private ModelInfo extractModelInfo(SearchedArtifactDto artifact) {
        try {
            // Get the latest version using the branch tip
            GA ga = new GA(artifact.getGroupId(), artifact.getArtifactId());
            GAV gav = storage.getBranchTip(ga, BranchId.LATEST, RetrievalBehavior.SKIP_DISABLED_LATEST);

            // Get the version metadata
            ArtifactVersionMetaDataDto versionMeta = storage.getArtifactVersionMetaData(
                    artifact.getGroupId(), artifact.getArtifactId(), gav.getRawVersionId());

            // Get the content
            StoredArtifactVersionDto content = storage.getArtifactVersionContent(
                    artifact.getGroupId(), artifact.getArtifactId(), gav.getRawVersionId());

            JsonNode schema = parseContent(content.getContent());

            // Extract model metadata fields
            String modelId = schema.path("modelId").asText(null);
            String provider = schema.path("provider").asText(null);
            String modelVersion = schema.path("version").asText(gav.getRawVersionId());

            JsonNode metadata = schema.path("metadata");
            Long contextWindow = null;
            List<String> capabilities = new ArrayList<>();

            if (!metadata.isMissingNode()) {
                if (!metadata.path("contextWindow").isMissingNode()) {
                    contextWindow = metadata.path("contextWindow").asLong();
                }

                JsonNode capsNode = metadata.path("capabilities");
                if (!capsNode.isMissingNode() && capsNode.isArray()) {
                    capabilities = StreamSupport.stream(capsNode.spliterator(), false)
                            .map(JsonNode::asText)
                            .collect(Collectors.toList());
                }
            }

            return ModelInfo.builder()
                    .groupId(artifact.getGroupId() != null ? artifact.getGroupId() : "default")
                    .artifactId(artifact.getArtifactId())
                    .version(gav.getRawVersionId())
                    .name(artifact.getName())
                    .description(artifact.getDescription())
                    .modelId(modelId)
                    .provider(provider)
                    .contextWindow(contextWindow)
                    .capabilities(capabilities)
                    .createdOn(artifact.getCreatedOn())
                    .modifiedOn(artifact.getModifiedOn())
                    .globalId(versionMeta.getGlobalId())
                    .build();

        } catch (Exception e) {
            log.debug("Failed to extract model info: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse content as YAML or JSON.
     */
    private JsonNode parseContent(ContentHandle content) throws Exception {
        String text = content.content();

        // Try YAML first (which also handles JSON)
        try {
            return YAML_MAPPER.readTree(text);
        } catch (Exception e) {
            // Fall back to JSON
            return JSON_MAPPER.readTree(text);
        }
    }

    /**
     * Check if a ModelInfo matches the given filter criteria.
     */
    private boolean matchesFilters(ModelInfo model, String provider, List<String> capabilities,
                                    Long minContextWindow, Long maxContextWindow) {

        // Filter by provider
        if (provider != null && !provider.isEmpty()) {
            if (model.getProvider() == null || !model.getProvider().equalsIgnoreCase(provider)) {
                return false;
            }
        }

        // Filter by capabilities (all must match)
        if (capabilities != null && !capabilities.isEmpty()) {
            if (model.getCapabilities() == null || model.getCapabilities().isEmpty()) {
                return false;
            }

            Set<String> modelCaps = new HashSet<>(model.getCapabilities());
            for (String cap : capabilities) {
                if (!modelCaps.contains(cap)) {
                    return false;
                }
            }
        }

        // Filter by context window range
        if (minContextWindow != null) {
            if (model.getContextWindow() == null || model.getContextWindow() < minContextWindow) {
                return false;
            }
        }

        if (maxContextWindow != null) {
            if (model.getContextWindow() == null || model.getContextWindow() > maxContextWindow) {
                return false;
            }
        }

        return true;
    }

    /**
     * Sort models by content-based fields.
     */
    private void sortByContentField(List<ModelInfo> models, ModelSortBy orderBy, SortOrder order) {
        Comparator<ModelInfo> comparator = switch (orderBy) {
            case provider -> Comparator.comparing(
                    m -> m.getProvider() != null ? m.getProvider() : "",
                    String.CASE_INSENSITIVE_ORDER);
            case contextWindow -> Comparator.comparing(
                    m -> m.getContextWindow() != null ? m.getContextWindow() : 0L);
            default -> null;
        };

        if (comparator != null) {
            if (order == SortOrder.desc) {
                comparator = comparator.reversed();
            }
            models.sort(comparator);
        }
    }
}
