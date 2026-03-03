package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

            // In multi-replica deployments, all replicas may reach this point at roughly
            // the same time after index creation. Wait briefly and recheck — if another
            // replica has already started indexing, we can skip the redundant work.
            Thread.sleep(2000 + (long) (Math.random() * 3000));
            documentCount = indexManager.count();
            if (documentCount > 0) {
                log.info("Another replica has started indexing ({} documents found), "
                        + "skipping startup reindex.", documentCount);
                ready = true;
                return;
            }

            log.info("Elasticsearch index is empty — starting full reindex from database.");
            reindex();
        } catch (Exception e) {
            log.error("Startup reindex failed", e);
        } finally {
            ready = true;
            log.info("Elasticsearch startup indexer is now ready.");
        }
    }

    /**
     * Performs a full reindex by streaming all versions with their content from storage using
     * the Elasticsearch bulk API for efficient batch indexing.
     */
    private void reindex() {
        log.info("Starting startup reindex of Elasticsearch search index...");
        long startTime = System.currentTimeMillis();

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

        // Refresh to make all documents searchable
        try {
            indexManager.refresh();
        } catch (Exception e) {
            log.error("Failed to refresh index after startup reindex", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Startup reindex complete: {} versions indexed, {} errors, took {}ms",
                counts[0], counts[1], duration);
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
}
