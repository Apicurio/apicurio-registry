package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Polls the database for version changes and updates the Lucene search index asynchronously. Only active
 * when Lucene search is enabled and update mode is ASYNCHRONOUS. Designed for multi-node SQL deployments
 * where CDI events only fire on the node that handled the request.
 */
@ApplicationScoped
public class AsynchronousLuceneIndexUpdater {

    private static final Logger log = LoggerFactory.getLogger(AsynchronousLuceneIndexUpdater.class);

    private static final int RECONCILIATION_INTERVAL = 100; // Run reconciliation every N polls

    @Inject
    LuceneSearchConfig config;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    LuceneDocumentBuilder documentBuilder;

    @Inject
    LuceneIndexWriter indexWriter;

    @Inject
    LuceneIndexSearcher indexSearcher;

    @Inject
    LuceneStartupIndexer startupIndexer;

    private boolean isActive;
    private long lastPolledTimestamp;
    private int pollCount;

    @PostConstruct
    void initialize() {
        isActive = config.isEnabled() && config.getUpdateMode() == IndexUpdateMode.ASYNCHRONOUS;

        if (isActive) {
            log.info("Asynchronous search index updates ENABLED");
            log.info("  - Polling interval: {}", config.getPollingInterval());
            log.info("  - Full rebuild threshold: {}", config.getFullRebuildThreshold());
        } else if (config.isEnabled()) {
            log.info("Asynchronous search index updates DISABLED (mode is {})",
                    config.getUpdateMode());
        }
    }

    /**
     * Scheduled polling method that checks for version changes and updates the index.
     */
    @Scheduled(concurrentExecution = SKIP,
            every = "{apicurio.search.lucene.polling-interval}",
            delay = 10)
    void pollAndUpdateIndex() {
        if (!isActive) {
            return;
        }

        try {
            if (!storage.isReady()) {
                log.debug("Skipping search index poll because storage is not ready.");
                return;
            }

            if (!startupIndexer.isReady()) {
                log.debug("Skipping poll - startup reindex not yet complete.");
                return;
            }

            // Initialize lastPolledTimestamp from the startup indexer's completion time
            if (lastPolledTimestamp == 0) {
                lastPolledTimestamp = startupIndexer.getCompletedTimestamp();
            }

            // First run or empty index: do a full rebuild
            if (!indexWriter.isInitialized() || indexWriter.getDocumentCount() == 0) {
                log.info("Search index is empty, performing full index rebuild...");
                rebuildIndex();
                return;
            }

            // Normal incremental poll
            List<ArtifactVersionMetaDataDto> modifiedVersions =
                    storage.getVersionsModifiedSince(lastPolledTimestamp);

            if (modifiedVersions.isEmpty()) {
                log.debug("No version changes detected since last poll.");
            } else if (modifiedVersions.size() > config.getFullRebuildThreshold()) {
                log.info("Large number of changes detected ({}), performing full index rebuild...",
                        modifiedVersions.size());
                rebuildIndex();
            } else {
                log.debug("Processing {} modified versions...", modifiedVersions.size());
                applyIncrementalUpdates(modifiedVersions);
            }

            lastPolledTimestamp = System.currentTimeMillis();
            pollCount++;

            // Periodic reconciliation to detect deleted versions
            if (pollCount % RECONCILIATION_INTERVAL == 0) {
                reconcileIndex();
            }

        } catch (Exception e) {
            log.error("Error during search index poll", e);
        }
    }

