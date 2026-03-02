package io.apicurio.registry.storage.impl.search;

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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startup component that checks whether the Lucene search index is empty when the application
 * starts and performs a full reindex from the database if necessary. This handles the case where
 * the application starts with existing data in the database but an empty search index (e.g. after
 * an upgrade or enabling indexing on an existing deployment).
 *
 * <p>Follows the {@code ImportLifecycleBean} pattern: observes the storage READY event, performs
 * work asynchronously, and blocks application readiness via {@link io.apicurio.registry.metrics.health.readiness.LuceneIndexReadinessCheck}
 * until the reindex is complete.</p>
 */
@ApplicationScoped
public class LuceneStartupIndexer {

    private static final Logger log = LoggerFactory.getLogger(LuceneStartupIndexer.class);

    private static final int PROGRESS_LOG_INTERVAL = 100;

    @Inject
    LuceneSearchConfig config;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    LuceneDocumentBuilder documentBuilder;

    @Inject
    ArtifactTypeUtilProviderFactory typeProviderFactory;

    @Inject
    LuceneIndexWriter indexWriter;

    @Inject
    LuceneIndexSearcher indexSearcher;

    private volatile boolean ready = false;
    private volatile long completedTimestamp = 0;

    /**
     * Observes the storage READY event and performs a full reindex if the Lucene index is empty.
     *
     * @param ev the storage event
     */
    void onStorageReady(@ObservesAsync StorageEvent ev) {
        if (!StorageEventType.READY.equals(ev.getType())) {
            return;
        }

        log.info("Storage is ready — checking if Lucene startup reindex is needed.");

        if (!config.isEnabled()) {
            log.info("Lucene search indexing is disabled, skipping startup reindex.");
            ready = true;
            return;
        }

        if (!indexWriter.isInitialized()) {
            log.warn("Lucene index writer not initialized, skipping startup reindex.");
            ready = true;
            return;
        }

        int documentCount = indexWriter.getDocumentCount();
        if (documentCount > 0) {
            log.info("Lucene search index already contains {} documents, skipping startup reindex.",
                    documentCount);
            completedTimestamp = System.currentTimeMillis();
            ready = true;
            return;
        }

        log.info("Lucene search index is empty — starting full reindex from database.");

        // Index is empty — perform full reindex
        try {
            reindex();
        } catch (Exception e) {
            log.error("Startup reindex failed", e);
        } finally {
            completedTimestamp = System.currentTimeMillis();
            ready = true;
            log.info("Lucene startup indexer is now ready.");
        }
    }

    /**
     * Performs a full reindex by streaming all versions with their content from storage in a
     * single cursor-based query. During the reindex, the
     * {@link io.apicurio.registry.storage.decorator.LuceneSearchDecorator} falls through to SQL
     * because {@link #isReady()} returns false.
     */
    private void reindex() {
        log.info("Starting startup reindex of Lucene search index...");
        long startTime = System.currentTimeMillis();

        int[] counts = {0, 0}; // [indexed, errors]

        storage.forEachVersion(versionContent -> {
            try {
                ArtifactVersionMetaDataDto metadata = versionContent.toMetaDataDto();
                byte[] contentBytes = versionContent.getContent().bytes();

                // Look up the structured content extractor for this artifact type
                StructuredContentExtractor extractor = null;
                if (metadata.getArtifactType() != null) {
                    extractor = typeProviderFactory.getArtifactTypeProvider(
                            metadata.getArtifactType()).getStructuredContentExtractor();
                }

                Document doc = documentBuilder.buildVersionDocument(metadata, contentBytes,
                        extractor);
                indexWriter.updateDocument(
                        new Term("globalId", String.valueOf(metadata.getGlobalId())), doc);
                counts[0]++;
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

        try {
            indexWriter.commit();
            indexSearcher.refresh();
        } catch (Exception e) {
            log.error("Failed to commit/refresh after startup reindex", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Startup reindex complete: {} versions indexed, {} errors, took {}ms",
                counts[0], counts[1], duration);
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
     * Returns the timestamp when the startup reindex completed. Used by the asynchronous poller
     * to initialize its polling timestamp, avoiding re-fetching versions that were just indexed.
     *
     * @return the completion timestamp in milliseconds, or 0 if not yet complete
     */
    public long getCompletedTimestamp() {
        return completedTimestamp;
    }
}
