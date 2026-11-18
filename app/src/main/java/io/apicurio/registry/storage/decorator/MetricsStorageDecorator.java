package io.apicurio.registry.storage.decorator;

import io.apicurio.registry.metrics.OTelMetricsProvider;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.CommitFailedException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Records the artifact and version counters for every write that reaches storage, whichever API it
 * came through (v3, v2, the Confluent-compatible API, MCP), rather than in one REST implementation.
 * With KafkaSQL, only the replica that receives a write runs the decorators, so each write is
 * counted once.
 */
@ApplicationScoped
public class MetricsStorageDecorator extends RegistryStorageDecoratorBase implements RegistryStorageDecorator {

    @Inject
    OTelMetricsProvider metrics;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public int order() {
        return RegistryStorageDecoratorOrderConstants.METRICS_DECORATOR;
    }

    public Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> createArtifact(String groupId,
            String artifactId, String artifactType, EditableArtifactMetaDataDto artifactMetaData,
            String version, ContentWrapperDto versionContent, EditableVersionMetaDataDto versionMetaData,
            List<String> versionBranches, boolean versionIsDraft, boolean dryRun, String owner)
            throws RegistryStorageException {
        Pair<ArtifactMetaDataDto, ArtifactVersionMetaDataDto> result = delegate.createArtifact(groupId,
                artifactId, artifactType, artifactMetaData, version, versionContent, versionMetaData,
                versionBranches, versionIsDraft, dryRun, owner);
        if (!dryRun) {
            String type = result.getLeft().getArtifactType();
            metrics.recordArtifactCreated(groupId, type);
            if (result.getRight() != null) {
                metrics.recordVersionCreated(groupId, type);
            }
        }
        return result;
    }

    public ArtifactVersionMetaDataDto createArtifactVersion(String groupId, String artifactId,
            String version, String artifactType, ContentWrapperDto content,
            EditableVersionMetaDataDto metaData, List<String> branches, boolean isDraft,
            boolean dryRun, String owner) throws RegistryStorageException {
        ArtifactVersionMetaDataDto result = delegate.createArtifactVersion(groupId, artifactId, version,
                artifactType, content, metaData, branches, isDraft, dryRun, owner);
        if (!dryRun) {
            metrics.recordVersionCreated(groupId, result.getArtifactType());
        }
        return result;
    }

    public ArtifactVersionMetaDataDto createArtifactVersionIfLatest(String groupId, String artifactId,
            String version, String artifactType, ContentWrapperDto content,
            EditableVersionMetaDataDto metaData, List<String> branches, boolean isDraft, String owner,
            int expectedBaseVersionOrder, EditableArtifactMetaDataDto artifactMetaData)
            throws ArtifactNotFoundException, VersionAlreadyExistsException, CommitFailedException,
            RegistryStorageException {
        ArtifactVersionMetaDataDto result = delegate.createArtifactVersionIfLatest(groupId, artifactId,
                version, artifactType, content, metaData, branches, isDraft, owner, expectedBaseVersionOrder,
                artifactMetaData);
        metrics.recordVersionCreated(groupId, result.getArtifactType());
        return result;
    }

    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        String type = delegate.getArtifactMetaData(groupId, artifactId).getArtifactType();
        List<String> result = delegate.deleteArtifact(groupId, artifactId);
        metrics.recordArtifactDeleted(groupId, type);
        return result;
    }
}
