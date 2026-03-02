package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.VersionState;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProvider;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for SynchronousLuceneIndexUpdater.
 */
@ExtendWith(MockitoExtension.class)
public class SynchronousLuceneIndexUpdaterTest {

    @Mock
    private LuceneSearchConfig config;

    @Mock
    private RegistryStorage storage;

    @Mock
    private LuceneDocumentBuilder documentBuilder;

    @Mock
    private LuceneIndexWriter indexWriter;

    @Mock
    private LuceneIndexSearcher indexSearcher;

    @Mock
    private ArtifactTypeUtilProviderFactory typeProviderFactory;

    @InjectMocks
    private SynchronousLuceneIndexUpdater updater;

    @BeforeEach
    void setUp() {
        // By default, set updater as inactive unless specified otherwise.
        // Each test that needs the updater active will stub isEnabled/getUpdateMode.
        when(config.isEnabled()).thenReturn(false);
    }

    @Test
    void testIsActive_WhenDisabled() {
        // Given
        when(config.isEnabled()).thenReturn(false);
        updater.initialize();

        // Then
        assertFalse(updater.isActive());
    }

    @Test
    void testIsActive_WhenEnabledAndSynchronous() {
        // Given
        when(config.isEnabled()).thenReturn(true);
        when(config.getUpdateMode()).thenReturn(IndexUpdateMode.SYNCHRONOUS);
        updater.initialize();

        // Then
        assertTrue(updater.isActive());
    }

    @Test
    void testIsActive_WhenEnabledButAsynchronous() {
        // Given
        when(config.isEnabled()).thenReturn(true);
        when(config.getUpdateMode()).thenReturn(IndexUpdateMode.ASYNCHRONOUS);
        updater.initialize();

        // Then
        assertFalse(updater.isActive());
    }

    @Test
    void testOnVersionCreated_WhenInactive() throws Exception {
        // Given
        when(config.isEnabled()).thenReturn(false);
        updater.initialize();

        VersionCreatedEvent event = new VersionCreatedEvent("group1", "artifact1", "1.0.0",
                123L, 456L);

        // When
        updater.onVersionCreated(event);

        // Then
        verify(storage, never()).getArtifactVersionMetaData(anyString(), anyString(),
                anyString());
        verify(indexWriter, never()).updateDocument(any(Term.class), any(Document.class));
    }

    @Test
    void testOnVersionCreated_WhenActive() throws Exception {
        // Given
        when(config.isEnabled()).thenReturn(true);
        when(config.getUpdateMode()).thenReturn(IndexUpdateMode.SYNCHRONOUS);
        updater.initialize();

        VersionCreatedEvent event = new VersionCreatedEvent("group1", "artifact1", "1.0.0",
                123L, 456L);

        // Mock metadata and content
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        when(storage.getArtifactVersionMetaData("group1", "artifact1", "1.0.0"))
                .thenReturn(metadata);

        StoredArtifactVersionDto storedVersion = mock(StoredArtifactVersionDto.class);
        ContentHandle content = ContentHandle.create("test content".getBytes());
        when(storedVersion.getContent()).thenReturn(content);
        when(storage.getArtifactVersionContent("group1", "artifact1", "1.0.0"))
                .thenReturn(storedVersion);

        // Mock type provider factory chain
        ArtifactTypeUtilProvider typeProvider = mock(ArtifactTypeUtilProvider.class);
        StructuredContentExtractor extractor = mock(StructuredContentExtractor.class);
        when(typeProviderFactory.getArtifactTypeProvider("OPENAPI")).thenReturn(typeProvider);
        when(typeProvider.getStructuredContentExtractor()).thenReturn(extractor);

        Document doc = new Document();
        when(documentBuilder.buildVersionDocument(any(), any(byte[].class), any()))
                .thenReturn(doc);

        // When
        updater.onVersionCreated(event);

        // Then
        verify(storage).getArtifactVersionMetaData("group1", "artifact1", "1.0.0");
        verify(storage).getArtifactVersionContent("group1", "artifact1", "1.0.0");
        verify(documentBuilder).buildVersionDocument(eq(metadata), any(byte[].class),
                eq(extractor));

        ArgumentCaptor<Term> termCaptor = ArgumentCaptor.forClass(Term.class);
        verify(indexWriter).updateDocument(termCaptor.capture(), eq(doc));

        Term capturedTerm = termCaptor.getValue();
        assertEquals("globalId", capturedTerm.field());
        assertEquals("123", capturedTerm.text());

        verify(indexWriter).commit();
        verify(indexSearcher).refresh();
    }

    @Test
    void testOnVersionDeleted_WhenActive() throws Exception {
        // Given
        when(config.isEnabled()).thenReturn(true);
        when(config.getUpdateMode()).thenReturn(IndexUpdateMode.SYNCHRONOUS);
        updater.initialize();

        VersionDeletedEvent event = new VersionDeletedEvent("group1", "artifact1", "1.0.0",
                123L);

        // When
        updater.onVersionDeleted(event);

        // Then
        ArgumentCaptor<Term> termCaptor = ArgumentCaptor.forClass(Term.class);
        verify(indexWriter).deleteDocuments(termCaptor.capture());

        Term capturedTerm = termCaptor.getValue();
        assertEquals("globalId", capturedTerm.field());
        assertEquals("123", capturedTerm.text());

        verify(indexWriter).commit();
        verify(indexSearcher).refresh();
    }

