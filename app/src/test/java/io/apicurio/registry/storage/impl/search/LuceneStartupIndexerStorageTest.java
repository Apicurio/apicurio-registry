package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link LuceneStartupIndexer} that exercises the full reindex code path. Uses a
 * {@link TestInMemoryRegistryStorage} pre-loaded with 5 versions across 2 groups and 3 artifacts,
 * and verifies that calling {@code onStorageReady()} populates the Lucene index with all versions
 * and that the indexed documents are searchable.
 */
public class LuceneStartupIndexerStorageTest {

    @TempDir
    Path tempDir;

    private LuceneIndexWriter indexWriter;
    private LuceneIndexSearcher indexSearcher;
    private LuceneDocumentBuilder documentBuilder;
    private LuceneStartupIndexer startupIndexer;
    private TestInMemoryRegistryStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        String testIndexPath = tempDir.resolve("test-index").toString();

        LuceneSearchConfig config = new LuceneSearchConfig() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String getIndexPath() {
                return testIndexPath;
            }

            @Override
            public int getRamBufferSizeMB() {
                return 16;
            }
        };

        // Initialize index writer
        indexWriter = new LuceneIndexWriter();
        injectField(indexWriter, LuceneIndexWriter.class, "config", config);
        indexWriter.initialize();

        // Initialize index searcher
        indexSearcher = new LuceneIndexSearcher();
        injectField(indexSearcher, LuceneIndexSearcher.class, "config", config);
        injectField(indexSearcher, LuceneIndexSearcher.class, "indexWriter", indexWriter);
        indexSearcher.initialize();

        // Initialize document builder
        documentBuilder = new LuceneDocumentBuilder();

        // Initialize storage with pre-configured data
        storage = new TestInMemoryRegistryStorage();

        // Initialize startup indexer with all dependencies
        startupIndexer = new LuceneStartupIndexer();
        injectField(startupIndexer, LuceneStartupIndexer.class, "config", config);
        injectField(startupIndexer, LuceneStartupIndexer.class, "storage", storage);
        injectField(startupIndexer, LuceneStartupIndexer.class, "documentBuilder", documentBuilder);
        injectField(startupIndexer, LuceneStartupIndexer.class, "indexWriter", indexWriter);
        injectField(startupIndexer, LuceneStartupIndexer.class, "indexSearcher", indexSearcher);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (indexSearcher != null) {
            indexSearcher.cleanup();
        }
        if (indexWriter != null) {
            indexWriter.cleanup();
        }
    }

    /**
     * Verifies that before triggering the storage ready event, the startup indexer reports
     * not ready and the index is empty.
     */
    @Test
    void testInitialStateIsNotReady() {
        assertFalse(startupIndexer.isReady(), "Should not be ready before onStorageReady");
        assertEquals(0, startupIndexer.getCompletedTimestamp(), "Timestamp should be 0 before reindex");
        assertEquals(0, indexWriter.getDocumentCount(), "Index should be empty before reindex");
    }

    /**
     * Verifies that firing a non-READY event is ignored and the indexer remains not ready.
     */
    @Test
    void testNonReadyEventIsIgnored() {
        StorageEvent nonReadyEvent = StorageEvent.builder()
                .type(StorageEventType.ARTIFACT_CREATED)
                .build();

        startupIndexer.onStorageReady(nonReadyEvent);

        assertFalse(startupIndexer.isReady(), "Should not be ready after non-READY event");
        assertEquals(0, indexWriter.getDocumentCount(), "Index should remain empty");
    }

    /**
     * Core test: verifies that the startup indexer performs a full reindex from the populated
     * storage when the index is empty. After firing the READY event, the index should contain
     * exactly as many documents as there are versions in storage.
     */
    @Test
    void testReindexPopulatesIndexFromStorage() {
        StorageEvent readyEvent = StorageEvent.builder()
                .type(StorageEventType.READY)
                .build();

        startupIndexer.onStorageReady(readyEvent);

        assertTrue(startupIndexer.isReady(), "Should be ready after reindex");
        assertTrue(startupIndexer.getCompletedTimestamp() > 0, "Completed timestamp should be set");
        assertEquals(storage.getVersionCount(), indexWriter.getDocumentCount(),
                "Index should contain all versions from storage");
    }

    /**
     * Verifies that the indexed documents contain correct globalIds matching the pre-configured
     * storage data.
     */
    @Test
    void testIndexedDocumentsHaveCorrectGlobalIds() throws IOException {
        StorageEvent readyEvent = StorageEvent.builder()
                .type(StorageEventType.READY)
                .build();

        startupIndexer.onStorageReady(readyEvent);

        Set<Long> expectedGlobalIds = Set.of(1001L, 1002L, 1003L, 1004L, 1005L);
        Set<Long> indexedGlobalIds = getIndexedGlobalIds();

        assertEquals(expectedGlobalIds, indexedGlobalIds,
                "Indexed globalIds should match the pre-configured storage data");
    }

    /**
     * Verifies that the indexed documents contain the expected metadata fields (groupId,
     * artifactId, version, artifactType, name, description).
     */
    @Test
    void testIndexedDocumentsContainMetadata() throws IOException {
        StorageEvent readyEvent = StorageEvent.builder()
                .type(StorageEventType.READY)
                .build();

        startupIndexer.onStorageReady(readyEvent);

        // Find the document for globalId 1001 (test-group-1/pet-api v1.0.0)
        TopDocs topDocs = indexSearcher.search(new MatchAllDocsQuery(), 100);
        Document petApiDoc = null;
        for (var scoreDoc : topDocs.scoreDocs) {
            Document doc = indexSearcher.doc(scoreDoc.doc);
            if ("1001".equals(doc.get("globalId"))) {
                petApiDoc = doc;
                break;
            }
        }

        assertNotNull(petApiDoc, "Should find document for globalId 1001");
        assertEquals("test-group-1", petApiDoc.get("groupId"));
        assertEquals("pet-api", petApiDoc.get("artifactId"));
        assertEquals("1.0.0", petApiDoc.get("version"));
        assertEquals("OPENAPI", petApiDoc.get("artifactType"));
        assertEquals("Pet Store API", petApiDoc.get("name"));
        assertEquals("An API for managing pets", petApiDoc.get("description"));
    }

    /**
     * Verifies that when the index already contains documents, the startup indexer skips
     * the reindex and marks itself ready immediately.
     */
    @Test
    void testSkipsReindexWhenIndexIsNotEmpty() throws IOException {
        // Pre-populate the index with a single document
        var metadata = io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto.builder()
                .globalId(9999L).contentId(999L).groupId("g").artifactId("a").version("1.0")
                .versionOrder(1).artifactType("OPENAPI")
                .state(io.apicurio.registry.types.VersionState.ENABLED)
                .name("test").owner("test").createdOn(System.currentTimeMillis())
                .modifiedOn(System.currentTimeMillis())
                .build();
        Document doc = documentBuilder.buildVersionDocument(metadata, "{}".getBytes());
        indexWriter.updateDocument(new org.apache.lucene.index.Term("globalId", "9999"), doc);
        indexWriter.commit();

        assertEquals(1, indexWriter.getDocumentCount(), "Index should have 1 document before reindex");

        StorageEvent readyEvent = StorageEvent.builder()
                .type(StorageEventType.READY)
                .build();

        startupIndexer.onStorageReady(readyEvent);

        assertTrue(startupIndexer.isReady(), "Should be ready");
        assertTrue(startupIndexer.getCompletedTimestamp() > 0, "Timestamp should be set");
        assertEquals(1, indexWriter.getDocumentCount(),
                "Index should still have only 1 document (reindex was skipped)");
    }

    /**
     * Verifies that all artifact types from the test data are represented in the index.
     */
    @Test
    void testAllArtifactTypesAreIndexed() throws IOException {
        StorageEvent readyEvent = StorageEvent.builder()
                .type(StorageEventType.READY)
                .build();

        startupIndexer.onStorageReady(readyEvent);

        Set<String> indexedTypes = new HashSet<>();
        TopDocs topDocs = indexSearcher.search(new MatchAllDocsQuery(), 100);
        for (var scoreDoc : topDocs.scoreDocs) {
            Document doc = indexSearcher.doc(scoreDoc.doc);
            indexedTypes.add(doc.get("artifactType"));
        }

        assertTrue(indexedTypes.contains("OPENAPI"), "Should contain OPENAPI type");
        assertTrue(indexedTypes.contains("AVRO"), "Should contain AVRO type");
        assertTrue(indexedTypes.contains("JSON"), "Should contain JSON type");
        assertEquals(3, indexedTypes.size(), "Should contain exactly 3 distinct artifact types");
    }

    // ===== Helper methods =====

    private Set<Long> getIndexedGlobalIds() throws IOException {
        Set<Long> globalIds = new HashSet<>();
        TopDocs topDocs = indexSearcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);
        Set<String> fieldsToLoad = Set.of("globalId");
        for (var scoreDoc : topDocs.scoreDocs) {
            Document doc = indexSearcher.doc(scoreDoc.doc, fieldsToLoad);
            String globalIdStr = doc.get("globalId");
            if (globalIdStr != null) {
                globalIds.add(Long.parseLong(globalIdStr));
            }
        }
        return globalIds;
    }

    private void injectField(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }
}
