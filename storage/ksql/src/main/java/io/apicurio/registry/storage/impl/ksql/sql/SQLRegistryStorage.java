package io.apicurio.registry.storage.impl.ksql.sql;

import java.util.concurrent.CompletionStage;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.ArtifactMetaDataDto;
import io.apicurio.registry.storage.ArtifactNotFoundException;
import io.apicurio.registry.storage.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.GlobalIdGenerator;
import io.apicurio.registry.types.ArtifactType;

@ApplicationScoped
@Named("SQLRegistryStorage")
public class SQLRegistryStorage extends AbstractSqlRegistryStorage{

    private static final Logger log = LoggerFactory.getLogger(SQLRegistryStorage.class);

    @PostConstruct
    void onConstruct() {
        log.info("Using internal SQL storage.");
    }

    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifact(String artifactId, ArtifactType artifactType, ContentHandle content, GlobalIdGenerator globalIdGenerator)
            throws ArtifactAlreadyExistsException, RegistryStorageException {
        return super.createArtifact(artifactId, artifactType, content, globalIdGenerator);
    }

    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> createArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator) throws ArtifactAlreadyExistsException, RegistryStorageException {
        return super.createArtifactWithMetadata(artifactId, artifactType, content, metaData, globalIdGenerator);
    }

    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifact(String artifactId, ArtifactType artifactType, ContentHandle content, GlobalIdGenerator globalIdGenerator)
            throws ArtifactNotFoundException, RegistryStorageException {
        return super.updateArtifact(artifactId, artifactType, content, globalIdGenerator);
    }

    @Override @Transactional
    public CompletionStage<ArtifactMetaDataDto> updateArtifactWithMetadata(String artifactId, ArtifactType artifactType, ContentHandle content,
            EditableArtifactMetaDataDto metaData, GlobalIdGenerator globalIdGenerator) throws ArtifactNotFoundException, RegistryStorageException {
        return super.updateArtifactWithMetadata(artifactId, artifactType, content, metaData, globalIdGenerator);
    }



}
