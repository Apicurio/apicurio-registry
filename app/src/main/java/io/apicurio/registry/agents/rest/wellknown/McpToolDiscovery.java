package io.apicurio.registry.agents.rest.wellknown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.agents.mcptools.McpToolsConfig;
import io.apicurio.registry.agents.mcptools.compatibility.CompatibilityVerdict;
import io.apicurio.registry.agents.mcptools.compatibility.CrossToolCompatibilityService;
import io.apicurio.registry.agents.mcptools.compatibility.PairCompatibility;
import io.apicurio.registry.agents.mcptools.compatibility.PreparedProducer;
import io.apicurio.registry.agents.mcptools.rest.beans.McpCompatibleToolsResults;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.ISearchAuthorizer;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResult;
import io.apicurio.registry.rest.v3.beans.McpToolSearchResults;
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.RegistryStorage;
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
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP tool endpoints: registered tools, tool search and compatible-tool discovery.
 */
@ApplicationScoped
public class McpToolDiscovery {

    private static final Logger log = LoggerFactory.getLogger(McpToolDiscovery.class);

    static final ObjectMapper mapper = new ObjectMapper();

    private static final String PROPERTIES_FIELD = "properties";

    /**
     * Maximum number of MCP-tool candidates evaluated per compatible-tools request.
     * Kept well below {@link #MAX_VISIBILITY_FILTER_RESULTS} because each candidate
     * requires a storage round-trip; raising this limit directly raises per-request cost.
     */
    private static final int MAX_COMPATIBLE_CANDIDATE_SCAN = 500;

    @Inject
    McpToolsConfig mcpToolsConfig;

    @Inject
    CrossToolCompatibilityService crossToolCompatibility;

    @Inject
    @Current
    RegistryStorage storage;

    /** Discovery lists registry artifacts, so it is restricted by per-resource authorization. */
    @Inject
    ISearchAuthorizer searchAuthorizer;

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
            ArtifactSearchResultsDto results = searchAuthorizer.searchArtifacts(filters, OrderBy.createdOn,
                    OrderDirection.desc, 0, AiCatalogDiscovery.MAX_VISIBILITY_FILTER_RESULTS, false);

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

        ArtifactSearchResultsDto results = searchAuthorizer.searchArtifacts(filters, OrderBy.createdOn,
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

    public McpCompatibleToolsResults findCompatibleTools(String groupId, String artifactId,
            String version, Integer offset, Integer limit) {
        if (!mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("MCP tools support is disabled");
        }

        StoredArtifactVersionDto sourceArtifact = fetchMcpToolArtifact(groupId, artifactId, version);
        PreparedProducer producer = readToolRoot(sourceArtifact)
                .map(crossToolCompatibility::prepareProducer)
                .orElseGet(crossToolCompatibility::unreadableProducer);

        if (!producer.canMatch()) {
            return McpCompatibleToolsResults.builder().count(0).tools(Collections.emptyList()).build();
        }

        String rawGroupId = new GroupId(groupId).getRawGroupIdWithNull();
        List<McpToolSearchResult> compatibleTools = findCompatibleCandidates(rawGroupId, artifactId, producer);

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

    private Optional<JsonNode> readToolRoot(StoredArtifactVersionDto stored) {
        try {
            return Optional.of(mapper.readTree(stored.getContent().content()));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /**
     * Scans at most {@link #MAX_COMPATIBLE_CANDIDATE_SCAN} MCP tools and returns those whose
     * compatibility verdict against the source tool is
     * {@link CompatibilityVerdict#COMPATIBLE}, as decided by {@link CrossToolCompatibilityService}.
     *
     * <p><b>Authorization note:</b> candidate identity and metadata are exposed at the same
     * read-level scope as {@code searchMcpTools}, which also returns all MCP tools visible
     * to the caller via {@link AuthorizedLevel#Read}.  No per-artifact visibility label is
     * applied because MCP tools (unlike A2A agents) do not carry an
     * {@code apicurio.agent.visibility} label; the caller's read-level authorization is the
     * sole gate for both endpoints.
     */
    private List<McpToolSearchResult> findCompatibleCandidates(String rawGroupId, String sourceArtifactId,
            PreparedProducer producer) {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_TOOL));

        ArtifactSearchResultsDto candidateResults = searchAuthorizer.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 0, MAX_COMPATIBLE_CANDIDATE_SCAN, false);

        if (candidateResults.getCount() >= MAX_COMPATIBLE_CANDIDATE_SCAN) {
            log.warn("Compatible-tools candidate scan reached the cap of {}; results beyond this"
                    + " limit are not evaluated. Consider raising MAX_COMPATIBLE_CANDIDATE_SCAN"
                    + " or implementing storage-side filtering.", MAX_COMPATIBLE_CANDIDATE_SCAN);
        }

        List<McpToolSearchResult> compatibleTools = new ArrayList<>();
        for (SearchedArtifactDto candidate : candidateResults.getArtifacts()) {
            if (sourceArtifactId.equals(candidate.getArtifactId())
                    && isSameGroup(rawGroupId, candidate.getGroupId())) {
                continue;
            }
            Optional<StoredArtifactVersionDto> candidateStored = fetchLatestCandidateContent(candidate);
            if (candidateStored.isEmpty()) {
                continue;
            }
            JsonNode candidateRoot = readToolRoot(candidateStored.get()).orElse(null);
            PairCompatibility compatibility = candidateRoot == null
                    ? crossToolCompatibility.unreadableConsumer(producer)
                    : crossToolCompatibility.compare(producer, candidateRoot);
            if (compatibility.verdict() == CompatibilityVerdict.COMPATIBLE) {
                compatibleTools.add(convertToMcpToolSearchResultFromContent(candidate, candidateRoot));
            }
        }
        return compatibleTools;
    }

    /**
     * Fetches the content of the candidate's latest enabled version. A candidate that has no
     * enabled version, or that was deleted after the search, is skipped. Any other storage
     * failure propagates.
     */
    private Optional<StoredArtifactVersionDto> fetchLatestCandidateContent(SearchedArtifactDto candidate) {
        try {
            GA candidateGa = new GA(candidate.getGroupId(), candidate.getArtifactId());
            GAV candidateGav = VersionExpressionParser.parse(candidateGa, "branch=latest",
                    (g, branchId) -> storage.getBranchTip(g, branchId,
                            RetrievalBehavior.SKIP_DISABLED_LATEST));
            return Optional.of(storage.getArtifactVersionContent(candidateGav.getRawGroupIdWithNull(),
                    candidateGav.getRawArtifactId(), candidateGav.getRawVersionId()));
        } catch (ArtifactNotFoundException | VersionNotFoundException e) {
            log.debug("Skipping compatible-tools candidate {}/{}: {}", candidate.getGroupId(),
                    candidate.getArtifactId(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isSameGroup(String sourceGroupId, String candidateGroupId) {
        return (sourceGroupId == null && candidateGroupId == null)
                || (sourceGroupId != null && sourceGroupId.equals(candidateGroupId));
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
}
