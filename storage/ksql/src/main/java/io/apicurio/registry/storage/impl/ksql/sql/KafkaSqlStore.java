package io.apicurio.registry.storage.impl.ksql.sql;

import java.util.Date;
import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import org.jdbi.v3.core.HandleCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.GlobalIdGenerator;
import io.apicurio.registry.storage.impl.sql.mappers.ContentMapper;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

/**
 * The SQL store used by the KSQL registry storage implementation.  This is ultimately where each
 * application replica stores its data after consuming it from the Kafka topic.  Often this is a 
 * H2 in-memory database, but it could be something else (e.g. a local postgresql sidecar DB).
 * This class extends the core SQL registry storage to leverage as much of the existing SQL 
 * support as possible.  However, this class extends the SQL support to include some functionality
 * only needed by the KSQL storage.
 * 
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Named("KafkaSqlStore")
public class KafkaSqlStore extends AbstractSqlRegistryStorage {

    private static final Logger log = LoggerFactory.getLogger(KafkaSqlStore.class);

    @PostConstruct
    void onConstruct() {
        log.info("Using internal SQL storage.");
    }

    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) {
        try {
            return this.jdbi.withHandle(callback);
        } catch (Exception e) {
            throw new RegistryStorageException(e);
        }
    }

    public boolean isArtifactExists(String artifactId) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectArtifactCountById();
            return handle.createQuery(sql)
                    .bind(0, artifactId)
                    .mapTo(Integer.class)
                    .one() > 0;
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

    public boolean isArtifactRuleExists(String artifactId, RuleType rule) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectArtifactRuleCountByType();
            return handle.createQuery(sql)
                    .bind(0, artifactId)
                    .bind(1, rule.name())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }

    public boolean isGlobalRuleExists(RuleType rule) throws RegistryStorageException {
        return withHandle( handle -> {
            String sql = sqlStatements().selectGlobalRuleCountByType();
            return handle.createQuery(sql)
                    .bind(0, rule.name())
                    .mapTo(Integer.class)
                    .one() > 0;
        });
    }
    
    public void storeContent(String contentHash, ArtifactType artifactType, ContentHandle content) throws RegistryStorageException {
        withHandle( handle -> {
            super.createOrUpdateContent(handle, artifactType, content);
            return null;
        });
    }
    
    private ContentHandle getContent(long contentId) {
        return withHandle( handle -> {
            String sql = sqlStatements().selectContentById();
            return handle.createQuery(sql)
                    .bind(0, contentId)
                    .map(ContentMapper.instance)
                    .one();
        });
    }

    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId,
            ArtifactType artifactType, String contentHash, String createdBy,
            Date createdOn, EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        long contentId = this.contentIdFromHash(contentHash);
        
        if (metaData == null) {
            ContentHandle content = this.getContent(contentId);
            metaData = this.extractMetaData(artifactType, content);
        }
        
        return super.createArtifactWithMetadata(artifactId, artifactType, contentId, createdBy, createdOn,
                metaData, globalIdGenerator);
    }
    
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
    
    protected long contentIdFromHash(String contentHash) {
        return withHandle( handle -> {
            String sql = sqlStatements().selectContentIdByHash();
            return handle.createQuery(sql)
                    .bind(0, contentHash)
                    .mapTo(Long.class)
                    .one();
        });
    }

    public void updateArtifactVersionMetaDataAndState(String artifactId, Integer version,
            EditableArtifactMetaDataDto metaData, ArtifactState state) {
        this.updateArtifactVersionMetaData(artifactId, version, metaData);
        this.updateArtifactState(artifactId, state, version);
    }

}
