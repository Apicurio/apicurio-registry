package io.apicurio.registry.storage.impl.kafkasql.sql;

import static io.apicurio.registry.storage.impl.sql.SqlUtil.normalizeGroupId;

import java.util.Date;
import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.transaction.Transactional;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.GlobalIdGenerator;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;

/**
 * The SQL store used by the KSQL registry artifactStore implementation.  This is ultimately where each
 * application replica stores its data after consuming it from the Kafka topic.  Often this is a
 * H2 in-memory database, but it could be something else (e.g. a local postgresql sidecar DB).
 * This class extends the core SQL registry artifactStore to leverage as much of the existing SQL
 * support as possible.  However, this class extends the SQL support to include some functionality
 * only needed by the KSQL artifactStore.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Named("KafkaSqlStore")
@Logged
public class KafkaSqlStore extends AbstractSqlRegistryStorage {

    @Transactional
    public long nextGlobalId() {
        return withHandle( handle -> {
            return nextGlobalId(handle);
        });
    }

    @Transactional
    public long nextContentId() {
        return withHandle( handle -> {
            return nextContentId(handle);
        });
    }

    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectContentCountByHash();
            return handle.createQuery(sql)
                    .bind(0, contentHash)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectArtifactRuleCountByType();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, normalizeGroupId(groupId))
                    .bind(2, artifactId)
                    .bind(3, rule.name())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    public boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectGlobalRuleCountByType();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, rule.name())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    @Transactional
    public void storeContent(long contentId, String contentHash, String canonicalHash, ContentHandle content) throws RegistryStorageException {
        withHandle( handle -> {
            if (!isContentExists(contentId)) {
                byte [] contentBytes = content.bytes();
                String sql = sqlStatements().importContent();
                handle.createUpdate(sql)
                    .bind(0, contentId)
                    .bind(1, canonicalHash)
                    .bind(2, contentHash)
                    .bind(3, contentBytes)
                    .execute();
            }
            return null;
        });
    }

    @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, String contentHash, String createdBy,
            Date createdOn, EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        long contentId = this.contentIdFromHash(contentHash);

        if (metaData == null) {
            metaData = new EditableArtifactMetaDataDto();
        }

        return super.createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }

    @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String groupId, String artifactId, String version,
            ArtifactType artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        long contentId = this.contentIdFromHash(contentHash);

        if (metaData == null) {
            metaData = new EditableArtifactMetaDataDto();
        }

        return super.updateArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }

    @Transactional
    public void updateArtifactVersionMetaDataAndState(String groupId, String artifactId, String version,
            EditableArtifactMetaDataDto metaData, ArtifactState state) {
        this.updateArtifactVersionMetaData(groupId, artifactId, version, metaData);
        this.updateArtifactState(groupId, artifactId, version, state);
    }

    private long contentIdFromHash(String contentHash) {
        return withHandle( handle -> {
            String sql = sqlStatements().selectContentIdByHash();
            return handle.createQuery(sql)
                    .bind(0, contentHash)
                    .mapTo(Long.class)
                    .one();
        });
    }

    @Transactional
    public void importArtifactRule(ArtifactRuleEntity entity) {
        withHandle(handle -> {
            super.importArtifactRule(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        withHandle(handle -> {
            super.importArtifactVersion(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importContent(ContentEntity entity) {
        withHandle(handle -> {
            super.importContent(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importGlobalRule(GlobalRuleEntity entity) {
        withHandle(handle -> {
            super.importGlobalRule(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importGroup(GroupEntity entity) {
        withHandle(handle -> {
            super.importGroup(handle, entity);
            return null;
        });
    }

    @Transactional
    public void resetContentId() {
        withHandle(handle -> {
            super.resetContentId(handle);
            return null;
        });
    }

    @Transactional
    public void resetGlobalId() {
        withHandle(handle -> {
            super.resetGlobalId(handle);
            return null;
        });
    }

}
