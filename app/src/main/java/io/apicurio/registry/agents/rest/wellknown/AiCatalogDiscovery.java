package io.apicurio.registry.agents.rest.wellknown;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.apicurio.registry.agents.aicatalog.AiCatalogConfig;
import io.apicurio.registry.agents.aicatalog.AiCatalogConstants;
import io.apicurio.registry.agents.ard.ArdConfig;
import io.apicurio.registry.auth.ISearchAuthorizer;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.model.GA;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.model.VersionExpressionParser;
import io.apicurio.registry.rest.v3.beans.AiCatalog;
import io.apicurio.registry.rest.v3.beans.AiCatalogEntry;
import io.apicurio.registry.rest.v3.beans.AiCatalogHost;
import io.apicurio.registry.rest.v3.beans.ArdAgentsResponse;
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
import io.apicurio.registry.storage.RegistryStorage.RetrievalBehavior;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI Catalog and Agent Resource Discovery (ARD) endpoints, plus the candidate collection shared with agent search.
 */
@ApplicationScoped
public class AiCatalogDiscovery {

    private static final Logger log = LoggerFactory.getLogger(AiCatalogDiscovery.class);

    static final ObjectMapper mapper = new ObjectMapper();

    static final int MAX_VISIBILITY_FILTER_RESULTS = 10000;

    private static final int MAX_REPRESENTATIVE_QUERIES = 5;

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
    AiCatalogConfig aiCatalogConfig;

    @Inject
    ArdConfig ardConfig;

    @Inject
    @Current
    RegistryStorage storage;

    /** Discovery lists registry artifacts, so it is restricted by per-resource authorization. */
    @Inject
    ISearchAuthorizer searchAuthorizer;

    @Inject
    AgentCardDiscovery agentCardDiscovery;

    @Inject
    AgentVisibilityFilter agentVisibilityFilter;

    @Inject
    WellKnownRequestSupport wellKnownRequestSupport;

