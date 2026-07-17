package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.error.ContentSearchNotSupportedException;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchService;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ElasticsearchSearchDecoratorTest {

    @Test
    public void testContentSearchWhenIndexNotReadyThrowsContentSearchNotSupported() {
        ElasticsearchSearchService searchService = mock(ElasticsearchSearchService.class);
        ElasticsearchStartupIndexer startupIndexer = mock(ElasticsearchStartupIndexer.class);
        when(searchService.requiresSearchIndex(any())).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(false);

        ElasticsearchSearchDecorator decorator = new ElasticsearchSearchDecorator();
        decorator.searchService = searchService;
        decorator.startupIndexer = startupIndexer;

        Set<SearchFilter> filters = Set.of();
        Assertions.assertThrows(ContentSearchNotSupportedException.class, () -> {
            decorator.searchVersions(filters, null, null, 0, 20, false);
        });
    }
}
