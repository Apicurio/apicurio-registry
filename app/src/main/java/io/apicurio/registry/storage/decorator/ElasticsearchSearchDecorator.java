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

import java.io.IOException;
import java.util.Set;

/**
 * Storage decorator that intercepts version search requests and routes them through the
 * Elasticsearch search index only when the search filters require it (e.g. content or
 * structure filters). All other searches are handled by the underlying SQL-based storage.
 */
@ApplicationScoped
public class ElasticsearchSearchDecorator extends RegistryStorageDecoratorBase
        implements RegistryStorageDecorator {

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
            OrderDirection orderDirection, int offset, int limit)
            throws RegistryStorageException {
        if (searchService.requiresSearchIndex(filters)) {
            if (!startupIndexer.isReady()) {
                throw new RegistryStorageException(
                        "Content search requires the Elasticsearch search index, which is not "
                        + "available. Enable the Elasticsearch search index to use content search.");
            }
            try {
                return searchService.searchVersions(filters, orderBy, orderDirection,
                        offset, limit);
            } catch (IOException e) {
                throw new RegistryStorageException(
                        "Elasticsearch search failed for index-only filters.", e);
            }
        }
        return delegate.searchVersions(filters, orderBy, orderDirection, offset, limit);
    }
}
