package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.search.LuceneIndexSearcher;
import io.apicurio.registry.storage.impl.search.LuceneSearchConfig;
import io.apicurio.registry.storage.impl.search.LuceneSearchService;
import io.apicurio.registry.storage.impl.search.LuceneStartupIndexer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Storage decorator that intercepts version search requests and routes them through the Lucene
 * search index when enabled. Falls back to the underlying SQL-based search when Lucene is
 * disabled, not yet initialized, or when filters contain types that Lucene cannot handle.
 */
@ApplicationScoped
public class LuceneSearchDecorator extends RegistryStorageDecoratorBase
        implements RegistryStorageDecorator {

    private static final Logger log = LoggerFactory.getLogger(LuceneSearchDecorator.class);

    @Inject
    LuceneSearchConfig config;

    @Inject
    LuceneSearchService searchService;

    @Inject
    LuceneIndexSearcher indexSearcher;

    @Inject
    LuceneStartupIndexer startupIndexer;

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public int order() {
        return 55; // Before SearchIndexEventDecorator (60)
    }

    /**
     * Intercepts version search requests. When the Lucene index is initialized and all filters
     * are Lucene-compatible, the search is executed against the Lucene index. Otherwise, the
     * request falls through to the underlying storage (SQL).
     */
    @Override
    public VersionSearchResultsDto searchVersions(Set<SearchFilter> filters, OrderBy orderBy,
            OrderDirection orderDirection, int offset, int limit) throws RegistryStorageException {
        if (startupIndexer.isReady() && indexSearcher.isInitialized()
                && searchService.canHandleFilters(filters)) {
            try {
                return searchService.searchVersions(filters, orderBy, orderDirection,
                        offset, limit);
            } catch (Exception e) {
                log.warn("Lucene search failed, falling back to SQL storage", e);
            }
        }
        // Fall through to SQL
        return delegate.searchVersions(filters, orderBy, orderDirection, offset, limit);
    }
}
