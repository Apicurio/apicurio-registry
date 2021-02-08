package io.apicurio.registry.storage.impl.kafkasql.sql;

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

    public boolean isContentExists(String contentHash) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectContentCountByHash();
            return handle.createQuery(sql)
                    .bind(0, contentHash)
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    public boolean isArtifactRuleExists(String artifactId, RuleType rule) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectArtifactRuleCountByType();
            return handle.createQuery(sql)
                    .bind(0, tenantContext().tenantId())
                    .bind(1, artifactId)
                    .bind(2, rule.name())
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
    public void storeContent(String contentHash, ArtifactType artifactType, ContentHandle content) throws RegistryStorageException {
        withHandle( handle -> {
            super.createOrUpdateContent(handle, artifactType, content);
            return null;
        });
    }
    
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, String contentHash, String createdBy,
            Date createdOn, EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        long contentId = this.contentIdFromHash(contentHash);
        
        if (metaData == null) {
            metaData = new EditableArtifactMetaDataDto();
        }
        
        return super.createArtifactWithMetadata(artifactId, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }
    
    @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, String contentHash, String createdBy, Date createdOn,
            EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        long contentId = this.contentIdFromHash(contentHash);
        
        if (metaData == null) {
            metaData = new EditableArtifactMetaDataDto();
        }
        
        return super.updateArtifactWithMetadata(artifactId, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }
    
    @Transactional
    public void updateArtifactVersionMetaDataAndState(String artifactId, Integer version,
            EditableArtifactMetaDataDto metaData, ArtifactState state) {
        this.updateArtifactVersionMetaData(artifactId, version, metaData);
        this.updateArtifactState(artifactId, state, version);
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

}