    public AiCatalog getAiCatalog() {
        if (!aiCatalogConfig.isEnabled()) {
            throw new NotFoundException("AI Catalog support is disabled");
        }

        String baseUrl = wellKnownRequestSupport.getBaseUrl();
        String publisherDomain = wellKnownRequestSupport.resolvePublisherDomain();

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

    public AiCatalog getArdManifest() {
        return getAiCatalog();
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

        String baseUrl = wellKnownRequestSupport.getBaseUrl();
        String publisherDomain = wellKnownRequestSupport.resolvePublisherDomain();

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

    public ArdAgentsResponse ardListAgents(String filter, String orderBy, Integer pageSize, String pageToken) {
        if (!ardConfig.isEnabled()) {
            throw new NotFoundException("ARD support is disabled");
        }

        // "orderBy" is currently a no-op: entries are already produced in a deterministic
        // (createdOn desc) order by the underlying storage query. The parameter is accepted
        // so ARD clients that always send it are not rejected.
        Map<String, List<String>> filters = parseArdAgentsFilter(filter);

        String baseUrl = wellKnownRequestSupport.getBaseUrl();
        String publisherDomain = wellKnownRequestSupport.resolvePublisherDomain();

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

        ArdAgentsResponse response = new ArdAgentsResponse();
        response.setItems(entries);
        response.setTotal(total);
        response.setPageToken(nextPageToken);

        return response;
    }

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

        String baseUrl = wellKnownRequestSupport.getBaseUrl();
        String publisherDomain = wellKnownRequestSupport.resolvePublisherDomain();

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
     * layer. Agent Card visibility labels are respected (this is the sole visibility-filtering
     * implementation in this class, also backing {@code searchAgents}); MCP tools carry no
     * visibility label, so read-level authorization is the sole gate (mirroring
     * {@code searchMcpTools}).
     */
    List<AiCatalogCandidate> collectAiCatalogCandidates(String baseUrl, String publisherDomain,
            String textFilter) {
        return collectAiCatalogCandidates(baseUrl, publisherDomain, textFilter, Set.of());
    }

    List<AiCatalogCandidate> collectAiCatalogCandidates(String baseUrl, String publisherDomain,
            String textFilter, Set<SearchFilter> structureFilters) {
        List<AiCatalogCandidate> candidates = new ArrayList<>();

        Set<SearchFilter> agentFilters = new HashSet<>();
        agentFilters.addAll(structureFilters);
        agentFilters.add(SearchFilter.ofArtifactType(ArtifactType.AGENT_CARD));
        if (!StringUtil.isEmpty(textFilter)) {
            agentFilters.add(SearchFilter.ofPartialName(textFilter));
        }
        ArtifactSearchResultsDto agentResults = searchAuthorizer.searchArtifacts(
                agentFilters, OrderBy.createdOn, OrderDirection.desc, 0, MAX_VISIBILITY_FILTER_RESULTS, false);
        warnIfTruncated(agentResults);
        for (SearchedArtifactDto artifact : agentVisibilityFilter.filterDtosByVisibility(agentResults.getArtifacts())) {
            candidates.add(buildAgentCandidate(artifact, baseUrl, publisherDomain));
        }

        Set<SearchFilter> toolFilters = new HashSet<>();
        toolFilters.add(SearchFilter.ofArtifactType(ArtifactType.MCP_TOOL));
        if (!StringUtil.isEmpty(textFilter)) {
            toolFilters.add(SearchFilter.ofPartialName(textFilter));
        }
        ArtifactSearchResultsDto toolResults = searchAuthorizer.searchArtifacts(
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
        List<String> capabilities = agentCardDiscovery.extractAgentCardSkillIds(root);
        List<String> representativeQueries = extractRepresentativeQueries(root);
        String groupSegment = groupIdSegment(artifact.getGroupId());

        AiCatalogEntry entry = AiCatalogEntry.builder()
                .identifier(buildAirIdentifier(publisherDomain, groupSegment, artifact.getArtifactId()))
                .displayName(displayName)
                .type(AiCatalogConstants.MEDIA_TYPE_AGENT_CARD)
                .url(baseUrl + "/.well-known/agents/" + groupSegment + "/" + artifact.getArtifactId())
                .description(artifact.getDescription())
                .capabilities(capabilities)
                .version(version)
                .updatedAt(formatUpdatedAt(artifact))
                .tags(formatTags(artifact.getLabels()))
                .representativeQueries(representativeQueries)
                .build();
        return new AiCatalogCandidate(entry, artifact.getLabels(), artifact);
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
                .updatedAt(formatUpdatedAt(artifact))
                .tags(formatTags(artifact.getLabels()))
                .build();
        return new AiCatalogCandidate(entry, artifact.getLabels(), artifact);
    }

    /**
     * Formats an artifact's modification timestamp as an ISO-8601 instant string for the
     * {@code AiCatalogEntry.updatedAt} field. Returns {@code null} if the artifact has no
     * recorded modification timestamp.
     */
    private String formatUpdatedAt(SearchedArtifactDto artifact) {
        if (artifact.getModifiedOn() == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(artifact.getModifiedOn().toInstant());
    }

    /**
     * Formats an artifact's labels as {@code key=value} strings for the
     * {@code AiCatalogEntry.tags} field, consistent with the exact-match form that
     * {@link #matchesTag(Map, String)} accepts for the ARD {@code tags} filter. Returns an
     * empty list if the artifact has no labels.
     */
    private List<String> formatTags(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        for (Map.Entry<String, String> label : labels.entrySet()) {
            tags.add(label.getKey() + "=" + label.getValue());
        }
        return tags;
    }

    /**
     * Extracts up to {@value #MAX_REPRESENTATIVE_QUERIES} sample natural-language queries
     * from an Agent Card's {@code skills[].examples} field, per ARD §4.2/§D.2. Returns
     * {@code null} (leaving {@code representativeQueries} unset) if no skill declares any
     * examples, rather than fabricating queries from the display name or description.
     */
    private List<String> extractRepresentativeQueries(JsonNode root) {
        List<String> queries = new ArrayList<>();
        JsonNode skillsNode = root.path("skills");
        if (skillsNode.isArray()) {
            for (JsonNode skill : skillsNode) {
                JsonNode examplesNode = skill.path("examples");
                if (examplesNode.isArray()) {
                    for (JsonNode example : examplesNode) {
                        if (example.isTextual()) {
                            queries.add(example.asText());
                            if (queries.size() >= MAX_REPRESENTATIVE_QUERIES) {
                                return queries;
                            }
                        }
                    }
                }
            }
        }
        return queries.isEmpty() ? null : queries;
    }

    /**
     * Fetches and parses the latest version content for an artifact. Returns a
     * {@link MissingNode} (rather than throwing) on any failure so callers can safely chain
     * {@code .path(...)} lookups without null-checking.
     */
    JsonNode readLatestContent(SearchedArtifactDto artifact) {
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

    private void warnIfTruncated(ArtifactSearchResultsDto results) {
        if (results.getCount() >= MAX_VISIBILITY_FILTER_RESULTS) {
            log.warn("Agent visibility filtering may be incomplete: total agent count ({}) "
                    + "reached the in-memory limit of {}. Results beyond this limit are not included.",
                    results.getCount(), MAX_VISIBILITY_FILTER_RESULTS);
        }
    }

    /**
     * A candidate AI Catalog entry paired with the originating artifact's labels, so ARD
     * filters that inspect labels (e.g. {@code tags}) can be evaluated without a second
     * storage round-trip.
     */
    static final class AiCatalogCandidate {
        final AiCatalogEntry entry;
        final Map<String, String> labels;
        final SearchedArtifactDto artifact;

        private AiCatalogCandidate(AiCatalogEntry entry, Map<String, String> labels,
                SearchedArtifactDto artifact) {
            this.entry = entry;
            this.labels = labels;
            this.artifact = artifact;
        }
    }
}
