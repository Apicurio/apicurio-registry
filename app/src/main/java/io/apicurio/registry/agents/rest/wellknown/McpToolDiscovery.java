package io.apicurio.registry.agents.rest.wellknown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.apicurio.registry.agents.mcptools.McpToolsConfig;
import io.apicurio.registry.agents.mcptools.compatibility.CompatibilityLimitation;
import io.apicurio.registry.agents.mcptools.compatibility.CompatibilityReason;
import io.apicurio.registry.agents.mcptools.compatibility.CompatibilityVerdict;
import io.apicurio.registry.agents.mcptools.compatibility.CrossToolCompatibilityService;
import io.apicurio.registry.agents.mcptools.compatibility.PairCompatibility;
import io.apicurio.registry.agents.mcptools.compatibility.PreparedProducer;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.ISearchAuthorizer;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.rest.InvalidParameterValueException;
import io.apicurio.registry.rest.ParameterValidationUtils;
import io.apicurio.registry.rest.v3.beans.McpCompatibleToolsResults;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityLimitation;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityReason;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityResult;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityResults;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilitySide;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityTotals;
import io.apicurio.registry.rest.v3.beans.McpToolCompatibilityVerdict;
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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
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

    /**
     * The page size of the compatible-tools operations when the request gives none.
     */
    private static final BigInteger DEFAULT_LIMIT = BigInteger.valueOf(20);

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
            String version, BigInteger offset, BigInteger limit) {
        if (!mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("MCP tools support is disabled");
        }

        PreparedProducer producer = prepareSource(groupId, artifactId, version);

        if (!producer.canMatch()) {
            return McpCompatibleToolsResults.builder().count(0).tools(Collections.emptyList()).build();
        }

        String rawGroupId = new GroupId(groupId).getRawGroupIdWithNull();
        List<McpToolSearchResult> compatibleTools = scanCandidates(rawGroupId, artifactId, producer,
                MAX_COMPATIBLE_CANDIDATE_SCAN).evaluated().stream()
                .filter(candidate -> candidate.compatibility().verdict() == CompatibilityVerdict.COMPATIBLE)
                .map(EvaluatedCandidate::tool)
                .toList();

        return McpCompatibleToolsResults.builder().count(compatibleTools.size())
                .tools(page(compatibleTools, offset, limit)).build();
    }

    public McpToolCompatibilityResults getToolCompatibility(String groupId, String artifactId,
            String version, List<String> verdict, BigInteger offset, BigInteger limit) {
        return getToolCompatibility(groupId, artifactId, version, verdict, offset, limit,
                MAX_COMPATIBLE_CANDIDATE_SCAN);
    }

    /**
     * Compares the source tool with the candidates among the newest {@code scanLimit} MCP tools. The
     * totals count every evaluated candidate, and {@code offset} and {@code limit} page through the
     * ones that have a requested verdict.
     */
    McpToolCompatibilityResults getToolCompatibility(String groupId, String artifactId,
            String version, List<String> verdict, BigInteger offset, BigInteger limit, int scanLimit) {
        if (!mcpToolsConfig.isEnabled()) {
            throw new NotFoundException("MCP tools support is disabled");
        }
        Set<McpToolCompatibilityVerdict> requested = requestedVerdicts(verdict);

        PreparedProducer producer = prepareSource(groupId, artifactId, version);

        CandidateScan scan = producer.canMatch()
                ? scanCandidates(new GroupId(groupId).getRawGroupIdWithNull(), artifactId, producer, scanLimit)
                : CandidateScan.NONE;
        List<McpToolCompatibilityResult> results = scan.evaluated().stream()
                .map(McpToolDiscovery::toResult)
                .toList();
        List<McpToolCompatibilityResult> matching = results.stream()
                .filter(result -> requested.contains(result.getVerdict()))
                .toList();

        return McpToolCompatibilityResults.builder()
                .candidateTotal((int) scan.candidateTotal())
                .evaluated(results.size())
                .scanLimit(scanLimit)
                .complete(scan.complete())
                .totals(McpToolCompatibilityTotals.builder()
                        .compatible(count(results, McpToolCompatibilityVerdict.COMPATIBLE))
                        .incompatible(count(results, McpToolCompatibilityVerdict.INCOMPATIBLE))
                        .indeterminate(count(results, McpToolCompatibilityVerdict.INDETERMINATE))
                        .build())
                .count(matching.size())
                .limitations(producer.canMatch() ? List.of() : toLimitations(producer.limitations()))
                .results(page(matching, offset, limit))
                .build();
    }

    /**
     * The verdicts to return, all of them when none is given. The parameter is declared as a
     * string, because a value that fails parameter conversion is answered with a 404, which a
     * caller could not tell apart from a missing source tool.
     */
    private static Set<McpToolCompatibilityVerdict> requestedVerdicts(List<String> verdicts) {
        if (verdicts == null || verdicts.isEmpty()) {
            return EnumSet.allOf(McpToolCompatibilityVerdict.class);
        }
        Set<McpToolCompatibilityVerdict> requested = EnumSet.noneOf(McpToolCompatibilityVerdict.class);
        for (String verdict : verdicts) {
            try {
                requested.add(McpToolCompatibilityVerdict.fromValue(verdict));
            } catch (IllegalArgumentException e) {
                throw new InvalidParameterValueException("verdict",
                        "COMPATIBLE, INCOMPATIBLE or INDETERMINATE", verdict);
            }
        }
        return requested;
    }

    private PreparedProducer prepareSource(String groupId, String artifactId, String version) {
        StoredArtifactVersionDto sourceArtifact = fetchMcpToolArtifact(groupId, artifactId, version);
        return readToolRoot(sourceArtifact)
                .map(crossToolCompatibility::prepareProducer)
                .orElseGet(crossToolCompatibility::unreadableProducer);
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
     * Scans the newest {@code scanLimit} MCP tools and compares the source tool with every other one
     * of them, as decided by {@link CrossToolCompatibilityService}. A candidate that
     * {@link #fetchLatestCandidateContent} skips counts as scanned but is not evaluated, and one
     * whose content is not JSON is evaluated as {@link CompatibilityVerdict#INDETERMINATE}.
     *
     * <p><b>Authorization note:</b> candidate identity and metadata, and the reasons and
     * limitations of each comparison, are exposed to a caller who may read the source tool
     * ({@link AuthorizedLevel#Read}), but only for the candidates that caller may read: as in
     * {@code searchMcpTools}, the search goes through {@link ISearchAuthorizer}, which with
     * per-resource authorization leaves the other MCP tools out of the results and out of the
     * count. No per-artifact visibility label is applied because MCP tools (unlike A2A agents) do
     * not carry an {@code apicurio.agent.visibility} label.
     */
    private CandidateScan scanCandidates(String rawGroupId, String sourceArtifactId,
            PreparedProducer producer, int scanLimit) {
        Set<SearchFilter> filters = new HashSet<>();
        filters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_TOOL));

        ArtifactSearchResultsDto candidateResults = searchAuthorizer.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 0, scanLimit, false);

        int scanned = 0;
        List<EvaluatedCandidate> evaluated = new ArrayList<>();
        for (SearchedArtifactDto candidate : candidateResults.getArtifacts()) {
            if (sourceArtifactId.equals(candidate.getArtifactId())
                    && isSameGroup(rawGroupId, candidate.getGroupId())) {
                continue;
            }
            scanned++;
            Optional<StoredArtifactVersionDto> candidateStored = fetchLatestCandidateContent(candidate);
            if (candidateStored.isEmpty()) {
                continue;
            }
            Optional<JsonNode> candidateRoot = readToolRoot(candidateStored.get());
            PairCompatibility compatibility = candidateRoot
                    .map(root -> crossToolCompatibility.compare(producer, root))
                    .orElseGet(() -> crossToolCompatibility.unreadableConsumer(producer));
            evaluated.add(new EvaluatedCandidate(convertToMcpToolSearchResultFromContent(candidate,
                    candidateRoot.orElse(MissingNode.getInstance())), compatibility));
        }

        // The count includes the source tool, which is never scanned as a candidate.
        boolean complete = scanned >= candidateResults.getCount() - 1;
        if (!complete) {
            log.warn("Compatible-tools candidate scan reached the cap of {}; results beyond this"
                    + " limit are not evaluated. Consider raising MAX_COMPATIBLE_CANDIDATE_SCAN"
                    + " or implementing storage-side filtering.", scanLimit);
        }
        return new CandidateScan(candidateResults.getCount(), evaluated, complete);
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

    /**
     * Returns one page of the results. As in the v3 search operations, an offset or limit sent
     * without a value takes its default, and both are normalized so that neither can overflow
     * however large the client sends them.
     */
    private static <T> List<T> page(List<T> results, BigInteger offset, BigInteger limit) {
        BigInteger first = offset == null ? BigInteger.ZERO : offset;
        BigInteger size = limit == null ? DEFAULT_LIMIT : limit;
        int fromIndex = Math.min(ParameterValidationUtils.normalizeOffset(first), results.size());
        int toIndex = Math.min(fromIndex + ParameterValidationUtils.normalizeLimit(size), results.size());
        return results.subList(fromIndex, toIndex);
    }

    private static McpToolCompatibilityResult toResult(EvaluatedCandidate candidate) {
        PairCompatibility compatibility = candidate.compatibility();
        return McpToolCompatibilityResult.builder()
                .tool(candidate.tool())
                .verdict(McpToolCompatibilityVerdict.valueOf(compatibility.verdict().name()))
                .reasons(compatibility.reasons().stream().map(McpToolDiscovery::toReason).toList())
                .limitations(toLimitations(compatibility.limitations()))
                .build();
    }

    private static McpToolCompatibilityReason toReason(CompatibilityReason reason) {
        return McpToolCompatibilityReason.builder()
                .code(reason.code().name())
                .producerPointer(reason.producerPointer())
                .consumerPointer(reason.consumerPointer())
                .message(reason.message())
                .build();
    }

    private static List<McpToolCompatibilityLimitation> toLimitations(List<CompatibilityLimitation> limitations) {
        return limitations.stream().map(McpToolDiscovery::toLimitation).toList();
    }

    private static McpToolCompatibilityLimitation toLimitation(CompatibilityLimitation limitation) {
        return McpToolCompatibilityLimitation.builder()
                .code(limitation.code().name())
                .side(McpToolCompatibilitySide.valueOf(limitation.side().name()))
                .pointer(limitation.pointer())
                .message(limitation.message())
                .build();
    }

    private static int count(List<McpToolCompatibilityResult> results, McpToolCompatibilityVerdict verdict) {
        return (int) results.stream().filter(result -> result.getVerdict() == verdict).count();
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
     * <p>Called from {@link #scanCandidates} so that each evaluated candidate
     * is converted using the {@link JsonNode} obtained during the compatibility check,
     * keeping the per-candidate storage cost to a single round-trip. A candidate whose content
     * is not JSON is passed a {@link MissingNode}, so it is listed without a title or parameters.
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

    /**
     * A candidate tool and the result of comparing the source tool with it.
     */
    private record EvaluatedCandidate(McpToolSearchResult tool, PairCompatibility compatibility) {
    }

    /**
     * The candidates a scan evaluated, newest first, and the number of MCP tools its search
     * counted, which includes the source tool. {@link #NONE} stands for a source tool that cannot
     * be compared, for which nothing is scanned.
     */
    private record CandidateScan(long candidateTotal, List<EvaluatedCandidate> evaluated, boolean complete) {

        static final CandidateScan NONE = new CandidateScan(0, List.of(), true);
    }
}
