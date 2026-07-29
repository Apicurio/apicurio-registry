package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ContentSearchNotSupportedException;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchConfig;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchService;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ElasticsearchSearchDecoratorTest {

    private ElasticsearchSearchConfig config;
    private ElasticsearchSearchService searchService;
    private ElasticsearchStartupIndexer startupIndexer;
    private RegistryStorage delegate;
    private ElasticsearchSearchDecorator decorator;

    @BeforeEach
    void setUp() {
        config = mock(ElasticsearchSearchConfig.class);
        searchService = mock(ElasticsearchSearchService.class);
        startupIndexer = mock(ElasticsearchStartupIndexer.class);
        delegate = mock(RegistryStorage.class);

        decorator = new ElasticsearchSearchDecorator();
        decorator.config = config;
        decorator.searchService = searchService;
        decorator.startupIndexer = startupIndexer;
        decorator.setDelegate(delegate);
    }

    @Test
    void searchVersionsThrowsContentSearchNotSupportedWhenIndexerNotReady() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("test"));
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(false);

        ContentSearchNotSupportedException exception = assertThrows(ContentSearchNotSupportedException.class,
                () -> decorator.searchVersions(filters, OrderBy.name, OrderDirection.asc, 0, 10, false));

        assertEquals("Content search requires the Elasticsearch search index, which is not "
                + "available. Enable the Elasticsearch search index to use content search.",
                exception.getMessage());
        verifyNoInteractions(delegate);
    }

    @Test
    void searchVersionsDelegatesToSearchServiceWhenIndexerReady() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("test"));
        VersionSearchResultsDto results = new VersionSearchResultsDto();
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(searchService.searchVersions(filters, OrderBy.name, OrderDirection.asc, 0, 10, false))
                .thenReturn(results);

        VersionSearchResultsDto actual = decorator.searchVersions(filters, OrderBy.name, OrderDirection.asc,
                0, 10, false);

        assertEquals(results, actual);
        verifyNoInteractions(delegate);
    }

    @Test
    void searchVersionsFallsThroughToDelegateWhenIndexNotRequired() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofName("test"));
        VersionSearchResultsDto results = new VersionSearchResultsDto();
        when(searchService.requiresSearchIndex(filters)).thenReturn(false);
        when(delegate.searchVersions(filters, OrderBy.name, OrderDirection.asc, 0, 10, false))
                .thenReturn(results);

        VersionSearchResultsDto actual = decorator.searchVersions(filters, OrderBy.name, OrderDirection.asc,
                0, 10, false);

        assertEquals(results, actual);
        verifyNoInteractions(startupIndexer);
    }
}
