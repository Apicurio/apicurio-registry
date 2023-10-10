package io.apicurio.registry.storage.impl.kafkasql.sql;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.CommentDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.IdGenerator;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.SqlUtil;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.CommentEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;

import java.util.Date;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

import static io.apicurio.registry.storage.impl.sql.SqlUtil.normalizeGroupId;

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

    @Inject
    Logger log;

    @Inject
    HandleFactory handles;

    public KafkaSqlStore() {
        super(false);
    }

    void onStart(@Observes StartupEvent ev) {
        // Do nothing, just force initialization of the bean.
        // Otherwise, there are some corner cases where KafkaSqlRegistryStorage does not become ready,
        // because it never receives the io.apicurio.registry.storage.impl.sql.SqlStorageEvent.
        // This can be reproduced by removing this method and running
        // io.apicurio.tests.dbupgrade.KafkaSqlLogCompactionIT.testLogCompaction test.
    }

    @Transactional
    public long nextGlobalId() {
        return handles.withHandleNoException(this::nextGlobalId);
    }

    @Transactional
    public long nextContentId() {
        return handles.withHandleNoException(this::nextContentId);
    }

    @Transactional
    public long nextCommentId() {
        return handles.withHandleNoException(this::nextCommentId);
    }

    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectContentCountByHash();
            return handle.createQuery(sql)
                    .bind(0, contentHash)
                    .bind(1, tenantContext().tenantId())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }
    
    public boolean isArtifactRuleExists(String groupId, String artifactId, RuleType rule) throws RegistryStorageException {
        return handles.withHandleNoException( handle -> {
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
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectGlobalRuleCountByType();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, rule.name())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }


    public boolean isRoleMappingExists(String principalId) {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectRoleMappingCountByPrincipal();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, principalId)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    @Transactional
    public void storeContent(long contentId, String contentHash, String canonicalHash, ContentHandle content, String serializedReferences) throws RegistryStorageException {
        handles.withHandleNoException( handle -> {
            if (!isContentExists(contentId)) {
                byte [] contentBytes = content.bytes();
                String sql = sqlStatements().importContent();
                handle.createUpdate(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, contentId)
                    .bind(2, canonicalHash)
                    .bind(3, contentHash)
                    .bind(4, contentBytes)
                    .bind(5, serializedReferences)
                    .execute();

                insertReferences(handle, contentId, SqlUtil.deserializeReferences(serializedReferences));
            }
            return null;
        });
    }

    @Transactional
    public ArtifactMetaDataDto createArtifactWithMetadata(String groupId, String artifactId, String version,
            String artifactType, String contentHash, String createdBy,
            Date createdOn, EditableArtifactMetaDataDto metaData, IdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        long contentId = this.contentIdFromHash(contentHash);

        if (metaData == null) {
            metaData = new EditableArtifactMetaDataDto();
        }

        return super.createArtifactWithMetadata(groupId, artifactId, version, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }

    @Transactional
    public ArtifactMetaDataDto updateArtifactWithMetadata(String groupId, String artifactId, String version,
                                                          String artifactType, String contentHash, String createdBy, Date createdOn,
                                                          EditableArtifactMetaDataDto metaData,
                                                          IdGenerator globalIdGenerator)
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

    public long contentIdFromHash(String contentHash) {
        return handles.withHandleNoException( handle -> {
            String sql = sqlStatements().selectContentIdByHash();
            return handle.createQuery(sql)
                    .bind(0, contentHash)
                    .bind(1, tenantContext().tenantId())
                    .mapTo(Long.class)
                    .one();
        });
    }

    @Transactional
    public void importArtifactRule(ArtifactRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            super.importArtifactRule(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importArtifactVersion(ArtifactVersionEntity entity) {
        handles.withHandleNoException(handle -> {
            super.importArtifactVersion(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importContent(ContentEntity entity) {
        handles.withHandleNoException(handle -> {
            super.importContent(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importGlobalRule(GlobalRuleEntity entity) {
        handles.withHandleNoException(handle -> {
            super.importGlobalRule(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importGroup(GroupEntity entity) {
        handles.withHandleNoException(handle -> {
            super.importGroup(handle, entity);
            return null;
        });
    }

    @Transactional
    public void importComment(CommentEntity entity) {
        handles.withHandleNoException(handle -> {
            super.importComment(handle, entity);
            return null;
        });
    }

    @Transactional
    public void resetContentId() {
        handles.withHandleNoException(handle -> {
            super.resetContentId(handle);
            return null;
        });
    }

    @Transactional
    public void resetCommentId() {
        handles.withHandleNoException(handle -> {
            super.resetCommentId(handle);
            return null;
        });
    }

    @Transactional
    public void resetGlobalId() {
        handles.withHandleNoException(handle -> {
            super.resetGlobalId(handle);
            return null;
        });
    }

    @Transactional
    public void updateContentCanonicalHash(String newCanonicalHash, long contentId, String contentHash) {
        handles.withHandleNoException(handle -> {
           String sql = sqlStatements().updateContentCanonicalHash();
           int rowCount = handle.createUpdate(sql)
                 .bind(0, newCanonicalHash)
                 .bind(1, tenantContext().tenantId())
                 .bind(2, contentId)
                 .bind(3, contentHash)
                 .execute();
           if (rowCount == 0) {
               log.warn("update content canonicalHash, no row match contentId {} contentHash {}", contentId, contentHash);
           }
           return null;
        });
    }
    
    @Transactional
    public String resolveVersion(String groupId, String artifactId, String version) {
        return super.resolveVersion(groupId, artifactId, version);
    }

    /**
     * @see io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage#createArtifactVersionComment(java.lang.String, java.lang.String, java.lang.String, io.apicurio.registry.storage.impl.sql.IdGenerator, java.lang.String, java.util.Date, java.lang.String)
     */
    @Transactional
    public CommentDto createArtifactVersionComment(String groupId, String artifactId, String version, IdGenerator commentId,
            String createdBy, Date createdOn, String value) {
        return super.createArtifactVersionComment(groupId, artifactId, version, commentId, createdBy, createdOn, value);
    }

}
