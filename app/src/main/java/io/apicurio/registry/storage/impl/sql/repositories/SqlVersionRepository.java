package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.model.BranchId;
import io.apicurio.registry.model.GAV;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.StoredArtifactVersionDto;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.RegistryContentUtils;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import io.apicurio.registry.storage.impl.sql.SqlStatements;
import io.apicurio.registry.storage.impl.sql.jdb.Handle;
import io.apicurio.registry.storage.impl.sql.mappers.ArtifactVersionMetaDataDtoMapper;
import io.apicurio.registry.storage.impl.sql.mappers.GAVMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StoredArtifactMapper;
import io.apicurio.registry.storage.impl.sql.mappers.StringMapper;
import io.apicurio.registry.storage.impl.sql.mappers.VersionStateMapper;
import io.apicurio.registry.events.ArtifactVersionDeleted;
import io.apicurio.registry.events.ArtifactVersionMetadataUpdated;
import io.apicurio.registry.events.ArtifactVersionStateChanged;
import io.apicurio.registry.types.VersionState;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.apicurio.registry.storage.impl.sql.RegistryContentUtils.normalizeGroupId;
import static io.apicurio.registry.utils.StringUtil.limitStr;
import static io.apicurio.registry.utils.StringUtil.asLowerCase;

/**
 * Repository handling artifact version operations in the SQL storage layer.
 * Extracted from AbstractSqlRegistryStorage to improve maintainability.
 */
@ApplicationScoped
public class SqlVersionRepository {

    public static final int MAX_VERSION_NAME_LENGTH = 512;
    public static final int MAX_VERSION_DESCRIPTION_LENGTH = 1024;
    public static final int MAX_LABEL_KEY_LENGTH = 256;
    public static final int MAX_LABEL_VALUE_LENGTH = 512;

    @Inject
    Logger log;

    @Inject
    SqlStatements sqlStatements;

    @Inject
    HandleFactory handles;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    Event<SqlOutboxEvent> outboxEvent;

    @Inject
    SqlBranchRepository branchRepository;

    @Inject
    SqlArtifactRepository artifactRepository;

