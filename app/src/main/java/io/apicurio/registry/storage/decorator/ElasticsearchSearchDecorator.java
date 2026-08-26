package io.apicurio.registry.storage.decorator;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ContentSearchNotSupportedException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchConfig;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchService;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Storage decorator that intercepts version search requests and routes them through the
 * Elasticsearch search index only when the search filters require it (e.g. content or
 * structure filters). All other searches are handled by the underlying SQL-based storage.
 */
@ApplicationScoped
public class ElasticsearchSearchDecorator extends RegistryStorageDecoratorBase
        implements RegistryStorageDecorator {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchDecorator.class);

    private static final String INDEX_NOT_AVAILABLE_MESSAGE =
            "Content search requires the Elasticsearch search index, which is not "
            + "available. Enable the Elasticsearch search index to use content search.";

    @Inject
    ElasticsearchSearchConfig config;

    @Inject
    ElasticsearchSearchService searchService;

    @Inject
    ElasticsearchStartupIndexer startupIndexer;

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public int order() {
        return 55; // Before SearchIndexEventDecorator (60)
    }

    /**
     * Intercepts version search requests. Only routes through Elasticsearch when the filters
     * require the search index (e.g. content or structure filters). All other searches fall
     * through to the underlying SQL-based storage.
     */
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit, boolean skipCount)
            throws RegistryStorageException {
        if (searchService.requiresSearchIndex(filters)) {
            if (!startupIndexer.isReady()) {
                throw new ContentSearchNotSupportedException(INDEX_NOT_AVAILABLE_MESSAGE);
            }
            try {
                return searchService.searchVersions(filters, orderBy, orderDirection,
                        offset, limit, skipCount);
            } catch (IOException | ElasticsearchException e) {
                throw new RegistryStorageException(
                        "Elasticsearch search failed for index-only filters.", e);
            }
        }
        return delegate.searchVersions(filters, orderBy, orderDirection, offset, limit, skipCount);
    }

    /**
     * Intercepts artifact search requests that include index-only filters (content or structure,
     * e.g. A2A agent skill/capability filters). The search index resolves which artifacts have a
     * matching version, while the underlying SQL storage evaluates the remaining filters and
     * supplies the authoritative artifact metadata (name, labels, timestamps) and ordering; the
     * two result sets are intersected and paginated in memory. Searches without index-only
     * filters fall through to the underlying SQL-based storage unchanged.
     *
     * <p>Only the index-only filters (plus the artifact-type filter, which has identical
     * exact-match semantics in both backends) are evaluated by the index; every other filter is
     * evaluated by SQL. This keeps filters with backend-specific semantics (wildcard-wrapped
     * name matching, case-normalized labels) on the SQL side, where the behavior matches
     * non-index searches.</p>
     *
     * <p><b>Hard cap (part of the search contract):</b> both the index scan and the SQL scan
     * retrieve at most {@link ElasticsearchSearchService#MAX_ARTIFACT_SEARCH_HITS} candidates,
     * and the returned result count is computed from their intersection. When more artifacts
     * than the cap match, results beyond the cap are omitted and the count understates the true
     * total — a paginating client will stop early. A warning is logged when either scan hits
     * the cap. Callers should scope index-backed searches (e.g. with an artifact-type filter,
     * as the well-known A2A endpoints always do) so the candidate sets stay below the cap; a
     * request carrying only index-only filters leaves the SQL scan unscoped and it degrades to
     * a full-catalog scan up to the cap.</p>
     */
    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit, boolean skipCount)
            throws RegistryStorageException {
        if (!searchService.requiresSearchIndex(filters)) {
            return delegate.searchArtifacts(filters, orderBy, orderDirection, offset, limit,
                    skipCount);
        }
        if (!startupIndexer.isReady()) {
            throw new ContentSearchNotSupportedException(INDEX_NOT_AVAILABLE_MESSAGE);
        }

        Set<SearchFilter> esFilters = new HashSet<>();
        Set<SearchFilter> sqlFilters = new HashSet<>();
        for (SearchFilter filter : filters) {
            if (searchService.isIndexOnlyFilter(filter)) {
                esFilters.add(filter);
            } else {
                sqlFilters.add(filter);
                if (filter.getType() == SearchFilterType.artifactType) {
                    esFilters.add(filter);
                }
            }
        }

        Set<String> matchedIdentities;
        try {
            matchedIdentities = searchService.searchArtifactIdentities(esFilters);
        } catch (IOException | ElasticsearchException e) {
            throw new RegistryStorageException(
                    "Elasticsearch search failed for index-only filters.", e);
        }

        ArtifactSearchResultsDto results = new ArtifactSearchResultsDto();
        if (matchedIdentities.isEmpty()) {
            return results;
        }

        ArtifactSearchResultsDto sqlResults = delegate.searchArtifacts(sqlFilters, orderBy,
                orderDirection, 0, ElasticsearchSearchService.MAX_ARTIFACT_SEARCH_HITS, true);
        if (sqlResults.getArtifacts().size() >= ElasticsearchSearchService.MAX_ARTIFACT_SEARCH_HITS) {
            log.warn("Artifact search SQL scan reached the cap of {} artifacts; matches beyond "
                    + "the cap are not included and the reported count may understate the true "
                    + "total.", ElasticsearchSearchService.MAX_ARTIFACT_SEARCH_HITS);
        }

        List<SearchedArtifactDto> matched = new ArrayList<>();
        for (SearchedArtifactDto artifact : sqlResults.getArtifacts()) {
            if (matchedIdentities.contains(ElasticsearchSearchService.identityKey(
                    artifact.getGroupId(), artifact.getArtifactId()))) {
                matched.add(artifact);
            }
        }

        int total = matched.size();
        int fromIndex = Math.min(Math.max(0, offset), total);
        int toIndex = Math.min(fromIndex + Math.max(0, limit), total);
        results.setArtifacts(new ArrayList<>(matched.subList(fromIndex, toIndex)));
        results.setCount(skipCount ? 0 : total);
        return results;
    }
}
