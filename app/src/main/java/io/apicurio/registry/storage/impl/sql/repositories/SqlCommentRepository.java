package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.CommentNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.CommentDtoMapper;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;

/**
 * Repository handling comment operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlCommentRepository {

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    SqlVersionRepository versionRepository;

    @Inject
    SqlSequenceRepository sequenceRepository;

    /**
     * Create a new comment on an artifact version.
     */
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version,
            String value) {
        log.debug("Inserting an artifact comment row for artifact: {} {} version: {}", groupId, artifactId,
                version);

        String owner = securityIdentity.getPrincipal().getName();
        Date createdOn = new Date();

        try {
            var metadata = versionRepository.getArtifactVersionMetaData(groupId, artifactId, version);
            long commentId = sequenceRepository.nextCommentId();

            var entity = CommentEntity.builder()
                    .commentId(String.valueOf(commentId))
                    .globalId(metadata.getGlobalId())
                    .owner(owner)
                    .createdOn(createdOn.getTime())
                    .value(value)
                    .build();

            importCommentRaw(entity);

            log.debug("Comment row successfully inserted.");

            return CommentDto.builder()
                    .commentId(entity.commentId)
                    .owner(owner)
                    .createdOn(createdOn.getTime())
                    .value(value)
                    .build();
        } catch (VersionNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            if (sqlStatements.isForeignKeyViolation(ex)) {
                throw new ArtifactNotFoundException(groupId, artifactId, ex);
            }
            throw ex;
        }
    }

    /**
     * Get all comments for an artifact version.
     */
    public List<CommentDto> getArtifactVersionComments(String groupId, String artifactId, String version) {
        log.debug("Getting a list of all artifact version comments for: {} {} @ {}", groupId, artifactId,
                version);

        try {
            return handles.withHandle(handle -> {
                return handle.createQuery(sqlStatements.selectVersionComments())
                        .bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId)
                        .bind(2, version)
                        .map(CommentDtoMapper.instance)
                        .list();
            });
        } catch (ArtifactNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RegistryStorageException(ex);
        }
    }

    /**
     * Delete a comment from an artifact version.
     */
    public void deleteArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId) {
        log.debug("Deleting a version comment for artifact: {} {} @ {}", groupId, artifactId, version);
        String deletedBy = securityIdentity.getPrincipal().getName();

        handles.withHandle(handle -> {
            Optional<ArtifactVersionMetaDataDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionMetaData())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, version)
                    .map(ArtifactVersionMetaDataDtoMapper.instance)
                    .findOne();
            ArtifactVersionMetaDataDto avmdd = res
                    .orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

            int rowCount = handle.createUpdate(sqlStatements.deleteVersionComment())
                    .bind(0, avmdd.getGlobalId())
                    .bind(1, commentId)
                    .bind(2, deletedBy)
                    .execute();
            if (rowCount == 0) {
                throw new CommentNotFoundException(commentId);
            }
            return null;
        });
    }

    /**
     * Update a comment on an artifact version.
     */
    public void updateArtifactVersionComment(String groupId, String artifactId, String version,
            String commentId, String value) {
        log.debug("Updating a comment for artifact: {} {} @ {}", groupId, artifactId, version);
        String modifiedBy = securityIdentity.getPrincipal().getName();

        handles.withHandle(handle -> {
            Optional<ArtifactVersionMetaDataDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionMetaData())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, version)
                    .map(ArtifactVersionMetaDataDtoMapper.instance)
                    .findOne();
            ArtifactVersionMetaDataDto avmdd = res
                    .orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

            int rowCount = handle.createUpdate(sqlStatements.updateVersionComment())
                    .bind(0, value)
                    .bind(1, avmdd.getGlobalId())
                    .bind(2, commentId)
                    .bind(3, modifiedBy)
                    .execute();
            if (rowCount == 0) {
                throw new CommentNotFoundException(commentId);
            }
            return null;
        });
    }

    /**
     * Import a comment entity (used for data import/migration).
     */
    public void importCommentRaw(CommentEntity entity) {
        handles.withHandle(handle -> {
            handle.createUpdate(sqlStatements.insertVersionComment())
                    .bind(0, entity.commentId)
                    .bind(1, entity.globalId)
                    .bind(2, entity.owner)
                    .bind(3, new Date(entity.createdOn))
                    .bind(4, entity.value)
                    .execute();
            return null;
        });
    }
}
