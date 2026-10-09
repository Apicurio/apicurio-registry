package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.dto.AuthorizationNames;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchFilterType;
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
import java.util.HashSet;
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

    /** Upper bound on owned artifacts resolved for an index search restricted by authorization. */
    static final int MAX_OWNED_ARTIFACTS = 1000;

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
                throw new ContentSearchNotSupportedException(
                        "Content search requires the Elasticsearch search index, which is not "
                        + "available. Enable the Elasticsearch search index to use content search.");
            }
            try {
                return searchService.searchVersions(resolveOwnership(filters), orderBy, orderDirection,
                        offset, limit, skipCount);
            } catch (IOException e) {
                throw new RegistryStorageException(
                        "Elasticsearch search failed for index-only filters.", e);
            }
        }
        return delegate.searchVersions(filters, orderBy, orderDirection, offset, limit, skipCount);
    }

    public ArtifactSearchResultsDto searchArtifacts(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit, boolean skipCount)
            throws RegistryStorageException {
        if (searchService.requiresSearchIndex(filters)) {
            if (!startupIndexer.isReady()) {
                throw new ContentSearchNotSupportedException(
                        "Content search requires the Elasticsearch search index, which is not "
                        + "available. Enable the Elasticsearch search index to use content search.");
            }
            try {
                return searchService.searchArtifacts(resolveOwnership(filters), orderBy, orderDirection,
                        offset, limit, skipCount);
            } catch (IOException e) {
                throw new RegistryStorageException(
                        "Elasticsearch search failed for index-only filters.", e);
            }
        }
        return delegate.searchArtifacts(filters, orderBy, orderDirection, offset, limit, skipCount);
    }

    /**
     * The index does not store artifact ownership (and would go stale on ownership transfers), so
     * per-resource authorization owner matches are resolved from the primary storage and passed to
     * the index query as artifact names.
     */
    private Set<SearchFilter> resolveOwnership(Set<SearchFilter> filters) {
        Set<SearchFilter> resolved = new HashSet<>();
        for (SearchFilter filter : filters) {
            if (filter.getType() == SearchFilterType.authorization
                    && filter.getAuthorizationValue().owner() != null) {
                AuthorizationFilter authorization = filter.getAuthorizationValue();
                ArtifactSearchResultsDto owned = delegate.searchArtifacts(
                        Set.of(SearchFilter.ofAuthorization(AuthorizationFilter.ownedBy(authorization.owner()))),
                        OrderBy.name, OrderDirection.asc, 0, MAX_OWNED_ARTIFACTS, true);
                if (owned.getArtifacts().size() >= MAX_OWNED_ARTIFACTS) {
                    log.warn("Caller owns more than {} artifacts; index searches include only the first {}.",
                            MAX_OWNED_ARTIFACTS, MAX_OWNED_ARTIFACTS);
                }
                Set<String> names = new HashSet<>();
                owned.getArtifacts().forEach(a -> names.add(AuthorizationNames.artifact(a.getGroupId(),
                        a.getArtifactId())));
                resolved.add(SearchFilter.ofAuthorization(authorization.withOwnedArtifacts(names)));
            } else {
                resolved.add(filter);
            }
        }
        return resolved;
    }
}
