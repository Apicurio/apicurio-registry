package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactMetaDataDtoMapper;
import io.apicurio.registry.events.ArtifactDeleted;
import io.apicurio.registry.events.ArtifactMetadataUpdated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;
import static io.apicurio.registry.utils.StringUtil.limitStr;
import static io.apicurio.registry.utils.StringUtil.asLowerCase;

/**
 * Repository handling artifact-level operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlArtifactRepository {

    // Maximum length constants for artifact metadata fields
    public static final int MAX_ARTIFACT_NAME_LENGTH = 512;
    public static final int MAX_ARTIFACT_DESCRIPTION_LENGTH = 1024;
    public static final int MAX_LABEL_KEY_LENGTH = 256;
    public static final int MAX_LABEL_VALUE_LENGTH = 512;

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    /**
     * Set the HandleFactory to use for database operations.
     * This allows storage implementations to override the default injected HandleFactory.
     */
    public void setHandleFactory(HandleFactory handleFactory) {
        this.handles = handleFactory;
    }

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    Event<SqlOutboxEvent> outboxEvent;

    @Inject
    SqlGroupRepository groupRepository;

    @Inject
    SqlContentRepository contentRepository;

    /**
     * Get artifact metadata by groupId and artifactId.
     */
    public ArtifactMetaDataDto getArtifactMetaData(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting artifact meta-data: {} {}", groupId, artifactId);
        return handles.withHandle(handle -> getArtifactMetaDataRaw(handle, groupId, artifactId));
    }

    /**
     * Get artifact metadata using an existing handle (for transactional operations).
     */
    public ArtifactMetaDataDto getArtifactMetaDataRaw(Handle handle, String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        Optional<ArtifactMetaDataDto> res = handle.createQuery(sqlStatements.selectArtifactMetaData())
                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                .map(ArtifactMetaDataDtoMapper.instance).findOne();
        return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
    }

    /**
     * Delete an artifact and all its versions.
     */
    public List<String> deleteArtifact(String groupId, String artifactId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Deleting an artifact: {} {}", groupId, artifactId);
        return handles.withHandle(handle -> {
            // Get the list of versions of the artifact (will be deleted)
            List<String> versions = handle.createQuery(sqlStatements.selectArtifactVersions())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(String.class).list();

            // Delete artifact rules
            handle.createUpdate(sqlStatements.deleteArtifactRules()).bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId).execute();

            // Delete artifact row
            int rowCount = handle.createUpdate(sqlStatements.deleteArtifact())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).execute();

            if (rowCount == 0) {
                throw new ArtifactNotFoundException(groupId, artifactId);
            }

            outboxEvent.fire(SqlOutboxEvent.of(ArtifactDeleted.of(groupId, artifactId)));

            return versions;
        });
    }

    /**
     * Delete all artifacts in a group.
     */
    public void deleteArtifacts(String groupId) throws RegistryStorageException {
        log.debug("Deleting all artifacts in group: {}", groupId);
        handles.withHandle(handle -> {
            // Delete artifact rules
            handle.createUpdate(sqlStatements.deleteArtifactRulesByGroupId())
                    .bind(0, normalizeGroupId(groupId)).execute();

            // Delete all artifacts
            int rowCount = handle.createUpdate(sqlStatements.deleteArtifactsByGroupId())
                    .bind(0, normalizeGroupId(groupId)).execute();

            if (rowCount == 0) {
                throw new ArtifactNotFoundException(groupId, null);
            }

            return null;
        });
    }

    /**
     * Update artifact metadata.
     */
    public void updateArtifactMetaData(String groupId, String artifactId,
            EditableArtifactMetaDataDto metaData) throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact: {} {}", groupId, artifactId);

        handles.withHandle(handle -> {
            boolean modified = false;

            // Update name
            if (metaData.getName() != null) {
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactName())
                        .bind(0, limitStr(metaData.getName(), MAX_ARTIFACT_NAME_LENGTH))
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId).execute();
                modified = true;
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }

            // Update description
            if (metaData.getDescription() != null) {
                modified = true;
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactDescription())
                        .bind(0, limitStr(metaData.getDescription(), MAX_ARTIFACT_DESCRIPTION_LENGTH))
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId).execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }

            // Update owner
            if (metaData.getOwner() != null && !metaData.getOwner().trim().isEmpty()) {
                modified = true;
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactOwner())
                        .bind(0, metaData.getOwner()).bind(1, normalizeGroupId(groupId)).bind(2, artifactId)
                        .execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }
            }

            // Update labels
            if (metaData.getLabels() != null) {
                modified = true;
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactLabels())
                        .bind(0, RegistryContentUtils.serializeLabels(metaData.getLabels()))
                        .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                }

                // Delete old labels
                handle.createUpdate(sqlStatements.deleteArtifactLabels()).bind(0, normalizeGroupId(groupId))
                        .bind(1, artifactId).execute();

                // Insert new labels
                Map<String, String> labels = metaData.getLabels();
                if (labels != null && !labels.isEmpty()) {
                    labels.forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertArtifactLabel())
                                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                                .bind(2, limitStr(k.toLowerCase(), MAX_LABEL_KEY_LENGTH))
                                .bind(3, limitStr(asLowerCase(v), MAX_LABEL_VALUE_LENGTH)).execute();
                    });
                }
            }

            if (modified) {
                String modifiedBy = securityIdentity.getPrincipal().getName();
                Date modifiedOn = new Date();

                int rowCount = handle.createUpdate(sqlStatements.updateArtifactModifiedByOn())
                        .bind(0, modifiedBy).bind(1, modifiedOn).bind(2, normalizeGroupId(groupId))
                        .bind(3, artifactId).execute();
                if (rowCount == 0) {
                    throw new ArtifactNotFoundException(groupId, artifactId);
                } else {
                    outboxEvent.fire(
                            SqlOutboxEvent.of(ArtifactMetadataUpdated.of(groupId, artifactId, metaData)));
                }
            }

            return null;
        });
    }

    /**
     * Check if an artifact exists.
     */
    public boolean isArtifactExists(String groupId, String artifactId) throws RegistryStorageException {
        return handles.<Boolean, RuntimeException>withHandleNoException(
                handle -> isArtifactExistsRaw(handle, groupId, artifactId));
    }

    /**
     * Check if an artifact exists using an existing handle.
     */
    public boolean isArtifactExistsRaw(Handle handle, String groupId, String artifactId)
            throws RegistryStorageException {
        return handle.createQuery(sqlStatements.selectArtifactCountById())
                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(Integer.class).one() > 0;
    }

    /**
     * Get all artifact IDs.
     */
    public Set<String> getArtifactIds(Integer limit) {
        final Integer adjustedLimit = limit == null ? Integer.MAX_VALUE : limit;
        log.debug("Getting the set of all artifact IDs");
        return handles.withHandleNoException(handle -> {
            return new HashSet<>(handle.createQuery(sqlStatements.selectArtifactIds())
                    .bind(0, adjustedLimit).mapTo(String.class).list());
        });
    }

    /**
     * Count all artifacts.
     */
    public long countArtifacts() throws RegistryStorageException {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllArtifactCount()).mapTo(Long.class).one();
        });
    }

    /**
     * Insert an artifact row in the database. Used during artifact creation.
     */
    public void insertArtifactRaw(Handle handle, String groupId, String artifactId, String artifactType,
            String owner, Date createdOn, EditableArtifactMetaDataDto amd) throws ArtifactAlreadyExistsException {
        try {
            Map<String, String> labels = amd.getLabels();
            String labelsStr = RegistryContentUtils.serializeLabels(labels);

            handle.createUpdate(sqlStatements.insertArtifact())
                    .bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId)
                    .bind(2, artifactType)
                    .bind(3, owner)
                    .bind(4, createdOn)
                    .bind(5, owner) // modifiedBy
                    .bind(6, createdOn) // modifiedOn
                    .bind(7, limitStr(amd.getName(), MAX_ARTIFACT_NAME_LENGTH))
                    .bind(8, limitStr(amd.getDescription(), MAX_ARTIFACT_DESCRIPTION_LENGTH, true))
                    .bind(9, labelsStr)
                    .execute();

            // Insert labels
            if (labels != null && !labels.isEmpty()) {
                labels.forEach((k, v) -> {
                    handle.createUpdate(sqlStatements.insertArtifactLabel())
                            .bind(0, normalizeGroupId(groupId)).bind(1, artifactId)
                            .bind(2, limitStr(k.toLowerCase(), MAX_LABEL_KEY_LENGTH))
                            .bind(3, limitStr(v.toLowerCase(), MAX_LABEL_VALUE_LENGTH)).execute();
                });
            }
        } catch (Exception ex) {
            if (sqlStatements.isPrimaryKeyViolation(ex)) {
                throw new ArtifactAlreadyExistsException(groupId, artifactId);
            }
            throw ex;
        }
    }

    // ==================== IMPORT OPERATIONS ====================

    /**
     * Import an artifact entity (used for data import/migration).
     */
    public void importArtifact(ArtifactEntity entity) {
        GroupId groupId = new GroupId(entity.groupId);
        if (!groupId.isDefaultGroup()) {
            String owner = securityIdentity.getPrincipal().getName();
            GroupMetaDataDto group = GroupMetaDataDto.builder()
                    .groupId(groupId.getRawGroupId())
                    .owner(owner)
                    .modifiedBy(owner)
                    .build();
            groupRepository.ensureGroup(group);
        }

        handles.withHandleNoException(handle -> {
            if (!isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
                String labelsStr = RegistryContentUtils.serializeLabels(entity.labels);
                handle.createUpdate(sqlStatements.insertArtifact())
                        .bind(0, normalizeGroupId(entity.groupId))
                        .bind(1, entity.artifactId)
                        .bind(2, entity.artifactType)
                        .bind(3, entity.owner)
                        .bind(4, new Date(entity.createdOn))
                        .bind(5, entity.modifiedBy)
                        .bind(6, new Date(entity.modifiedOn))
                        .bind(7, entity.name)
                        .bind(8, entity.description)
                        .bind(9, labelsStr)
                        .execute();

                // Insert labels into the "artifact_labels" table
                if (entity.labels != null && !entity.labels.isEmpty()) {
                    entity.labels.forEach((k, v) -> {
                        handle.createUpdate(sqlStatements.insertArtifactLabel())
                                .bind(0, normalizeGroupId(entity.groupId))
                                .bind(1, entity.artifactId)
                                .bind(2, k.toLowerCase())
                                .bind(3, v == null ? null : v.toLowerCase())
                                .execute();
                    });
                }
            } else {
                throw new ArtifactAlreadyExistsException(entity.groupId, entity.artifactId);
            }
            return null;
        });
    }
}