    /**
     * Get artifact version metadata by globalId.
     */
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(Long globalId)
            throws VersionNotFoundException, RegistryStorageException {
        return handles.withHandle(handle -> {
            Optional<ArtifactVersionMetaDataDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionMetaDataByGlobalId()).bind(0, globalId)
                    .map(ArtifactVersionMetaDataDtoMapper.instance).findOne();
            return res.orElseThrow(() -> new VersionNotFoundException(globalId));
        });
    }

    /**
     * Get artifact version metadata by GAV.
     */
    public ArtifactVersionMetaDataDto getArtifactVersionMetaData(String groupId, String artifactId,
            String version) {
        return handles.withHandle(handle -> getArtifactVersionMetaDataRaw(handle, groupId, artifactId, version));
    }

    /**
     * Get artifact version metadata using an existing handle.
     */
    public ArtifactVersionMetaDataDto getArtifactVersionMetaDataRaw(Handle handle, String groupId,
            String artifactId, String version) {
        Optional<ArtifactVersionMetaDataDto> res = handle
                .createQuery(sqlStatements.selectArtifactVersionMetaData()).bind(0, normalizeGroupId(groupId))
                .bind(1, artifactId).bind(2, version).map(ArtifactVersionMetaDataDtoMapper.instance)
                .findOne();
        return res.orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));
    }

    /**
     * Get artifact version content by globalId.
     */
    public StoredArtifactVersionDto getArtifactVersionContent(long globalId)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by globalId: {}", globalId);
        return handles.withHandle(handle -> {
            Optional<StoredArtifactVersionDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionContentByGlobalId()).bind(0, globalId)
                    .map(StoredArtifactMapper.instance).findOne();
            return res.orElseThrow(() -> new ArtifactNotFoundException(null, "gid-" + globalId));
        });
    }

    /**
     * Get artifact version content by GAV.
     */
    public StoredArtifactVersionDto getArtifactVersionContent(String groupId, String artifactId,
            String version) throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Selecting a single artifact version by artifactId: {} {} and version {}", groupId,
                artifactId, version);
        return handles.withHandle(handle -> {
            Optional<StoredArtifactVersionDto> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionContent())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(StoredArtifactMapper.instance).findOne();
            return res.orElseThrow(() -> new ArtifactNotFoundException(groupId, artifactId));
        });
    }

    /**
     * Delete an artifact version.
     */
    public void deleteArtifactVersion(String groupId, String artifactId, String version)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Deleting version {} of artifact {} {}", version, groupId, artifactId);

        handles.withHandle(handle -> {
            int rows = handle.createUpdate(sqlStatements.deleteVersion()).bind(0, normalizeGroupId(groupId))
                    .bind(1, artifactId).bind(2, version).execute();

            if (rows == 0) {
                throw new VersionNotFoundException(groupId, artifactId, version);
            }

            outboxEvent.fire(SqlOutboxEvent.of(ArtifactVersionDeleted.of(groupId, artifactId, version)));

            return null;
        });
    }

    /**
     * Update artifact version metadata.
     */
    public void updateArtifactVersionMetaData(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto editableMetadata)
            throws ArtifactNotFoundException, VersionNotFoundException, RegistryStorageException {
        log.debug("Updating meta-data for an artifact version: {} {}", groupId, artifactId);

        var metadata = getArtifactVersionMetaData(groupId, artifactId, version);
        long globalId = metadata.getGlobalId();

        handles.withHandle(handle -> {
            boolean modified = false;

            if (editableMetadata.getName() != null) {
                modified = true;
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionNameByGAV())
                        .bind(0, limitStr(editableMetadata.getName(), MAX_VERSION_NAME_LENGTH))
                        .bind(1, normalizeGroupId(groupId))
                        .bind(2, artifactId).bind(3, version).execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
            }

            if (editableMetadata.getDescription() != null) {
                modified = true;
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionDescriptionByGAV())
                        .bind(0, limitStr(editableMetadata.getDescription(), MAX_VERSION_DESCRIPTION_LENGTH))
                        .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, version).execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }
            }

            Map<String, String> labels = editableMetadata.getLabels();
            if (labels != null) {
                modified = true;
                int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionLabelsByGAV())
                        .bind(0, RegistryContentUtils.serializeLabels(labels))
                        .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, version).execute();
                if (rowCount == 0) {
                    throw new VersionNotFoundException(groupId, artifactId, version);
                }

                // Delete old labels
                handle.createUpdate(sqlStatements.deleteVersionLabelsByGlobalId()).bind(0, globalId).execute();

                // Insert new labels
                labels.forEach((k, v) -> {
                    handle.createUpdate(sqlStatements.insertVersionLabel())
                            .bind(0, globalId)
                            .bind(1, limitStr(k.toLowerCase(), MAX_LABEL_KEY_LENGTH))
                            .bind(2, limitStr(asLowerCase(v), MAX_LABEL_VALUE_LENGTH)).execute();
                });

                if (modified) {
                    String modifiedBy = securityIdentity.getPrincipal().getName();
                    Date modifiedOn = new Date();

                    rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionModifiedByOn())
                            .bind(0, modifiedBy).bind(1, modifiedOn).bind(2, normalizeGroupId(groupId))
                            .bind(3, artifactId).bind(4, version).execute();
                    if (rowCount == 0) {
                        throw new VersionNotFoundException(groupId, artifactId, version);
                    }
                }
            }

            outboxEvent.fire(SqlOutboxEvent
                    .of(ArtifactVersionMetadataUpdated.of(groupId, artifactId, version, editableMetadata)));

            return null;
        });
    }

    /**
     * Get artifact version state.
     */
    public VersionState getArtifactVersionState(String groupId, String artifactId, String version) {
        return handles.withHandle(handle -> {
            Optional<VersionState> res = handle.createQuery(sqlStatements.selectArtifactVersionState())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(VersionStateMapper.instance).findOne();
            return res.orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));
        });
    }

    /**
     * Update artifact version state.
     */
    public void updateArtifactVersionState(String groupId, String artifactId, String version,
            VersionState newState, boolean dryRun) {
        handles.withHandle(handle -> {
            if (dryRun) {
                handle.setRollback(true);
            }

            Optional<VersionState> res = handle
                    .createQuery(sqlStatements.selectArtifactVersionStateForUpdate())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                    .map(VersionStateMapper.instance).findOne();
            VersionState currentState = res
                    .orElseThrow(() -> new VersionNotFoundException(groupId, artifactId, version));

            handle.createUpdate(sqlStatements.updateArtifactVersionStateByGAV()).bind(0, newState.name())
                    .bind(1, normalizeGroupId(groupId)).bind(2, artifactId).bind(3, version).execute();

            String modifiedBy = securityIdentity.getPrincipal().getName();
            Date modifiedOn = new Date();
            handle.createUpdate(sqlStatements.updateArtifactVersionModifiedByOn()).bind(0, modifiedBy)
                    .bind(1, modifiedOn).bind(2, normalizeGroupId(groupId)).bind(3, artifactId)
                    .bind(4, version).execute();

            // Handle branch updates on state transition from DRAFT
            if (currentState == VersionState.DRAFT) {
                GAV gav = new GAV(groupId, artifactId, version);
                branchRepository.createOrUpdateBranchRaw(handle, gav, BranchId.LATEST, true);
                branchRepository.createOrUpdateSemverBranchesRaw(handle, gav);
                branchRepository.removeVersionFromBranchRaw(handle, gav, BranchId.DRAFTS);
            }

            outboxEvent.fire(SqlOutboxEvent.of(
                    ArtifactVersionStateChanged.of(groupId, artifactId, version, currentState, newState)));

            return null;
        });
    }

    /**
     * Get list of artifact versions.
     */
    public List<String> getArtifactVersions(String groupId, String artifactId, Set<VersionState> filterBy)
            throws ArtifactNotFoundException, RegistryStorageException {
        log.debug("Getting a list of versions for artifact: {} {}", groupId, artifactId);

        return handles.withHandle(handle -> {
            String sql = sqlStatements.selectArtifactVersions();
            if (filterBy != null && !filterBy.isEmpty()) {
                sql = sqlStatements.selectArtifactVersionsFilteredByState();
                String jclause = filterBy.stream().map(vs -> "'" + vs.name() + "'")
                        .collect(Collectors.joining(",", "(", ")"));
                sql = sql.replace("(?)", jclause);
            }
            return getArtifactVersionsRaw(handle, groupId, artifactId, sql);
        });
    }

    private List<String> getArtifactVersionsRaw(Handle handle, String groupId, String artifactId,
            String sqlStatement) throws ArtifactNotFoundException, RegistryStorageException {
        List<String> versions = handle.createQuery(sqlStatement).bind(0, normalizeGroupId(groupId))
                .bind(1, artifactId).map(StringMapper.instance).list();

        if (versions.isEmpty()) {
            throw new ArtifactNotFoundException(groupId, artifactId);
        }
        return versions;
    }

    /**
     * Count active artifact versions.
     */
    public long countActiveArtifactVersions(String groupId, String artifactId)
            throws RegistryStorageException {
        log.debug("Searching for versions of artifact {} {}", groupId, artifactId);
        return handles.withHandleNoException(handle -> {
            Integer count = handle.createQuery(sqlStatements.selectActiveArtifactVersionsCount())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(Integer.class).one();
            return count.longValue();
        });
    }

    /**
     * Count all artifact versions.
     */
    public long countArtifactVersions(String groupId, String artifactId) throws RegistryStorageException {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectAllArtifactVersionsCount())
                    .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).mapTo(Long.class).one();
        });
    }

    /**
     * Count total artifact versions.
     */
    public long countTotalArtifactVersions() throws RegistryStorageException {
        return handles.withHandle(handle -> {
            return handle.createQuery(sqlStatements.selectTotalArtifactVersionsCount()).mapTo(Long.class).one();
        });
    }

    /**
     * Check if artifact version exists.
     */
    public boolean isArtifactVersionExists(String groupId, String artifactId, String version)
            throws RegistryStorageException {
        try {
            getArtifactVersionMetaData(groupId, artifactId, version);
            return true;
        } catch (VersionNotFoundException ignored) {
            return false;
        }
    }

    /**
     * Get GAV by globalId using an existing handle.
     */
    public GAV getGAVByGlobalIdRaw(Handle handle, long globalId) {
        return handle.createQuery(sqlStatements.selectGAVByGlobalId()).bind(0, globalId)
                .map(GAVMapper.instance).findOne().orElseThrow(() -> new VersionNotFoundException(globalId));
    }

    /**
     * Create artifact version using an existing handle.
     * This is the core method for version creation, used by AbstractSqlRegistryStorage.
     */
    public ArtifactVersionMetaDataDto createArtifactVersionRaw(Handle handle, boolean firstVersion,
            String groupId, String artifactId, String version, EditableVersionMetaDataDto metaData,
            String owner, Date createdOn, Long contentId, List<String> branches, boolean isDraft,
            SqlBranchRepository branchRepo) {

        if (metaData == null) {
            metaData = EditableVersionMetaDataDto.builder().build();
        }

        VersionState state = isDraft ? VersionState.DRAFT : VersionState.ENABLED;
        String labelsStr = RegistryContentUtils.serializeLabels(metaData.getLabels());

        // This would typically get the next globalId from the parent class
        // For now, we'll leave this to be coordinated by AbstractSqlRegistryStorage
        throw new UnsupportedOperationException(
                "createArtifactVersionRaw requires globalId generation from parent storage class");
    }

    // ==================== IMPORT OPERATIONS ====================

    /**
     * Check if a globalId exists using an existing handle.
     */
    public boolean isGlobalIdExistsRaw(Handle handle, long globalId) {
        return handle.createQuery(sqlStatements.selectGlobalIdExists())
                .bind(0, globalId)
                .mapTo(Integer.class)
                .one() > 0;
    }

    /**
     * Import an artifact version entity (used for data import/migration).
     */
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        handles.withHandleNoException(handle -> {
            if (!artifactRepository.isArtifactExistsRaw(handle, entity.groupId, entity.artifactId)) {
                throw new ArtifactNotFoundException(entity.groupId, entity.artifactId);
            }
            if (isGlobalIdExistsRaw(handle, entity.globalId)) {
                throw new VersionAlreadyExistsException(entity.globalId);
            }

            handle.createUpdate(sqlStatements.importArtifactVersion())
                    .bind(0, entity.globalId)
                    .bind(1, normalizeGroupId(entity.groupId))
                    .bind(2, entity.artifactId)
                    .bind(3, entity.version)
                    .bind(4, entity.versionOrder)
                    .bind(5, entity.state)
                    .bind(6, entity.name)
                    .bind(7, entity.description)
                    .bind(8, entity.owner)
                    .bind(9, new Date(entity.createdOn))
                    .bind(10, entity.modifiedBy)
                    .bind(11, new Date(entity.modifiedOn))
                    .bind(12, RegistryContentUtils.serializeLabels(entity.labels))
                    .bind(13, entity.contentId)
                    .execute();

            // Insert labels into the "version_labels" table
            if (entity.labels != null && !entity.labels.isEmpty()) {
                entity.labels.forEach((k, v) -> {
                    handle.createUpdate(sqlStatements.insertVersionLabel())
                            .bind(0, entity.globalId)
                            .bind(1, k.toLowerCase())
                            .bind(2, v == null ? null : v.toLowerCase())
                            .execute();
                });
            }

            return null;
        });
    }

    // ==================== ADDITIONAL VERSION OPERATIONS ====================

    /**
     * Get artifact versions by content ID.
     */
    public List<ArtifactVersionMetaDataDto> getArtifactVersionsByContentId(long contentId) {
        return handles.withHandleNoException(handle -> {
            List<ArtifactVersionMetaDataDto> dtos = handle
                    .createQuery(sqlStatements.selectArtifactVersionMetaDataByContentId())
                    .bind(0, contentId).map(ArtifactVersionMetaDataDtoMapper.instance).list();
            return dtos;
        });
    }

    /**
     * Update artifact version content (for draft versions).
     */
    public void updateArtifactVersionContent(String groupId, String artifactId, String version,
            long contentId) {
        log.debug("Updating content for artifact version: {} {} @ {}", groupId, artifactId, version);

        String modifiedBy = securityIdentity.getPrincipal().getName();
        Date modifiedOn = new Date();

        handles.withHandle(handle -> {
            int rowCount = handle.createUpdate(sqlStatements.updateArtifactVersionContent())
                    .bind(0, contentId).bind(1, modifiedBy).bind(2, modifiedOn)
                    .bind(3, normalizeGroupId(groupId)).bind(4, artifactId).bind(5, version).execute();
            if (rowCount == 0) {
                throw new VersionNotFoundException(groupId, artifactId, version);
            }
            return null;
        });
    }

    /**
     * Check if artifact version exists.
     */
    public boolean isArtifactVersionExistsRaw(Handle handle, String groupId, String artifactId,
            String version) {
        return handle.createQuery(sqlStatements.selectArtifactVersionCountByGAV())
                .bind(0, normalizeGroupId(groupId)).bind(1, artifactId).bind(2, version)
                .mapTo(Integer.class).one() > 0;
    }
}
