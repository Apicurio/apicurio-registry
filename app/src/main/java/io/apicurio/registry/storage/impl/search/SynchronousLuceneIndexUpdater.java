package io.apicurio.registry.storage.impl.search;

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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 * Observes CDI events for version changes and updates the Lucene search index synchronously. Only active
 * when Lucene search is enabled and update mode is SYNCHRONOUS.
 */
@ApplicationScoped
public class SynchronousLuceneIndexUpdater {

    private static final Logger log = LoggerFactory.getLogger(SynchronousLuceneIndexUpdater.class);

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

    private boolean isActive;

    @PostConstruct
    void initialize() {
        isActive = config.isEnabled() && config.getUpdateMode() == IndexUpdateMode.SYNCHRONOUS;

        if (isActive) {
            log.info("Synchronous search index updates ENABLED");
        } else if (config.isEnabled()) {
            log.info("Synchronous search index updates DISABLED (mode is {})",
                    config.getUpdateMode());
        }
    }

    /**
     * Observes version creation and immediately indexes the new version.
     */
    public void onVersionCreated(@Observes VersionCreatedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Indexing newly created version: {}/{}/{}", event.getGroupId(),
                    event.getArtifactId(), event.getVersion());

            indexVersion(event.getGroupId(), event.getArtifactId(), event.getVersion(),
                    event.getGlobalId());

            log.debug("Successfully indexed version {}/{}/{} (globalId={})", event.getGroupId(),
                    event.getArtifactId(), event.getVersion(), event.getGlobalId());

        } catch (Exception e) {
            log.error("Failed to index new version {}/{}/{}", event.getGroupId(),
                    event.getArtifactId(), event.getVersion(), e);
            // Don't throw - logging is sufficient, don't break version creation
        }
    }

    /**
     * Observes artifact metadata updates and re-indexes all affected versions.
     */
    public void onArtifactMetadataUpdated(@Observes ArtifactMetadataUpdatedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Re-indexing versions for artifact with updated metadata: {}/{}",
                    event.getGroupId(), event.getArtifactId());

            // Get all versions of this artifact
            VersionSearchResultsDto versions = storage.searchVersions(
                    Set.of(SearchFilter.ofGroupId(event.getGroupId()),
                            SearchFilter.ofArtifactId(event.getArtifactId())),
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
                    event.getGroupId(), event.getArtifactId());

        } catch (Exception e) {
            log.error("Failed to re-index versions for artifact {}/{}", event.getGroupId(),
                    event.getArtifactId(), e);
        }
    }

    /**
     * Observes version deletion and removes from index.
     */
    public void onVersionDeleted(@Observes VersionDeletedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Removing deleted version from index: {}/{}/{}", event.getGroupId(),
                    event.getArtifactId(), event.getVersion());

            indexWriter.deleteDocuments(new Term("globalId", String.valueOf(event.getGlobalId())));

            indexWriter.commit();
            indexSearcher.refresh();

            log.debug("Successfully removed version {}/{}/{} from index", event.getGroupId(),
                    event.getArtifactId(), event.getVersion());

        } catch (Exception e) {
            log.error("Failed to remove version from index: {}/{}/{}", event.getGroupId(),
                    event.getArtifactId(), event.getVersion(), e);
        }
    }

    /**
     * Observes version state changes and updates the index.
     */
    public void onVersionStateChanged(@Observes VersionStateChangedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Updating index for version state change: {}/{}/{} ({} -> {})",
                    event.getGroupId(), event.getArtifactId(), event.getVersion(),
                    event.getOldState(), event.getNewState());

            // Re-index the version with new state
            indexVersion(event.getGroupId(), event.getArtifactId(), event.getVersion(),
                    event.getGlobalId());

            log.debug("Successfully updated index for version state change: {}/{}/{}",
                    event.getGroupId(), event.getArtifactId(), event.getVersion());

        } catch (Exception e) {
            log.error("Failed to update index for version state change: {}/{}/{}",
                    event.getGroupId(), event.getArtifactId(), event.getVersion(), e);
        }
    }

    /**
     * Indexes a single version by fetching its metadata and content from storage.
     *
     * @param groupId The group ID
     * @param artifactId The artifact ID
     * @param version The version string
     * @param globalId The global ID
     * @throws IOException if indexing fails
     */
    private void indexVersion(String groupId, String artifactId, String version, long globalId)
            throws IOException {

        // Fetch version metadata
        ArtifactVersionMetaDataDto versionMetadata = storage.getArtifactVersionMetaData(groupId,
                artifactId, version);

        // Fetch content
        StoredArtifactVersionDto storedVersion = storage.getArtifactVersionContent(groupId,
                artifactId, version);
        ContentHandle content = storedVersion.getContent();

        // Look up the structured content extractor for this artifact type
        StructuredContentExtractor extractor = null;
        if (versionMetadata.getArtifactType() != null) {
            extractor = typeProviderFactory.getArtifactTypeProvider(
                    versionMetadata.getArtifactType()).getStructuredContentExtractor();
        }

        // Build Lucene document
        Document doc = documentBuilder.buildVersionDocument(versionMetadata, content.bytes(),
                extractor);

        // Update index (using globalId as unique key)
        indexWriter.updateDocument(new Term("globalId", String.valueOf(globalId)), doc);

        // Commit and refresh immediately (synchronous mode)
        indexWriter.commit();
        indexSearcher.refresh();
    }

    /**
     * Checks if synchronous indexing is active.
     *
     * @return true if active, false otherwise
     */
    public boolean isActive() {
        return isActive;
    }
}
