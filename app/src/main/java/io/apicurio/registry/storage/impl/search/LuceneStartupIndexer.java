package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.StorageEvent;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchedVersionDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.dto.VersionSearchResultsDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

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

    private static final int BATCH_SIZE = 100;

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
     * Performs a full reindex by paginating through all versions in storage and indexing each one.
     * Uses {@code storage.searchVersions()} for enumeration, which works for all storage types.
     * During the reindex, the {@link io.apicurio.registry.storage.decorator.LuceneSearchDecorator}
     * falls through to SQL because {@link #isReady()} returns false.
     */
    private void reindex() {
        log.info("Starting startup reindex of Lucene search index...");
        long startTime = System.currentTimeMillis();

        int offset = 0;
        int indexedCount = 0;
        int errorCount = 0;

        while (true) {
            VersionSearchResultsDto results = storage.searchVersions(
                    Collections.emptySet(), OrderBy.globalId, OrderDirection.asc,
                    offset, BATCH_SIZE);

            List<SearchedVersionDto> versions = results.getVersions();
            if (versions.isEmpty()) {
                break;
            }

            for (SearchedVersionDto searchedVersion : versions) {
                try {
                    indexVersion(searchedVersion);
                    indexedCount++;
                } catch (Exception e) {
                    errorCount++;
                    log.warn("Failed to index version {}/{}/{} (globalId={}) during startup reindex",
                            searchedVersion.getGroupId(), searchedVersion.getArtifactId(),
                            searchedVersion.getVersion(), searchedVersion.getGlobalId(), e);
                }
            }

            offset += versions.size();
            log.info("Startup reindex progress: {} versions indexed so far ({} errors).",
                    indexedCount, errorCount);

            if (versions.size() < BATCH_SIZE) {
                break;
            }
        }

        try {
            indexWriter.commit();
            indexSearcher.refresh();
        } catch (Exception e) {
            log.error("Failed to commit/refresh after startup reindex", e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Startup reindex complete: {} versions indexed, {} errors, took {}ms",
                indexedCount, errorCount, duration);
    }

    /**
     * Indexes a single version by fetching its full metadata and content from storage.
     *
     * @param searchedVersion the searched version DTO from the paginated query
     * @throws Exception if indexing fails
     */
    private void indexVersion(SearchedVersionDto searchedVersion) throws Exception {
        // Fetch full metadata
        ArtifactVersionMetaDataDto metadata = storage.getArtifactVersionMetaData(
                searchedVersion.getGroupId(), searchedVersion.getArtifactId(),
                searchedVersion.getVersion());

        // Fetch content
        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(
                searchedVersion.getGroupId(), searchedVersion.getArtifactId(),
                searchedVersion.getVersion());
        ContentHandle content = storedVersion.getContent();

        // Build and index document
        Document doc = documentBuilder.buildVersionDocument(metadata, content.bytes());
        indexWriter.updateDocument(
                new Term("globalId", String.valueOf(metadata.getGlobalId())), doc);
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
