package io.apicurio.registry.storage.decorator;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorResponse;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.SearchedArtifactDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.storage.error.ContentSearchNotSupportedException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchConfig;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchService;
import io.apicurio.registry.storage.impl.search.ElasticsearchStartupIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        Set<SearchFilter> filters = Set.of(SearchFilter.ofStructure("agent_card:skill:test-skill"));
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(false);

        assertThrows(ContentSearchNotSupportedException.class,
                () -> decorator.searchArtifacts(filters, OrderBy.createdOn, OrderDirection.desc, 0, 10, false));

        verifyNoInteractions(delegate);
    }

    @Test
    void searchArtifactsFallsThroughToDelegateWhenIndexNotRequired() {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofName("test"));
        ArtifactSearchResultsDto results = new ArtifactSearchResultsDto();
        when(searchService.requiresSearchIndex(filters)).thenReturn(false);
        when(delegate.searchArtifacts(filters, OrderBy.createdOn, OrderDirection.desc, 0, 10, false))
                .thenReturn(results);

        ArtifactSearchResultsDto actual = decorator.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 0, 10, false);

        assertEquals(results, actual);
        verifyNoInteractions(startupIndexer);
    }

    @Test
    void searchArtifactsReturnsEmptyWithoutSqlQueryWhenIndexHasNoMatches() throws IOException {
        SearchFilter structureFilter = SearchFilter.ofStructure("agent_card:skill:no-such-skill");
        Set<SearchFilter> filters = Set.of(structureFilter);
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(searchService.isIndexOnlyFilter(structureFilter)).thenReturn(true);
        when(searchService.searchArtifactIdentities(filters)).thenReturn(Set.of());

        ArtifactSearchResultsDto actual = decorator.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 0, 10, false);

        assertEquals(0, actual.getCount());
        assertEquals(0, actual.getArtifacts().size());
        verify(delegate, never()).searchArtifacts(anySet(), any(), any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void searchArtifactsIntersectsIndexMatchesWithSqlResults() throws IOException {
        SearchFilter structureFilter = SearchFilter.ofStructure("agent_card:skill:test-skill");
        SearchFilter typeFilter = SearchFilter.ofArtifactType("AGENT_CARD");
        Set<SearchFilter> filters = Set.of(structureFilter, typeFilter);
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(searchService.isIndexOnlyFilter(structureFilter)).thenReturn(true);
        when(searchService.isIndexOnlyFilter(typeFilter)).thenReturn(false);
        when(searchService.searchArtifactIdentities(filters)).thenReturn(Set.of(
                ElasticsearchSearchService.identityKey(null, "agent-1"),
                ElasticsearchSearchService.identityKey("group-1", "agent-3")));

        // SQL returns three artifacts; only two of them have a matching version in the index
        ArtifactSearchResultsDto sqlResults = new ArtifactSearchResultsDto();
        sqlResults.setArtifacts(new ArrayList<>(List.of(
                searchedArtifact(null, "agent-1"),
                searchedArtifact(null, "agent-2"),
                searchedArtifact("group-1", "agent-3"))));
        when(delegate.searchArtifacts(eq(Set.of(typeFilter)), eq(OrderBy.createdOn),
                eq(OrderDirection.desc), eq(0), eq(ElasticsearchSearchService.MAX_ARTIFACT_SEARCH_HITS),
                eq(true))).thenReturn(sqlResults);

        ArtifactSearchResultsDto actual = decorator.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 0, 10, false);

        assertEquals(2, actual.getCount());
        assertEquals(2, actual.getArtifacts().size());
        assertEquals("agent-1", actual.getArtifacts().get(0).getArtifactId());
        assertEquals("agent-3", actual.getArtifacts().get(1).getArtifactId());
        assertEquals("group-1", actual.getArtifacts().get(1).getGroupId());
    }

    @Test
    void searchArtifactsAppliesOffsetAndLimitAfterIntersection() throws IOException {
        SearchFilter structureFilter = SearchFilter.ofStructure("agent_card:skill:test-skill");
        Set<SearchFilter> filters = Set.of(structureFilter);
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(searchService.isIndexOnlyFilter(structureFilter)).thenReturn(true);
        when(searchService.searchArtifactIdentities(filters)).thenReturn(Set.of(
                ElasticsearchSearchService.identityKey(null, "agent-1"),
                ElasticsearchSearchService.identityKey(null, "agent-2"),
                ElasticsearchSearchService.identityKey(null, "agent-3")));

        ArtifactSearchResultsDto sqlResults = new ArtifactSearchResultsDto();
        sqlResults.setArtifacts(new ArrayList<>(List.of(
                searchedArtifact(null, "agent-1"),
                searchedArtifact(null, "agent-2"),
                searchedArtifact(null, "agent-3"))));
        when(delegate.searchArtifacts(eq(Set.of()), eq(OrderBy.createdOn), eq(OrderDirection.desc),
                eq(0), eq(ElasticsearchSearchService.MAX_ARTIFACT_SEARCH_HITS), eq(true)))
                .thenReturn(sqlResults);

        ArtifactSearchResultsDto actual = decorator.searchArtifacts(filters, OrderBy.createdOn,
                OrderDirection.desc, 1, 1, false);

        assertEquals(3, actual.getCount());
        assertEquals(1, actual.getArtifacts().size());
        assertEquals("agent-2", actual.getArtifacts().get(0).getArtifactId());
    }

    @Test
    void searchVersionsWrapsElasticsearchExceptionAsStorageException() throws IOException {
        Set<SearchFilter> filters = Set.of(SearchFilter.ofContent("test"));
        ElasticsearchException esException = esException();
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(searchService.searchVersions(filters, OrderBy.name, OrderDirection.asc, 0, 10, false))
                .thenThrow(esException);

        RegistryStorageException exception = assertThrows(RegistryStorageException.class,
                () -> decorator.searchVersions(filters, OrderBy.name, OrderDirection.asc, 0, 10, false));

        assertEquals("Elasticsearch search failed for index-only filters.", exception.getMessage());
        assertEquals(esException, exception.getCause());
        verifyNoInteractions(delegate);
    }

    @Test
    void searchArtifactsWrapsElasticsearchExceptionAsStorageException() throws IOException {
        SearchFilter structureFilter = SearchFilter.ofStructure("agent_card:skill:test-skill");
        Set<SearchFilter> filters = Set.of(structureFilter);
        ElasticsearchException esException = esException();
        when(searchService.requiresSearchIndex(filters)).thenReturn(true);
        when(startupIndexer.isReady()).thenReturn(true);
        when(searchService.isIndexOnlyFilter(structureFilter)).thenReturn(true);
        when(searchService.searchArtifactIdentities(filters)).thenThrow(esException);

        RegistryStorageException exception = assertThrows(RegistryStorageException.class,
                () -> decorator.searchArtifacts(filters, OrderBy.createdOn, OrderDirection.desc, 0, 10, false));

        assertEquals("Elasticsearch search failed for index-only filters.", exception.getMessage());
        assertEquals(esException, exception.getCause());
        verify(delegate, never()).searchArtifacts(anySet(), any(), any(), anyInt(), anyInt(), anyBoolean());
    }

    private static ElasticsearchException esException() {
        return new ElasticsearchException("search", ErrorResponse.of(r -> r
                .error(e -> e.type("transport_error").reason("connection refused"))
                .status(500)));
    }

    private static SearchedArtifactDto searchedArtifact(String groupId, String artifactId) {
        SearchedArtifactDto dto = new SearchedArtifactDto();
        dto.setGroupId(groupId);
        dto.setArtifactId(artifactId);
        return dto;
    }
}
