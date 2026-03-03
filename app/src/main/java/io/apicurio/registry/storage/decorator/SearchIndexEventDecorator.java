package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.impl.search.ArtifactMetadataUpdatedEvent;
import io.apicurio.registry.storage.impl.search.ElasticsearchSearchConfig;
import io.apicurio.registry.storage.impl.search.VersionCreatedEvent;
import io.apicurio.registry.storage.impl.search.VersionDeletedEvent;
import io.apicurio.registry.storage.impl.search.VersionStateChangedEvent;
import io.apicurio.registry.types.VersionState;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Storage decorator that fires CDI events for search index updates. These events are observed by
 * the ElasticsearchIndexUpdater when search indexing is enabled.
 */
@ApplicationScoped
public class SearchIndexEventDecorator extends RegistryStorageDecoratorBase
        implements RegistryStorageDecorator {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexEventDecorator.class);

    @Inject
    ElasticsearchSearchConfig config;

    @Inject
    Event<VersionCreatedEvent> versionCreatedEvent;

    @Inject
    Event<VersionDeletedEvent> versionDeletedEvent;

    @Inject
    Event<VersionStateChangedEvent> versionStateChangedEvent;

    @Inject
    Event<ArtifactMetadataUpdatedEvent> artifactMetadataUpdatedEvent;

    @Override
    public boolean isEnabled() {
        // Only enable if search indexing is enabled
        return config.isEnabled();
    }

    @Override
    public int order() {
        // Execute after all other decorators
        return RegistryStorageDecoratorOrderConstants.SEARCH_INDEX_EVENT_DECORATOR;
    }

    @Override
    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean versionIsDraft, boolean dryRun, String owner)
            throws RegistryStorageException {

        // Call delegate to perform the actual creation
        Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> result = super.createArtifact(groupId,
                artifactId, artifactType, artifactMetaData, version, versionContent, versionMetaData,
                versionBranches, versionIsDraft, dryRun, owner);

        // Fire event for search indexing (if not a dry run)
        if (!dryRun) {
            ArtifactVersionMetaDataDto versionMeta = result.getRight();
            versionCreatedEvent.fire(new VersionCreatedEvent(groupId, artifactId,
                    versionMeta.getVersion(), versionMeta.getGlobalId(), versionMeta.getContentId()));
        }

        return result;
    }

    @Override
    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId,
            String version, String artifactType, ContentWrapperDto content,
            EditableVersionMetaDataDto metaData, List<String> branches, boolean isDraft,
            boolean dryRun, String owner) throws RegistryStorageException {

        // Call delegate to perform the actual creation
        ArtifactVersionMetaDataDto versionMeta = super.createArtifactVersion(groupId, artifactId,
                version, artifactType, content, metaData, branches, isDraft, dryRun, owner);

        // Fire event for search indexing (if not a dry run)
        if (!dryRun) {
            versionCreatedEvent.fire(new VersionCreatedEvent(groupId, artifactId,
                    versionMeta.getVersion(), versionMeta.getGlobalId(), versionMeta.getContentId()));
        }

        return versionMeta;
    }

    @Override
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws RegistryStorageException {

        // Get version metadata BEFORE deletion (we need globalId)
        ArtifactVersionMetaDataDto versionMeta = delegate.getArtifactVersionMetaData(groupId,
                artifactId, version);
        long globalId = versionMeta.getGlobalId();

        // Call delegate to perform the actual deletion
        super.deleteArtifactVersion(groupId, artifactId, version);

        // Fire event for search index update
        versionDeletedEvent.fire(new VersionDeletedEvent(groupId, artifactId, version, globalId));
    }

    @Override
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws RegistryStorageException {

        // Call delegate to perform the actual update
        super.updateArtifactMetaData(groupId, artifactId, metaData);

        // Fire event for search index update (affects all versions of this artifact)
        artifactMetadataUpdatedEvent
                .fire(new ArtifactMetadataUpdatedEvent(groupId, artifactId));
    }

    @Override
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto metaData) throws RegistryStorageException {

        // Call delegate to perform the actual update
        super.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);

        // Version metadata updated - fire event to re-index this version
        ArtifactVersionMetaDataDto versionMeta = delegate.getArtifactVersionMetaData(groupId,
                artifactId, version);
        versionCreatedEvent.fire(new VersionCreatedEvent(groupId, artifactId, version,
                versionMeta.getGlobalId(), versionMeta.getContentId()));
    }

    @Override
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            VersionState newState, boolean dryRun) {

        // Get old state BEFORE update
        ArtifactVersionMetaDataDto versionMeta = delegate.getArtifactVersionMetaData(groupId,
                artifactId, version);
        VersionState oldState = versionMeta.getState();
        long globalId = versionMeta.getGlobalId();

        // Call delegate to perform the actual update
        super.updateArtifactVersionState(groupId, artifactId, version, newState, dryRun);

        // Fire event for search index update
        versionStateChangedEvent.fire(
                new VersionStateChangedEvent(groupId, artifactId, version, globalId, oldState, newState));
    }
}