    /**
     * Rebuilds the entire index from scratch by querying all versions from the database.
     */
    private void rebuildIndex() {
        try {
            log.info("Starting full search index rebuild...");
            long startTime = System.currentTimeMillis();

            // Clear the existing index
            indexWriter.deleteAll();
            indexWriter.commit();

            // Get all versions from the database using the modified-since method with timestamp 0
            List<ArtifactVersionMetaDataDto> allVersions = storage.getVersionsModifiedSince(0);

            int indexedCount = 0;
            int errorCount = 0;
            for (ArtifactVersionMetaDataDto versionMetadata : allVersions) {
                try {
                    indexVersion(versionMetadata);
                    indexedCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.warn("Failed to index version {}/{}/{} (globalId={})",
                            versionMetadata.getGroupId(), versionMetadata.getArtifactId(),
                            versionMetadata.getVersion(), versionMetadata.getGlobalId(), e);
                }
            }

            // Batch commit
            indexWriter.commit();
            indexSearcher.refresh();

            lastPolledTimestamp = System.currentTimeMillis();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Full index rebuild complete: {} versions indexed, {} errors, took {}ms",
                    indexedCount, errorCount, duration);

        } catch (Exception e) {
            log.error("Failed to rebuild search index", e);
        }
    }

    /**
     * Applies incremental updates for a batch of modified versions.
     *
     * @param modifiedVersions The list of modified versions to index
     */
    private void applyIncrementalUpdates(List<ArtifactVersionMetaDataDto> modifiedVersions) {
        try {
            int indexedCount = 0;
            int errorCount = 0;

            for (ArtifactVersionMetaDataDto versionMetadata : modifiedVersions) {
                try {
                    indexVersion(versionMetadata);
                    indexedCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.warn("Failed to index version {}/{}/{} (globalId={})",
                            versionMetadata.getGroupId(), versionMetadata.getArtifactId(),
                            versionMetadata.getVersion(), versionMetadata.getGlobalId(), e);
                }
            }

            // Batch commit after all updates
            indexWriter.commit();
            indexSearcher.refresh();

            log.debug("Incremental update complete: {} versions indexed, {} errors",
                    indexedCount, errorCount);

        } catch (Exception e) {
            log.error("Failed to apply incremental index updates", e);
        }
    }

    /**
     * Indexes a single version by fetching its content from storage. The version metadata is already
     * provided from the poll query.
     *
     * @param versionMetadata The version metadata (already fetched)
     * @throws IOException if indexing fails
     */
    private void indexVersion(ArtifactVersionMetaDataDto versionMetadata) throws IOException {
        // Fetch content
        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(
                versionMetadata.getGroupId(), versionMetadata.getArtifactId(),
                versionMetadata.getVersion());
        ContentHandle content = storedVersion.getContent();

        // Build Lucene document
        Document doc = documentBuilder.buildVersionDocument(versionMetadata, content.bytes());

        // Update index (using globalId as unique key) - no per-document commit in async mode
        indexWriter.updateDocument(
                new Term("globalId", String.valueOf(versionMetadata.getGlobalId())), doc);
    }

    /**
     * Reconciles the index with the database to detect and remove entries for deleted versions.
     * Compares globalIds in the index with globalIds in the database, removing any orphaned entries.
     */
    private void reconcileIndex() {
        try {
            log.info("Starting periodic index reconciliation...");
            long startTime = System.currentTimeMillis();

            // Get all globalIds from the database
            List<Long> dbGlobalIds = storage.getAllVersionGlobalIds();
            Set<Long> dbGlobalIdSet = new HashSet<>(dbGlobalIds);

            // Get all globalIds from the index
            Set<Long> indexGlobalIds = getIndexedGlobalIds();

            // Find orphaned entries (in index but not in DB)
            int removedCount = 0;
            for (Long indexGlobalId : indexGlobalIds) {
                if (!dbGlobalIdSet.contains(indexGlobalId)) {
                    indexWriter.deleteDocuments(
                            new Term("globalId", String.valueOf(indexGlobalId)));
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                indexWriter.commit();
                indexSearcher.refresh();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Index reconciliation complete: {} orphaned entries removed, took {}ms",
                    removedCount, duration);

        } catch (Exception e) {
            log.error("Failed to reconcile search index", e);
        }
    }

    /**
     * Retrieves all globalIds currently stored in the Lucene index.
     *
     * @return Set of globalIds in the index
     * @throws IOException if an error occurs reading the index
     */
    private Set<Long> getIndexedGlobalIds() throws IOException {
        Set<Long> globalIds = new HashSet<>();

        TopDocs topDocs = indexSearcher.search(new MatchAllDocsQuery(), Integer.MAX_VALUE);

        Set<String> fieldsToLoad = Set.of("globalId");
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = indexSearcher.doc(scoreDoc.doc, fieldsToLoad);
            String globalIdStr = doc.get("globalId");
            if (globalIdStr != null) {
                globalIds.add(Long.parseLong(globalIdStr));
            }
        }

        return globalIds;
    }

    /**
     * Checks if asynchronous indexing is active.
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return isActive;
    }
}
