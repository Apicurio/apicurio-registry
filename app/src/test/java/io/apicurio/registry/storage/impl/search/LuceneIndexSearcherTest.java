package io.apicurio.registry.storage.impl.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LuceneIndexSearcher.
 */
public class LuceneIndexSearcherTest {

    @TempDir
    Path tempDir;

    private LuceneIndexWriter indexWriter;
    private LuceneIndexSearcher indexSearcher;
    private String testIndexPath;

    @BeforeEach
    void setUp() throws IOException {
        testIndexPath = tempDir.resolve("test-index").toString();

        // Create and configure the index writer
        indexWriter = new LuceneIndexWriter();
        LuceneSearchConfig writerConfig = new LuceneSearchConfig() {
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

        try {
            var configField = LuceneIndexWriter.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(indexWriter, writerConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject writer config", e);
        }

        indexWriter.initialize();

        // Create and configure the index searcher
        indexSearcher = new LuceneIndexSearcher();
        LuceneSearchConfig searcherConfig = new LuceneSearchConfig() {
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

        try {
            var configField = LuceneIndexSearcher.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(indexSearcher, searcherConfig);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject searcher config", e);
        }

        indexSearcher.initialize();
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

    @Test
    void testInitialize_HandlesNonExistentIndex() {
        // When searcher is initialized before any documents are indexed
        // Then it should not throw an exception
        assertDoesNotThrow(() -> indexSearcher.initialize());
    }

    @Test
    void testSearch_FindsDocuments() throws IOException {
        // Given - index some documents
        Document doc1 = createTestDocument("doc1", "test content");
        Document doc2 = createTestDocument("doc2", "other content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.updateDocument(new Term("id", "doc2"), doc2);
        indexWriter.commit();
        indexSearcher.refresh();

        // When - search for a specific term
        Query query = new TermQuery(new Term("content", "test"));
        TopDocs results = indexSearcher.search(query, 10);

        // Then
        assertEquals(1, results.totalHits.value);
        assertEquals(1, results.scoreDocs.length);
    }

    @Test
    void testSearch_MatchAllDocuments() throws IOException {
        // Given
        Document doc1 = createTestDocument("doc1", "content 1");
        Document doc2 = createTestDocument("doc2", "content 2");
        Document doc3 = createTestDocument("doc3", "content 3");
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.updateDocument(new Term("id", "doc2"), doc2);
        indexWriter.updateDocument(new Term("id", "doc3"), doc3);
        indexWriter.commit();
        indexSearcher.refresh();

        // When
        Query query = new MatchAllDocsQuery();
        TopDocs results = indexSearcher.search(query, 10);

        // Then
        assertEquals(3, results.totalHits.value);
        assertEquals(3, results.scoreDocs.length);
    }

    @Test
    void testSearch_WithLimit() throws IOException {
        // Given - index 5 documents
        for (int i = 0; i < 5; i++) {
            Document doc = createTestDocument("doc" + i, "content");
            indexWriter.updateDocument(new Term("id", "doc" + i), doc);
        }
        indexWriter.commit();
        indexSearcher.refresh();

        // When - search with limit of 3
        Query query = new MatchAllDocsQuery();
        TopDocs results = indexSearcher.search(query, 3);

        // Then
        assertEquals(5, results.totalHits.value, "Should report all matching documents");
        assertEquals(3, results.scoreDocs.length, "Should only return top 3 results");
    }

    @Test
    void testSearch_NoResults() throws IOException {
        // Given - index documents
        Document doc = createTestDocument("doc1", "test content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc);
        indexWriter.commit();
        indexSearcher.refresh();

        // When - search for non-existent term
        Query query = new TermQuery(new Term("content", "nonexistent"));
        TopDocs results = indexSearcher.search(query, 10);

        // Then
        assertEquals(0, results.totalHits.value);
        assertEquals(0, results.scoreDocs.length);
    }

    @Test
    void testRefresh_MakesNewDocumentsVisible() throws IOException {
        // Given - index is not yet initialized (no documents indexed)
        assertFalse(indexSearcher.isInitialized(), "Searcher should not be initialized before any indexing");

        // When - add document, commit, and refresh
        Document doc = createTestDocument("doc1", "test content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc);
        indexWriter.commit();
        indexSearcher.refresh();

        // Then - searcher should now be initialized and find the document
        assertTrue(indexSearcher.isInitialized(), "Searcher should be initialized after refresh");
        Query query = new MatchAllDocsQuery();
        TopDocs results = indexSearcher.search(query, 10);
        assertEquals(1, results.totalHits.value, "Should find the new document after refresh");
    }

    @Test
    void testRefresh_ReflectsUpdates() throws IOException {
        // Given - initial document
        Document doc1 = createTestDocument("doc1", "original content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.commit();
        indexSearcher.refresh();

        Query originalQuery = new TermQuery(new Term("content", "original"));
        TopDocs originalResults = indexSearcher.search(originalQuery, 10);
        assertEquals(1, originalResults.totalHits.value, "Should find original content");

        // When - update document and refresh
        Document doc2 = createTestDocument("doc1", "updated content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc2);
        indexWriter.commit();
        indexSearcher.refresh();

        // Then - should find updated content
        Query updatedQuery = new TermQuery(new Term("content", "updated"));
        TopDocs updatedResults = indexSearcher.search(updatedQuery, 10);
        assertEquals(1, updatedResults.totalHits.value, "Should find updated content");

        // And original content should be gone
        TopDocs oldResults = indexSearcher.search(originalQuery, 10);
        assertEquals(0, oldResults.totalHits.value, "Should not find original content anymore");
    }

    @Test
    void testRefresh_ReflectsDeletions() throws IOException {
        // Given - two documents
        Document doc1 = createTestDocument("doc1", "content 1");
        Document doc2 = createTestDocument("doc2", "content 2");
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.updateDocument(new Term("id", "doc2"), doc2);
        indexWriter.commit();
        indexSearcher.refresh();

        Query allQuery = new MatchAllDocsQuery();
        TopDocs allResults = indexSearcher.search(allQuery, 10);
        assertEquals(2, allResults.totalHits.value, "Should have 2 documents initially");

        // When - delete one document and refresh
        indexWriter.deleteDocuments(new Term("id", "doc1"));
        indexWriter.commit();
        indexSearcher.refresh();

        // Then - should only find one document
        TopDocs newResults = indexSearcher.search(allQuery, 10);
        assertEquals(1, newResults.totalHits.value, "Should have 1 document after deletion");

        Query doc2Query = new TermQuery(new Term("id", "doc2"));
        TopDocs doc2Results = indexSearcher.search(doc2Query, 10);
        assertEquals(1, doc2Results.totalHits.value, "Should still find doc2");
    }

    @Test
    void testSearch_MultipleSearches() throws IOException {
        // Given
        Document doc1 = createTestDocument("doc1", "apple banana");
        Document doc2 = createTestDocument("doc2", "banana cherry");
        Document doc3 = createTestDocument("doc3", "cherry date");
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.updateDocument(new Term("id", "doc2"), doc2);
        indexWriter.updateDocument(new Term("id", "doc3"), doc3);
        indexWriter.commit();
        indexSearcher.refresh();

        // When - multiple different searches
        Query appleQuery = new TermQuery(new Term("content", "apple"));
        Query bananaQuery = new TermQuery(new Term("content", "banana"));
        Query cherryQuery = new TermQuery(new Term("content", "cherry"));

        TopDocs appleResults = indexSearcher.search(appleQuery, 10);
        TopDocs bananaResults = indexSearcher.search(bananaQuery, 10);
        TopDocs cherryResults = indexSearcher.search(cherryQuery, 10);

        // Then
        assertEquals(1, appleResults.totalHits.value);
        assertEquals(2, bananaResults.totalHits.value);
        assertEquals(2, cherryResults.totalHits.value);
    }

    @Test
    void testDoc_RetrievesStoredFields() throws IOException {
        // Given
        Document doc = createTestDocument("doc1", "test content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc);
        indexWriter.commit();
        indexSearcher.refresh();

        // When - search and retrieve document
        Query query = new TermQuery(new Term("id", "doc1"));
        TopDocs results = indexSearcher.search(query, 10);
        assertEquals(1, results.totalHits.value);

        Document retrievedDoc = indexSearcher.doc(results.scoreDocs[0].doc);

        // Then
        assertNotNull(retrievedDoc);
        assertEquals("doc1", retrievedDoc.get("id"));
        assertEquals("test content", retrievedDoc.get("content"));
        assertNotNull(retrievedDoc.get("timestamp"));
    }

    @Test
    void testShutdown_ClosesSearcher() throws IOException {
        // Given
        Document doc = createTestDocument("doc1", "test content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc);
        indexWriter.commit();
        indexSearcher.refresh();

        // When
        indexSearcher.cleanup();

        // Then - should not throw exception
        assertDoesNotThrow(() -> indexSearcher.cleanup());
    }

    /**
     * Creates a test document with ID and content fields.
     */
    private Document createTestDocument(String id, String content) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("content", content, Field.Store.YES));
        doc.add(new StoredField("timestamp", System.currentTimeMillis()));
        return doc;
    }
}
