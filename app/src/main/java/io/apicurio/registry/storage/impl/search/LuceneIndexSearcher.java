package io.apicurio.registry.storage.impl.search;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Manages the Lucene IndexSearcher for searching the content index.
 */
@ApplicationScoped
public class LuceneIndexSearcher {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexSearcher.class);

    @Inject
    LuceneSearchConfig config;

    @Inject
    LuceneIndexWriter indexWriter;

    private Directory indexDirectory;
    private IndexReader indexReader;
    private IndexSearcher indexSearcher;
    private long lastRefreshTimestamp;

    @PostConstruct
    void initialize() {
        if (!config.isEnabled()) {
            return;
        }

        try {
            Path indexPath = Paths.get(config.getIndexPath());

            // Ensure index directory exists (it should have been created by IndexWriter)
            if (!Files.exists(indexPath)) {
                Files.createDirectories(indexPath);
                log.warn("Index directory did not exist, created: {}", indexPath);
            }

            // Open directory
            indexDirectory = FSDirectory.open(indexPath);

            // Try to open the index reader
            // If the index doesn't exist yet, DirectoryReader.open will fail
            // In that case, we'll initialize on first search
            try {
                indexReader = DirectoryReader.open(indexDirectory);
                indexSearcher = new IndexSearcher(indexReader);
                lastRefreshTimestamp = System.currentTimeMillis();

                log.info("Lucene IndexSearcher initialized successfully");
                log.info("  - Index directory: {}", indexPath);
                log.info("  - Total documents: {}", indexReader.numDocs());
            } catch (IOException e) {
                log.info("Index does not exist yet, will be created when first document is indexed");
                // This is expected when starting with an empty index
                indexReader = null;
                indexSearcher = null;
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Lucene IndexSearcher", e);
        }
    }

    @PreDestroy
    void cleanup() {
        if (indexReader != null) {
            try {
                log.info("Closing Lucene IndexReader...");
                indexReader.close();
                log.info("Lucene IndexReader closed successfully");
            } catch (IOException e) {
                log.error("Error closing Lucene IndexReader", e);
            }
        }

        if (indexDirectory != null) {
            try {
                indexDirectory.close();
            } catch (IOException e) {
                log.error("Error closing Lucene directory", e);
            }
        }
    }

    /**
     * Refreshes the IndexReader to see the latest changes. Should be called after commits to the
     * IndexWriter.
     *
     * @throws IOException if an error occurs during refresh
     */
    public void refresh() throws IOException {
        if (!config.isEnabled()) {
            return;
        }

        // If reader hasn't been initialized yet, try to initialize it now
        if (indexReader == null) {
            try {
                indexReader = DirectoryReader.open(indexDirectory);
                indexSearcher = new IndexSearcher(indexReader);
                lastRefreshTimestamp = System.currentTimeMillis();
                log.info("IndexSearcher initialized after first index write");
                return;
            } catch (IOException e) {
                // Index still doesn't exist, nothing to refresh
                return;
            }
        }

        // Refresh existing reader
        DirectoryReader newReader = DirectoryReader.openIfChanged((DirectoryReader) indexReader);
        if (newReader != null) {
            // Reader was refreshed
            indexReader.close();
            indexReader = newReader;
            indexSearcher = new IndexSearcher(indexReader);
            lastRefreshTimestamp = System.currentTimeMillis();
            log.debug("IndexSearcher refreshed, now has {} documents", indexReader.numDocs());
        }
    }

    /**
     * Searches the index with the given query.
     *
     * @param query The Lucene query to execute
     * @param maxResults Maximum number of results to return
     * @return TopDocs containing the search results
     * @throws IOException if an error occurs during search
     */
    public TopDocs search(Query query, int maxResults) throws IOException {
        ensureInitialized();

        return indexSearcher.search(query, maxResults);
    }

    /**
     * Searches the index with the given query and sort order.
     *
     * @param query The Lucene query to execute
     * @param maxResults Maximum number of results to return
     * @param sort The sort criteria
     * @return TopDocs containing the search results
     * @throws IOException if an error occurs during search
     */
    public TopDocs search(Query query, int maxResults, Sort sort) throws IOException {
        ensureInitialized();

        return indexSearcher.search(query, maxResults, sort);
    }

    /**
     * Returns the total number of documents matching the given query. More efficient than
     * fetching results when only the count is needed.
     *
     * @param query The Lucene query to count matches for
     * @return The number of matching documents
     * @throws IOException if an error occurs during counting
     */
    public int count(Query query) throws IOException {
        ensureInitialized();

        return indexSearcher.count(query);
    }

    /**
     * Retrieves a document by its internal Lucene doc ID.
     *
     * @param docId The internal document ID
     * @return The document
     * @throws IOException if an error occurs during retrieval
     */
    public Document doc(int docId) throws IOException {
        ensureInitialized();

        return indexSearcher.doc(docId);
    }

    /**
     * Retrieves a document by its internal Lucene doc ID, with only specified fields loaded.
     *
     * @param docId The internal document ID
     * @param fieldsToLoad Set of field names to load
     * @return The document with only specified fields
     * @throws IOException if an error occurs during retrieval
     */
    public Document doc(int docId, Set<String> fieldsToLoad) throws IOException {
        ensureInitialized();

        return indexSearcher.doc(docId, fieldsToLoad);
    }

    /**
     * Gets the underlying IndexReader.
     *
     * @return The IndexReader instance
     */
    public IndexReader getIndexReader() {
        return indexReader;
    }

    /**
     * Gets the Analyzer from the IndexWriter.
     *
     * @return The Analyzer instance
     */
    public Analyzer getAnalyzer() {
        return indexWriter.getAnalyzer();
    }

    /**
     * Gets the timestamp (milliseconds) of when the index was last refreshed.
     *
     * @return Timestamp as a string
     */
    public String getIndexedAsOf() {
        return String.valueOf(lastRefreshTimestamp);
    }

    /**
     * Checks if the IndexSearcher is initialized and ready for searches.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return config.isEnabled() && indexSearcher != null;
    }

    /**
     * Ensures the searcher is initialized before executing a search.
     *
     * @throws IOException if the searcher cannot be initialized
     */
    private void ensureInitialized() throws IOException {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Lucene search is not enabled");
        }

        if (indexSearcher == null) {
            // Try to refresh in case index was created after initialization
            refresh();

            if (indexSearcher == null) {
                throw new IllegalStateException("Index is not yet available. No documents have been indexed.");
            }
        }
    }
}
