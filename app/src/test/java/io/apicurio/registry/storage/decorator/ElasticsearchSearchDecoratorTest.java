package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.SearchFilterType;
import io.apicurio.registry.storage.dto.AuthorizationFilter;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ContentSearchNotSupportedException;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchConfig;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchService;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void searchArtifactsThrowsContentSearchNotSupportedWhenIndexerNotReady() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("test"));
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(false);

        assertThrows(ContentSearchNotSupportedException.class,
                () -> decorator.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false));

        verifyNoInteractions(delegate);
    }

    @Test
    void searchArtifactsDelegatesToSearchServiceWhenIndexerReady() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("test"));
        ArtifactSearchResultsDto results = ArtifactSearchResultsDto.builder().count(1).build();
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(searchService.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false))
                .thenReturn(results);

        ArtifactSearchResultsDto actual = decorator.searchArtifacts(filters, OrderBy.name, OrderDirection.asc,
                0, 10, false);

        assertEquals(results, actual);
        verifyNoInteractions(delegate);
    }

    @Test
    void searchArtifactsFallsThroughToDelegateWhenIndexNotRequired() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofName("test"));
        ArtifactSearchResultsDto results = ArtifactSearchResultsDto.builder().count(1).build();
        when(searchService.requiresSearchIndex(filters)).thenReturn(false);
        when(delegate.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false))
                .thenReturn(results);

        ArtifactSearchResultsDto actual = decorator.searchArtifacts(filters, OrderBy.name, OrderDirection.asc,
                0, 10, false);

        assertEquals(results, actual);
        verifyNoInteractions(startupIndexer);
    }

    // ==================== Per-resource authorization owner resolution ====================

    @SuppressWarnings("unchecked")
    private Set<SearchFilter> captureIndexFilters(Set<SearchFilter> filters) throws IOException {
        ArgumentCaptor<Set<SearchFilter>> captor = ArgumentCaptor.forClass(Set.class);
        verify(searchService).searchArtifacts(captor.capture(), eq(OrderBy.name), eq(OrderDirection.asc),
                eq(0), eq(10), eq(false));
        return captor.getValue();
    }

    private static AuthorizationFilter authorizationOf(Set<SearchFilter> filters) {
        return filters.stream().filter(f -> f.getType() == SearchFilterType.authorization)
                .findFirst().orElseThrow().getAuthorizationValue();
    }

    private static ArtifactSearchResultsDto artifacts(int count) {
        List<SearchedArtifactDto> list = IntStream.range(0, count)
                .mapToObj(i -> SearchedArtifactDto.builder().groupId("team-a/sub").artifactId("a" + i).build())
                .toList();
        return ArtifactSearchResultsDto.builder().artifacts(list).count(count).build();
    }

    @Test
    void indexSearchIncludesOwnedArtifactsResolvedFromPrimaryStorage() throws Exception {
        SearchFilter content = SearchFilter.ofContent("test");
        AuthorizationFilter grants = new AuthorizationFilter(false, Set.of(), Set.of("team-b/"), Set.of(), Set.of(),
                "carol");
        Set<SearchFilter> filters = Set.of(content, SearchFilter.ofAuthorization(grants));
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(delegate.searchArtifacts(any(), any(), any(), anyInt(), anyInt(), anyBoolean())).thenReturn(artifacts(2));

        decorator.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false);

        // The owner lookup asks the primary storage for exactly the caller's artifacts
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<SearchFilter>> lookup = ArgumentCaptor.forClass(Set.class);
        verify(delegate).searchArtifacts(lookup.capture(), eq(OrderBy.name), eq(OrderDirection.asc), eq(0),
                eq(ElasticsearchSearchDecorator.MAX_OWNED_ARTIFACTS), eq(true));
        assertEquals(1, lookup.getValue().size());
        assertEquals(AuthorizationFilter.ownedBy("carol"), authorizationOf(lookup.getValue()));
        Set<SearchFilter> indexFilters = captureIndexFilters(filters);
        assertTrue(indexFilters.contains(content));
        AuthorizationFilter resolved = authorizationOf(indexFilters);
        assertEquals(Set.of("team-a%2Fsub/a0", "team-a%2Fsub/a1"), resolved.ownedArtifacts());
        assertEquals(Set.of("team-b/"), resolved.allowPrefix());
        assertEquals("carol", resolved.owner());
    }

    @Test
    void indexSearchWithoutOwnerDoesNotQueryPrimaryStorage() throws Exception {
        AuthorizationFilter grants = new AuthorizationFilter(false, Set.of(), Set.of("team-b/"), Set.of(), Set.of(),
                null);
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("test"), SearchFilter.ofAuthorization(grants));
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);

        decorator.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false);

        verifyNoInteractions(delegate);
        assertEquals(Set.of(), authorizationOf(captureIndexFilters(filters)).ownedArtifacts());
    }

    @Test
    void ownedArtifactsAreCappedAtTheLimit() throws Exception {
        AuthorizationFilter grants = new AuthorizationFilter(false, Set.of(), Set.of(), Set.of(), Set.of(), "carol");
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("test"), SearchFilter.ofAuthorization(grants));
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        int limit = ElasticsearchSearchDecorator.MAX_OWNED_ARTIFACTS;
        when(delegate.searchArtifacts(any(), any(), any(), anyInt(), eq(limit), anyBoolean()))
                .thenReturn(artifacts(limit));

        decorator.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, 10, false);

        assertEquals(limit, authorizationOf(captureIndexFilters(filters)).ownedArtifacts().size());
    }
}