    @Test
    void testOnVersionStateChanged_WhenActive() throws Exception {
        // Given
        when(config.isEnabled()).thenReturn(true);
        when(config.getUpdateMode()).thenReturn(IndexUpdateMode.SYNCHRONOUS);
        updater.initialize();

        VersionStateChangedEvent event = new VersionStateChangedEvent("group1", "artifact1",
                "1.0.0", 123L, VersionState.ENABLED, VersionState.DISABLED);

        // Mock metadata and content
        ArtifactVersionMetaDataDto metadata = createTestMetadata();
        when(storage.getArtifactVersionMetaData("group1", "artifact1", "1.0.0"))
                .thenReturn(metadata);

        StoredArtifactVersionDto storedVersion = mock(StoredArtifactVersionDto.class);
        ContentHandle content = ContentHandle.create("test content".getBytes());
        when(storedVersion.getContent()).thenReturn(content);
        when(storage.getArtifactVersionContent("group1", "artifact1", "1.0.0"))
                .thenReturn(storedVersion);

        // Mock type provider factory chain
        ArtifactTypeUtilProvider typeProvider = mock(ArtifactTypeUtilProvider.class);
        StructuredContentExtractor extractor = mock(StructuredContentExtractor.class);
        when(typeProviderFactory.getArtifactTypeProvider("OPENAPI")).thenReturn(typeProvider);
        when(typeProvider.getStructuredContentExtractor()).thenReturn(extractor);

        Document doc = new Document();
        when(documentBuilder.buildVersionDocument(any(), any(byte[].class), any()))
                .thenReturn(doc);

        // When
        updater.onVersionStateChanged(event);

        // Then
        verify(storage).getArtifactVersionMetaData("group1", "artifact1", "1.0.0");
        verify(indexWriter).updateDocument(any(Term.class), eq(doc));
        verify(indexWriter).commit();
        verify(indexSearcher).refresh();
    }

    @Test
    void testOnArtifactMetadataUpdated_WhenActive() throws Exception {
        // Given
        when(config.isEnabled()).thenReturn(true);
        when(config.getUpdateMode()).thenReturn(IndexUpdateMode.SYNCHRONOUS);
        updater.initialize();

        ArtifactMetadataUpdatedEvent event = new ArtifactMetadataUpdatedEvent("group1",
                "artifact1");

        // Mock search results with 2 versions
        SearchedVersionDto version1 = createSearchedVersion("1.0.0", 123L);
        SearchedVersionDto version2 = createSearchedVersion("2.0.0", 124L);
        VersionSearchResultsDto searchResults = new VersionSearchResultsDto();
        searchResults.setVersions(List.of(version1, version2));
        searchResults.setCount(2);

        when(storage.searchVersions(any(Set.class), eq(OrderBy.createdOn),
                eq(OrderDirection.asc), eq(0), eq(Integer.MAX_VALUE))).thenReturn(searchResults);

        // Mock metadata and content for both versions
        ArtifactVersionMetaDataDto metadata1 = createTestMetadata();
        metadata1.setVersion("1.0.0");
        when(storage.getArtifactVersionMetaData("group1", "artifact1", "1.0.0"))
                .thenReturn(metadata1);

        ArtifactVersionMetaDataDto metadata2 = createTestMetadata();
        metadata2.setVersion("2.0.0");
        when(storage.getArtifactVersionMetaData("group1", "artifact1", "2.0.0"))
                .thenReturn(metadata2);

        StoredArtifactVersionDto storedVersion = mock(StoredArtifactVersionDto.class);
        ContentHandle content = ContentHandle.create("test content".getBytes());
        when(storedVersion.getContent()).thenReturn(content);
        when(storage.getArtifactVersionContent(anyString(), anyString(), anyString()))
                .thenReturn(storedVersion);

        // Mock type provider factory chain
        ArtifactTypeUtilProvider typeProvider = mock(ArtifactTypeUtilProvider.class);
        StructuredContentExtractor extractor = mock(StructuredContentExtractor.class);
        when(typeProviderFactory.getArtifactTypeProvider("OPENAPI")).thenReturn(typeProvider);
        when(typeProvider.getStructuredContentExtractor()).thenReturn(extractor);

        Document doc = new Document();
        when(documentBuilder.buildVersionDocument(any(), any(byte[].class), any()))
                .thenReturn(doc);

        // When
        updater.onArtifactMetadataUpdated(event);

        // Then
        verify(storage).searchVersions(any(Set.class), eq(OrderBy.createdOn),
                eq(OrderDirection.asc), eq(0), eq(Integer.MAX_VALUE));
        verify(storage).getArtifactVersionMetaData("group1", "artifact1", "1.0.0");
        verify(storage).getArtifactVersionMetaData("group1", "artifact1", "2.0.0");
        verify(indexWriter, times(2)).updateDocument(any(Term.class), eq(doc));
        verify(indexWriter, times(2)).commit();
        verify(indexSearcher, times(2)).refresh();
    }

    private ArtifactVersionMetaDataDto createTestMetadata() {
        ArtifactVersionMetaDataDto metadata = new ArtifactVersionMetaDataDto();
        metadata.setGlobalId(123L);
        metadata.setContentId(456L);
        metadata.setGroupId("group1");
        metadata.setArtifactId("artifact1");
        metadata.setVersion("1.0.0");
        metadata.setArtifactType("OPENAPI");
        metadata.setState(VersionState.ENABLED);
        return metadata;
    }

    private SearchedVersionDto createSearchedVersion(String version, long globalId) {
        SearchedVersionDto dto = new SearchedVersionDto();
        dto.setGroupId("group1");
        dto.setArtifactId("artifact1");
        dto.setVersion(version);
        dto.setGlobalId(globalId);
        dto.setState(VersionState.ENABLED);
        return dto;
    }
}
