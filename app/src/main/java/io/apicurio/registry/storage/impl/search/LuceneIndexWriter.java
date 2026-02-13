package io.apicurio.registry.storage.impl.search;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the Lucene IndexWriter for adding, updating, and deleting documents from the search index.
 */
@ApplicationScoped
public class LuceneIndexWriter {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexWriter.class);

    @Inject
    LuceneSearchConfig config;

    private Directory indexDirectory;
    private IndexWriter indexWriter;
    private Analyzer analyzer;

    @PostConstruct
    void initialize() {
        if (!config.isEnabled()) {
            return;
        }

        try {
            // Create index directory if it doesn't exist
            Path indexPath = Paths.get(config.getIndexPath());
            if (!Files.exists(indexPath)) {
                Files.createDirectories(indexPath);
                log.info("Created Lucene index directory: {}", indexPath);
            }

            // Open directory
            indexDirectory = FSDirectory.open(indexPath);

            // Create analyzer (standard analyzer for general text)
            analyzer = new StandardAnalyzer();

            // Configure IndexWriter
            IndexWriterConfig iwConfig = new IndexWriterConfig(analyzer);
            iwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
            iwConfig.setRAMBufferSizeMB(config.getRamBufferSizeMB());

            // Create IndexWriter
            indexWriter = new IndexWriter(indexDirectory, iwConfig);

            log.info("Lucene IndexWriter initialized successfully");
            log.info("  - Index directory: {}", indexPath);
            log.info("  - RAM buffer size: {} MB", config.getRamBufferSizeMB());
            log.info("  - Total documents: {}", indexWriter.getDocStats().numDocs);

        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Lucene IndexWriter", e);
        }
    }

    @PreDestroy
    void cleanup() {
        if (indexWriter != null) {
            try {
                log.info("Closing Lucene IndexWriter...");
                indexWriter.close();
                log.info("Lucene IndexWriter closed successfully");
            } catch (IOException e) {
                log.error("Error closing Lucene IndexWriter", e);
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
     * Updates or inserts a document in the index. If a document with the same term exists, it will be
     * replaced.
     *
     * @param updateTerm The term to identify the document (typically globalId)
     * @param document The document to index
     * @throws IOException if an error occurs during indexing
     */
    public void updateDocument(Term updateTerm, Document document) throws IOException {
        if (!config.isEnabled() || indexWriter == null) {
            return;
        }

        indexWriter.updateDocument(updateTerm, document);
    }

    /**
     * Deletes documents matching the given term from the index.
     *
     * @param deleteTerm The term to match documents for deletion
     * @throws IOException if an error occurs during deletion
     */
    public void deleteDocuments(Term deleteTerm) throws IOException {
        if (!config.isEnabled() || indexWriter == null) {
            return;
        }

        indexWriter.deleteDocuments(deleteTerm);
    }

    /**
     * Deletes all documents from the index.
     *
     * @throws IOException if an error occurs during deletion
     */
    public void deleteAll() throws IOException {
        if (!config.isEnabled() || indexWriter == null) {
            return;
        }

        indexWriter.deleteDocuments(new MatchAllDocsQuery());
    }

    /**
     * Commits all pending changes to the index.
     *
     * @throws IOException if an error occurs during commit
     */
    public void commit() throws IOException {
        if (!config.isEnabled() || indexWriter == null) {
            return;
        }

        indexWriter.commit();
    }

    /**
     * Forces a merge of index segments to optimize search performance. This should be called
     * periodically but not too frequently as it can be expensive.
     *
     * @throws IOException if an error occurs during optimization
     */
    public void optimize() throws IOException {
        if (!config.isEnabled() || indexWriter == null) {
            return;
        }

        log.info("Starting index optimization...");
        long startTime = System.currentTimeMillis();

        indexWriter.forceMerge(1);
        indexWriter.commit();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Index optimization completed in {}ms", duration);
    }

    /**
     * Gets the analyzer used by this IndexWriter.
     *
     * @return The analyzer instance
     */
    public Analyzer getAnalyzer() {
        return analyzer;
    }

    /**
     * Gets the number of documents in the index.
     *
     * @return The document count
     */
    public int getDocumentCount() {
        if (!config.isEnabled() || indexWriter == null) {
            return 0;
        }

        return indexWriter.getDocStats().numDocs;
    }

    /**
     * Checks if the IndexWriter is initialized and ready.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return config.isEnabled() && indexWriter != null;
    }
}
