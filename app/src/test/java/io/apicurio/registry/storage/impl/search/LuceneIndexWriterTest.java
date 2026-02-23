package io.apicurio.registry.storage.impl.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LuceneIndexWriter.
 */
public class LuceneIndexWriterTest {

    @TempDir
    Path tempDir;

    private LuceneIndexWriter indexWriter;
    private String testIndexPath;

    @BeforeEach
    void setUp() throws IOException {
        testIndexPath = tempDir.resolve("test-index").toString();
        indexWriter = new LuceneIndexWriter();

        // Use reflection to set the config field since it's injected
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
                return 16; // Small buffer for testing
            }
        };

        try {
            var configField = LuceneIndexWriter.class.getDeclaredField("config");
            configField.setAccessible(true);
            configField.set(indexWriter, config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject config", e);
        }

        indexWriter.initialize();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (indexWriter != null) {
            indexWriter.cleanup();
        }
    }

    @Test
    void testInitialize_CreatesIndex() throws IOException {
        // Then - index directory should exist
        assertTrue(tempDir.resolve("test-index").toFile().exists());
    }

    @Test
    void testUpdateDocument_AddsNewDocument() throws IOException {
        // Given
        Document doc = createTestDocument("doc1", "Test Content");
        Term idTerm = new Term("id", "doc1");

        // When
        indexWriter.updateDocument(idTerm, doc);
        indexWriter.commit();

        // Then
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(1, reader.numDocs());

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(new TermQuery(idTerm), 10);
            assertEquals(1, results.totalHits.value);
        }
    }

    @Test
    void testUpdateDocument_UpdatesExistingDocument() throws IOException {
        // Given - add initial document
        Document doc1 = createTestDocument("doc1", "Original Content");
        Term idTerm = new Term("id", "doc1");
        indexWriter.updateDocument(idTerm, doc1);
        indexWriter.commit();

        // When - update with new content
        Document doc2 = createTestDocument("doc1", "Updated Content");
        indexWriter.updateDocument(idTerm, doc2);
        indexWriter.commit();

        // Then - should still have only 1 document
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(1, reader.numDocs(), "Should have exactly 1 document after update");

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(new TermQuery(new Term("content", "updated")), 10);
            assertEquals(1, results.totalHits.value, "Should find the updated content");

            TopDocs oldResults = searcher.search(new TermQuery(new Term("content", "original")), 10);
            assertEquals(0, oldResults.totalHits.value, "Should not find the old content");
        }
    }

    @Test
    void testUpdateDocument_MultipleDocuments() throws IOException {
        // Given
        Document doc1 = createTestDocument("doc1", "Content 1");
        Document doc2 = createTestDocument("doc2", "Content 2");
        Document doc3 = createTestDocument("doc3", "Content 3");

        // When
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.updateDocument(new Term("id", "doc2"), doc2);
        indexWriter.updateDocument(new Term("id", "doc3"), doc3);
        indexWriter.commit();

        // Then
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(3, reader.numDocs());
        }
    }

    @Test
    void testDeleteDocuments_RemovesDocument() throws IOException {
        // Given - add documents
        Document doc1 = createTestDocument("doc1", "Content 1");
        Document doc2 = createTestDocument("doc2", "Content 2");
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.updateDocument(new Term("id", "doc2"), doc2);
        indexWriter.commit();

        // When - delete one document
        indexWriter.deleteDocuments(new Term("id", "doc1"));
        indexWriter.commit();

        // Then
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(1, reader.numDocs(), "Should have 1 document after deletion");

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs results = searcher.search(new TermQuery(new Term("id", "doc2")), 10);
            assertEquals(1, results.totalHits.value, "Should still find doc2");

            TopDocs deletedResults = searcher.search(new TermQuery(new Term("id", "doc1")), 10);
            assertEquals(0, deletedResults.totalHits.value, "Should not find deleted doc1");
        }
    }

    @Test
    void testDeleteDocuments_MultipleTerms() throws IOException {
        // Given
        Document doc1 = createTestDocument("doc1", "Content 1");
        Document doc2 = createTestDocument("doc2", "Content 2");
        Document doc3 = createTestDocument("doc3", "Content 3");
        indexWriter.updateDocument(new Term("id", "doc1"), doc1);
        indexWriter.updateDocument(new Term("id", "doc2"), doc2);
        indexWriter.updateDocument(new Term("id", "doc3"), doc3);
        indexWriter.commit();

        // When - delete multiple documents
        indexWriter.deleteDocuments(new Term("id", "doc1"));
        indexWriter.deleteDocuments(new Term("id", "doc3"));
        indexWriter.commit();

        // Then
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(1, reader.numDocs(), "Should have 1 document after deletion");
        }
    }

    @Test
    void testCommit_PersistsChanges() throws IOException {
        // Given
        Document doc = createTestDocument("doc1", "Test Content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc);

        // When - commit changes
        indexWriter.commit();

        // Then - changes should be visible in a new reader
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(1, reader.numDocs());
        }
    }

    @Test
    void testOptimize_ReducesSegments() throws IOException {
        // Given - add multiple documents to create multiple segments
        for (int i = 0; i < 20; i++) {
            Document doc = createTestDocument("doc" + i, "Content " + i);
            indexWriter.updateDocument(new Term("id", "doc" + i), doc);
            if (i % 5 == 0) {
                indexWriter.commit(); // Create multiple segments
            }
        }
        indexWriter.commit();

        // When
        indexWriter.optimize();

        // Then - should have all documents
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(20, reader.numDocs(), "Should still have all documents after optimize");
        }
    }

    @Test
    void testShutdown_ClosesWriter() throws IOException {
        // Given
        Document doc = createTestDocument("doc1", "Test Content");
        indexWriter.updateDocument(new Term("id", "doc1"), doc);
        indexWriter.commit();

        // When
        indexWriter.cleanup();

        // Then - should be able to open the index
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(1, reader.numDocs());
        }
    }

    @Test
    void testMultipleCommits() throws IOException {
        // Given & When - multiple commit cycles
        for (int i = 0; i < 5; i++) {
            Document doc = createTestDocument("doc" + i, "Content " + i);
            indexWriter.updateDocument(new Term("id", "doc" + i), doc);
            indexWriter.commit();
        }

        // Then
        try (Directory directory = FSDirectory.open(Path.of(testIndexPath));
             IndexReader reader = DirectoryReader.open(directory)) {

            assertEquals(5, reader.numDocs());
        }
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
