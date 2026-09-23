package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.OpType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Startup component that checks whether the Elasticsearch search index is empty when the
 * application starts and performs a full reindex from the database if necessary. This handles
 * the case where the application starts with existing data in the database but an empty search
 * index (e.g. after an upgrade or enabling indexing on an existing deployment).
 *
 * <p>Follows the {@code ImportLifecycleBean} pattern: observes the storage READY event, performs
 * work asynchronously, and blocks application readiness via
 * {@link io.apicurio.registry.metrics.health.readiness.ElasticsearchIndexReadinessCheck}
 * until the reindex is complete.</p>
 */
@ApplicationScoped
public class ElasticsearchStartupIndexer {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchStartupIndexer.class);

    private static final int PROGRESS_LOG_INTERVAL = 100;
    private static final int BULK_BATCH_SIZE = 500;
    private static final String REINDEX_LOCK_DOC_ID = ElasticsearchIndexManager.REINDEX_LOCK_DOC_ID;

    @Inject
    ElasticsearchSearchConfig config;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    ElasticsearchDocumentBuilder documentBuilder;

    @Inject
    ArtifactTypeUtilProviderFactory typeProviderFactory;

    @Inject
    ElasticsearchClient client;

    @Inject
    ElasticsearchIndexManager indexManager;

    private volatile boolean ready = false;

    /**
     * Observes the storage READY event and performs a full reindex if the Elasticsearch index
     * is empty.
     *
     * @param ev the storage event
     */
    void onStorageReady(@ObservesAsync StorageEvent ev) {
        if (!StorageEventType.READY.equals(ev.getType())) {
            return;
        }

        log.info("Storage is ready — checking if Elasticsearch startup reindex is needed.");

        if (!config.isEnabled()) {
            log.info("Elasticsearch search indexing is disabled, skipping startup reindex.");
            ready = true;
            return;
        }

        try {
            indexManager.ensureIndexExists();

            long documentCount = indexManager.count();
            if (documentCount > 0) {
                log.info("Elasticsearch index already contains {} documents, "
                        + "skipping startup reindex.", documentCount);
                ready = true;
                return;
            }

            // Try to acquire a distributed lock via ES document creation.
            // OpType.Create fails with 409 if the document already exists,
            // ensuring only one replica performs the reindex.
            if (!acquireReindexLock()) {
                log.info("Another replica has acquired the reindex lock, skipping startup reindex.");
                ready = true;
                return;
            }

            try {
                log.info("Elasticsearch index is empty — starting full reindex from database.");
                reindex();
            } finally {
                releaseReindexLock();
            }
        } catch (Exception e) {
            log.error("Startup reindex failed", e);
        } finally {
            ready = true;
            log.info("Elasticsearch startup indexer is now ready.");
        }
    }

    /**
     * Attempts to acquire a distributed reindex lock by creating a lock document in
     * Elasticsearch. Uses {@link OpType#Create} which atomically fails with a 409 conflict
     * if the document already exists, ensuring only one replica can acquire the lock.
     *
     * @return {@code true} if the lock was acquired, {@code false} if another replica holds it
     */
    private boolean acquireReindexLock() {
        try {
            client.index(i -> i
                    .index(config.getIndexName())
                    .id(REINDEX_LOCK_DOC_ID)
                    .opType(OpType.Create)
                    .document(Map.of("type", "reindex_lock", "timestamp", System.currentTimeMillis()))
            );
            log.info("Acquired reindex lock.");
            return true;
        } catch (ElasticsearchException e) {
            if (e.status() == 409) {
                return false;
            }
            throw new RuntimeException("Unexpected error acquiring reindex lock", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to acquire reindex lock", e);
        }
    }

    /**
     * Releases the distributed reindex lock by deleting the lock document from Elasticsearch.
     * Called in a {@code finally} block after reindex to ensure cleanup even on failure.
     */
    private void releaseReindexLock() {
        try {
            client.delete(d -> d
                    .index(config.getIndexName())
                    .id(REINDEX_LOCK_DOC_ID)
            );
            log.info("Released reindex lock.");
        } catch (Exception e) {
            log.warn("Failed to release reindex lock document — it will be cleared "
                    + "on next triggerReindex() or deleteAllDocuments() call.", e);
        }
    }

    /**
     * Performs a full reindex by streaming all versions with their content from storage using
     * the Elasticsearch bulk API for efficient batch indexing. Disables automatic index
     * refreshes during the bulk operation to improve throughput, then restores the original
     * refresh interval and performs an explicit refresh when complete.
     */
    private void reindex() {
        log.info("Starting startup reindex of Elasticsearch search index...");
        long startTime = System.currentTimeMillis();

        // Disable automatic refresh during bulk indexing for better throughput
        String previousRefreshInterval = null;
        try {
            previousRefreshInterval = indexManager.getRefreshInterval();
            indexManager.disableRefresh();
        } catch (IOException e) {
            log.warn("Failed to disable automatic refresh during reindex, continuing anyway", e);
        }

        try {
            int[] counts = {0, 0}; // [indexed, errors]
            BulkRequest.Builder[] bulkBuilder = {new BulkRequest.Builder()};
            int[] batchCount = {0};

            storage.forEachVersion(versionContent -> {
                try {
                    ArtifactVersionMetaDataDto metadata = versionContent.toMetaDataDto();
                    byte[] contentBytes = versionContent.getContent().bytes();

                    StructuredContentExtractor extractor = null;
                    if (metadata.getArtifactType() != null) {
                        extractor = typeProviderFactory.getArtifactTypeProvider(
                                metadata.getArtifactType()).getStructuredContentExtractor();
                    }

                    Map<String, Object> doc = documentBuilder.buildVersionDocument(metadata,
                            contentBytes, extractor);

                    String docId = String.valueOf(metadata.getGlobalId());
                    bulkBuilder[0].operations(op -> op
                            .index(idx -> idx
                                    .index(config.getIndexName())
                                    .id(docId)
                                    .document(doc)
                            )
                    );

                    counts[0]++;
                    batchCount[0]++;

                    if (batchCount[0] >= BULK_BATCH_SIZE) {
                        flushBulk(bulkBuilder[0], counts);
                        bulkBuilder[0] = new BulkRequest.Builder();
                        batchCount[0] = 0;
                    }

                    if (counts[0] % PROGRESS_LOG_INTERVAL == 0) {
                        log.info("Startup reindex progress: {} versions indexed so far ({} errors).",
                                counts[0], counts[1]);
                    }
                } catch (Exception e) {
                    counts[1]++;
                    log.warn("Failed to index version {}/{}/{} (globalId={}) during startup reindex",
                            versionContent.getGroupId(), versionContent.getArtifactId(),
                            versionContent.getVersion(), versionContent.getGlobalId(), e);
                }
            });

            // Flush remaining documents
            if (batchCount[0] > 0) {
                flushBulk(bulkBuilder[0], counts);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Startup reindex complete: {} versions indexed, {} errors, took {}ms",
                    counts[0], counts[1], duration);
        } finally {
            // Restore the original refresh interval and make all documents searchable
            try {
                indexManager.restoreRefresh(previousRefreshInterval);
            } catch (IOException e) {
                log.error("Failed to restore refresh interval after reindex", e);
            }
            try {
                indexManager.refresh();
            } catch (Exception e) {
                log.error("Failed to refresh index after startup reindex", e);
            }
        }
    }

    /**
     * Flushes the current bulk request batch to Elasticsearch.
     *
     * @param bulkBuilder the bulk request builder
     * @param counts the counts array [indexed, errors]
     */
    private void flushBulk(BulkRequest.Builder bulkBuilder, int[] counts) {
        try {
            BulkResponse response = client.bulk(bulkBuilder.build());
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        counts[1]++;
                        log.warn("Bulk indexing error for document {}: {}",
                                item.id(), item.error().reason());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to execute bulk index request", e);
            counts[1]++;
        }
    }

    /**
     * Returns whether the startup reindex is complete (or was skipped).
     *
     * @return true when the startup indexer has finished or determined no reindex is needed
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Triggers a full reindex of the search index by clearing all existing documents
     * and rebuilding from the database. Used after bulk operations like data import
     * or upgrade that bypass the normal per-entity event mechanism.
     */
    public void triggerReindex() {
        try {
            log.info("Triggering full reindex of Elasticsearch search index...");
            indexManager.deleteAllDocuments();
            reindex();
            log.info("Full reindex triggered by import/upgrade completed successfully.");
        } catch (Exception e) {
            log.error("Full reindex triggered by import/upgrade failed. "
                    + "The search index may be out of sync with the database.", e);
        }
    }

    /**
     * Observes the {@link ReindexRequestedEvent} CDI event and triggers a full reindex.
     *
     * @param event the reindex requested event
     */
    public void onReindexRequested(@Observes ReindexRequestedEvent event) {
        if (!config.isEnabled()) {
            return;
        }
        triggerReindex();
    }
}
