package io.apicurio.registry.storage.impl.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import io.apicurio.registry.types.provider.ArtifactTypeUtilProviderFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Observes CDI events for version changes and updates the Elasticsearch search index. Updates
 * are processed asynchronously via a dedicated background worker thread, making the search index
 * eventually consistent. This improves write performance by offloading indexing from the request
 * thread.
 */
@ApplicationScoped
public class ElasticsearchIndexUpdater {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexUpdater.class);

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

    private boolean isActive;
    private final LinkedBlockingQueue<IndexingOperation> operationQueue = new LinkedBlockingQueue<>();
    private volatile Thread workerThread;
    private volatile boolean running;

    @PostConstruct
    void initialize() {
        isActive = config.isEnabled();

        if (isActive) {
            log.info("Elasticsearch search index updates ENABLED");
            running = true;
            workerThread = new Thread(this::processQueue, "es-index-updater");
            workerThread.setDaemon(true);
            workerThread.start();
        }
    }

    @PreDestroy
    void shutdown() {
        if (!isActive) {
            return;
        }

        running = false;
        Thread thread = workerThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        drainQueue();
    }

    /**
     * Observes version creation and enqueues indexing of the new version.
     *
     * @param event the version created event
     */
    public void onVersionCreated(@Observes VersionCreatedEvent event) {
        if (!isActive) {
            return;
        }
        operationQueue.add(new IndexingOperation.IndexVersion(
                event.getGroupId(), event.getArtifactId(), event.getVersion(),
                event.getGlobalId()));
    }

    /**
     * Observes artifact metadata updates and enqueues re-indexing of all affected versions.
     *
     * @param event the artifact metadata updated event
     */
    public void onArtifactMetadataUpdated(@Observes ArtifactMetadataUpdatedEvent event) {
        if (!isActive) {
            return;
        }
        operationQueue.add(new IndexingOperation.ReindexArtifactVersions(
                event.getGroupId(), event.getArtifactId()));
    }

    /**
     * Observes version deletion and enqueues removal from the index.
     *
     * @param event the version deleted event
     */
    public void onVersionDeleted(@Observes VersionDeletedEvent event) {
        if (!isActive) {
            return;
        }
        operationQueue.add(new IndexingOperation.DeleteVersion(
                event.getGroupId(), event.getArtifactId(), event.getVersion(),
                event.getGlobalId()));
    }

    /**
     * Observes artifact deletion and enqueues removal of all versions from the index.
     *
     * @param event the artifact deleted event
     */
    public void onArtifactDeleted(@Observes ArtifactDeletedEvent event) {
        if (!isActive) {
            return;
        }
        operationQueue.add(new IndexingOperation.DeleteArtifact(
                event.getGroupId(), event.getArtifactId()));
    }

    /**
     * Observes group deletion and enqueues removal of all versions in the group from the index.
     *
     * @param event the group deleted event
     */
    public void onGroupDeleted(@Observes GroupDeletedEvent event) {
        if (!isActive) {
            return;
        }
        operationQueue.add(new IndexingOperation.DeleteGroup(event.getGroupId()));
    }

    /**
     * Observes deletion of all user data and enqueues clearing of the entire index.
     *
     * @param event the all data deleted event
     */
    public void onAllDataDeleted(@Observes AllDataDeletedEvent event) {
        if (!isActive) {
            return;
        }
        operationQueue.add(new IndexingOperation.DeleteAllData());
    }

    /**
     * Observes version state changes and enqueues re-indexing of the version.
     *
     * @param event the version state changed event
     */
    public void onVersionStateChanged(@Observes VersionStateChangedEvent event) {
        if (!isActive) {
            return;
        }
        operationQueue.add(new IndexingOperation.IndexVersion(
                event.getGroupId(), event.getArtifactId(), event.getVersion(),
                event.getGlobalId()));
    }

    /**
     * Waits for the operation queue to drain and all pending operations to complete, then
     * refreshes the Elasticsearch index so that all processed documents are immediately
     * searchable. Intended for use in tests to synchronize with the asynchronous indexing.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws IllegalStateException if the timeout expires before the queue is idle
     */
    public void awaitIdle(long timeout, TimeUnit unit) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);

        while (!operationQueue.isEmpty()) {
            if (System.nanoTime() >= deadlineNanos) {
                throw new IllegalStateException(
                        "Timed out waiting for indexing queue to drain. Remaining items: "
                                + operationQueue.size());
            }
            Thread.sleep(50);
        }

        // Allow the last in-flight operation to complete
        Thread.sleep(100);

        try {
            indexManager.refresh();
        } catch (IOException e) {
            throw new RuntimeException("Failed to refresh Elasticsearch index", e);
        }
    }

    /**
     * Checks if index updating is active.
     *
     * @return true if active
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Worker loop that processes indexing operations from the queue. Runs on a dedicated
     * background thread and blocks on {@code take()} when the queue is empty. Exits when
     * the thread is interrupted (shutdown signal).
     */
    private void processQueue() {
        log.info("Elasticsearch index updater worker thread started");
        while (running) {
            try {
                IndexingOperation operation = operationQueue.take();
                executeOperation(operation);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Elasticsearch index updater worker thread stopped");
    }

    /**
     * Dispatches a single indexing operation to the appropriate handler method. Wraps each
     * call in try-catch to log errors and continue processing the queue.
     *
     * @param operation the indexing operation to execute
     */
    private void executeOperation(IndexingOperation operation) {
        try {
            switch (operation.type()) {
                case INDEX_VERSION: {
                    IndexingOperation.IndexVersion op = (IndexingOperation.IndexVersion) operation;
                    log.debug("Indexing version: {}/{}/{}", op.groupId(), op.artifactId(),
                            op.version());
                    indexVersion(op.groupId(), op.artifactId(), op.version(), op.globalId());
                    log.debug("Successfully indexed version {}/{}/{} (globalId={})",
                            op.groupId(), op.artifactId(), op.version(), op.globalId());
                    break;
                }
                case REINDEX_ARTIFACT_VERSIONS: {
                    IndexingOperation.ReindexArtifactVersions op =
                            (IndexingOperation.ReindexArtifactVersions) operation;
                    log.debug("Re-indexing versions for artifact: {}/{}", op.groupId(),
                            op.artifactId());
                    reindexAllVersions(op.groupId(), op.artifactId());
                    log.debug("Re-indexed versions for artifact {}/{}", op.groupId(),
                            op.artifactId());
                    break;
                }
                case DELETE_VERSION: {
                    IndexingOperation.DeleteVersion op =
                            (IndexingOperation.DeleteVersion) operation;
                    log.debug("Removing deleted version from index: {}/{}/{}",
                            op.groupId(), op.artifactId(), op.version());
                    deleteVersionFromIndex(op.globalId());
                    log.debug("Successfully removed version {}/{}/{} from index",
                            op.groupId(), op.artifactId(), op.version());
                    break;
                }
                case DELETE_ARTIFACT: {
                    IndexingOperation.DeleteArtifact op =
                            (IndexingOperation.DeleteArtifact) operation;
                    log.debug("Removing all versions of deleted artifact from index: {}/{}",
                            op.groupId(), op.artifactId());
                    deleteArtifactFromIndex(op.groupId(), op.artifactId());
                    log.debug("Successfully removed all versions of artifact {}/{} from index",
                            op.groupId(), op.artifactId());
                    break;
                }
                case DELETE_GROUP: {
                    IndexingOperation.DeleteGroup op =
                            (IndexingOperation.DeleteGroup) operation;
                    log.debug("Removing all versions in deleted group from index: {}",
                            op.groupId());
                    deleteGroupFromIndex(op.groupId());
                    log.debug("Successfully removed all versions in group {} from index",
                            op.groupId());
                    break;
                }
                case DELETE_ALL_DATA: {
                    log.debug("Removing all data from search index");
                    deleteAllDataFromIndex();
                    log.debug("Successfully removed all data from search index");
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute indexing operation: {}", operation, e);
        }
    }

    /**
     * Drains remaining operations from the queue and executes them on the calling thread.
     * Used during shutdown to process any queued operations before the bean is destroyed.
     */
    private void drainQueue() {
        IndexingOperation operation;
        while ((operation = operationQueue.poll()) != null) {
            executeOperation(operation);
        }
    }

    /**
     * Indexes a single version by fetching its metadata and content from storage.
     *
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @param version the version string
     * @param globalId the global ID
     * @throws IOException if indexing fails
     */
    private void indexVersion(String groupId, String artifactId, String version, long globalId)
            throws IOException {

        ArtifactVersionMetaDataDto versionMetadata = storage.getArtifactVersionMetaData(
                groupId, artifactId, version);

        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(
                groupId, artifactId, version);
        ContentHandle content = storedVersion.getContent();

        StructuredContentExtractor extractor = null;
        if (versionMetadata.getArtifactType() != null) {
            extractor = typeProviderFactory.getArtifactTypeProvider(
                    versionMetadata.getArtifactType()).getStructuredContentExtractor();
        }

        Map<String, Object> doc = documentBuilder.buildVersionDocument(versionMetadata,
                content.bytes(), extractor);

        client.index(i -> i
                .index(config.getIndexName())
                .id(String.valueOf(globalId))
                .document(doc)
                .refresh(Refresh.False)
        );
    }

    /**
     * Re-indexes all versions of an artifact.
     *
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @throws IOException if the storage query fails
     */
    private void reindexAllVersions(String groupId, String artifactId) throws IOException {
        VersionSearchResultsDto versions = storage.searchVersions(
                Set.of(SearchFilter.ofGroupId(groupId),
                        SearchFilter.ofArtifactId(artifactId)),
                OrderBy.createdOn, OrderDirection.asc, 0, Integer.MAX_VALUE);

        int reindexedCount = 0;
        for (var version : versions.getVersions()) {
            try {
                indexVersion(version.getGroupId(), version.getArtifactId(),
                        version.getVersion(), version.getGlobalId());
                reindexedCount++;
            } catch (Exception e) {
                log.error("Failed to re-index version {}/{}/{}", version.getGroupId(),
                        version.getArtifactId(), version.getVersion(), e);
            }
        }

        log.debug("Re-indexed {} versions for artifact {}/{}", reindexedCount,
                groupId, artifactId);
    }

    /**
     * Removes a single version from the index by its global ID.
     *
     * @param globalId the global ID of the version to remove
     * @throws IOException if the delete fails
     */
    private void deleteVersionFromIndex(long globalId) throws IOException {
        client.delete(d -> d
                .index(config.getIndexName())
                .id(String.valueOf(globalId))
                .refresh(Refresh.False)
        );
    }

    /**
     * Removes all versions of an artifact from the index.
     *
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @throws IOException if the delete-by-query fails
     */
    private void deleteArtifactFromIndex(String groupId, String artifactId) throws IOException {
        client.deleteByQuery(d -> d
                .index(config.getIndexName())
                .query(q -> q
                        .bool(b -> b
                                .must(m -> m.term(t -> t
                                        .field("groupId")
                                        .value(groupId)))
                                .must(m -> m.term(t -> t
                                        .field("artifactId")
                                        .value(artifactId)))))
                .refresh(false)
        );
    }

    /**
     * Removes all versions in a group from the index.
     *
     * @param groupId the group ID
     * @throws IOException if the delete-by-query fails
     */
    private void deleteGroupFromIndex(String groupId) throws IOException {
        client.deleteByQuery(d -> d
                .index(config.getIndexName())
                .query(q -> q
                        .term(t -> t
                                .field("groupId")
                                .value(groupId)))
                .refresh(false)
        );
    }

    /**
     * Removes all data from the search index.
     *
     * @throws IOException if the delete-by-query fails
     */
    private void deleteAllDataFromIndex() throws IOException {
        client.deleteByQuery(d -> d
                .index(config.getIndexName())
                .query(q -> q
                        .matchAll(m -> m))
                .refresh(false)
        );
    }
}
