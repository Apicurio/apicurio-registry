package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchConfig;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchService;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Storage decorator that intercepts version search requests and routes them through the
 * Elasticsearch search index when enabled. Falls back to the underlying SQL-based search
 * when Elasticsearch is disabled, not yet initialized, or when filters contain types that
 * Elasticsearch cannot handle.
 */
@ApplicationScoped
public class ElasticsearchSearchDecorator extends RegistryStorageDecoratorBase
        implements RegistryStorageDecorator {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchDecorator.class);

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
     * Intercepts version search requests. When the Elasticsearch index is initialized and all
     * filters are ES-compatible, the search is executed against the ES index. Otherwise, the
     * request falls through to the underlying storage (SQL). If the filters include
     * index-only types (e.g. content search) and ES is unavailable, an error is thrown rather
     * than silently returning incorrect results from SQL.
     */
    @Override
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit)
            throws RegistryStorageException {
        if (startupIndexer.isReady() && searchService.canHandleFilters(filters)) {
            try {
                return searchService.searchVersions(filters, orderBy, orderDirection,
                        offset, limit);
            } catch (Exception e) {
                log.warn("Elasticsearch search failed, falling back to SQL storage", e);
            }
        }
        // If the filters require the search index but we can't use it, fail explicitly
        if (searchService.requiresSearchIndex(filters)) {
            throw new RegistryStorageException(
                    "Content search requires the Elasticsearch search index, which is not "
                    + "available. Enable the Elasticsearch search index to use content search.");
        }
        // Fall through to SQL
        return delegate.searchVersions(filters, orderBy, orderDirection, offset, limit);
    }
}
