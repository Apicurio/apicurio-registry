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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Observes CDI events for version changes and updates the Elasticsearch search index. All updates
 * are synchronous since all registry nodes share a single Elasticsearch cluster.
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

    private boolean isActive;

    @PostConstruct
    void initialize() {
        isActive = config.isEnabled();

        if (isActive) {
            log.info("Elasticsearch search index updates ENABLED");
        }
    }

    /**
     * Observes version creation and immediately indexes the new version.
     *
     * @param event the version created event
     */
    public void onVersionCreated(@Observes VersionCreatedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Indexing newly created version: {}/{}/{}",
                    event.getGroupId(), event.getArtifactId(), event.getVersion());

            indexVersion(event.getGroupId(), event.getArtifactId(), event.getVersion(),
                    event.getGlobalId());

            log.debug("Successfully indexed version {}/{}/{} (globalId={})",
                    event.getGroupId(), event.getArtifactId(), event.getVersion(),
                    event.getGlobalId());

        } catch (Exception e) {
            log.error("Failed to index new version {}/{}/{}", event.getGroupId(),
                    event.getArtifactId(), event.getVersion(), e);
        }
    }

    /**
     * Observes artifact metadata updates and re-indexes all affected versions.
     *
     * @param event the artifact metadata updated event
     */
    public void onArtifactMetadataUpdated(@Observes ArtifactMetadataUpdatedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Re-indexing versions for artifact with updated metadata: {}/{}",
                    event.getGroupId(), event.getArtifactId());

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
            log.error("Failed to re-index versions for artifact {}/{}",
                    event.getGroupId(), event.getArtifactId(), e);
        }
    }

    /**
     * Observes version deletion and removes from index.
     *
     * @param event the version deleted event
     */
    public void onVersionDeleted(@Observes VersionDeletedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Removing deleted version from index: {}/{}/{}",
                    event.getGroupId(), event.getArtifactId(), event.getVersion());

            client.delete(d -> d
                    .index(config.getIndexName())
                    .id(String.valueOf(event.getGlobalId()))
                    .refresh(Refresh.WaitFor)
            );

            log.debug("Successfully removed version {}/{}/{} from index",
                    event.getGroupId(), event.getArtifactId(), event.getVersion());

        } catch (Exception e) {
            log.error("Failed to remove version from index: {}/{}/{}",
                    event.getGroupId(), event.getArtifactId(), event.getVersion(), e);
        }
    }

    /**
     * Observes artifact deletion and removes all versions of the artifact from the index.
     *
     * @param event the artifact deleted event
     */
    public void onArtifactDeleted(@Observes ArtifactDeletedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Removing all versions of deleted artifact from index: {}/{}",
                    event.getGroupId(), event.getArtifactId());

            client.deleteByQuery(d -> d
                    .index(config.getIndexName())
                    .query(q -> q
                            .bool(b -> b
                                    .must(m -> m.term(t -> t
                                            .field("groupId")
                                            .value(event.getGroupId())))
                                    .must(m -> m.term(t -> t
                                            .field("artifactId")
                                            .value(event.getArtifactId())))))
                    .refresh(true)
            );

            log.debug("Successfully removed all versions of artifact {}/{} from index",
                    event.getGroupId(), event.getArtifactId());

        } catch (Exception e) {
            log.error("Failed to remove artifact versions from index: {}/{}",
                    event.getGroupId(), event.getArtifactId(), e);
        }
    }

    /**
     * Observes group deletion and removes all versions in the group from the index.
     *
     * @param event the group deleted event
     */
    public void onGroupDeleted(@Observes GroupDeletedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Removing all versions in deleted group from index: {}",
                    event.getGroupId());

            client.deleteByQuery(d -> d
                    .index(config.getIndexName())
                    .query(q -> q
                            .term(t -> t
                                    .field("groupId")
                                    .value(event.getGroupId())))
                    .refresh(true)
            );

            log.debug("Successfully removed all versions in group {} from index",
                    event.getGroupId());

        } catch (Exception e) {
            log.error("Failed to remove group versions from index: {}",
                    event.getGroupId(), e);
        }
    }

    /**
     * Observes deletion of all user data and clears the entire index.
     *
     * @param event the all data deleted event
     */
    public void onAllDataDeleted(@Observes AllDataDeletedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Removing all data from search index");

            client.deleteByQuery(d -> d
                    .index(config.getIndexName())
                    .query(q -> q
                            .matchAll(m -> m))
                    .refresh(true)
            );

            log.debug("Successfully removed all data from search index");

        } catch (Exception e) {
            log.error("Failed to remove all data from search index", e);
        }
    }

    /**
     * Observes version state changes and updates the index.
     *
     * @param event the version state changed event
     */
    public void onVersionStateChanged(@Observes VersionStateChangedEvent event) {
        if (!isActive) {
            return;
        }

        try {
            log.debug("Updating index for version state change: {}/{}/{} ({} -> {})",
                    event.getGroupId(), event.getArtifactId(), event.getVersion(),
                    event.getOldState(), event.getNewState());

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
                .refresh(Refresh.WaitFor)
        );
    }

    /**
     * Checks if index updating is active.
     *
     * @return true if active
     */
    public boolean isActive() {
        return isActive;
    }
}
