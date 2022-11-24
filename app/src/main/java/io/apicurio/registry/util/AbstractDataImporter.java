package io.apicurio.registry.util;

import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import org.slf4j.Logger;

import javax.transaction.Transactional;

public abstract class AbstractDataImporter implements DataImporter {

    private final Logger log;

    public AbstractDataImporter(Logger log) {
        this.log = log;
    }

    @Override
    @Transactional
    public void importEntity(Entity entity) throws RegistryStorageException {
        switch (entity.getEntityType()) {
            case ArtifactRule:
                importArtifactRule((ArtifactRuleEntity) entity);
                break;
            case ArtifactVersion:
                importArtifactVersion((ArtifactVersionEntity) entity);
                break;
            case Content:
                importContent((ContentEntity) entity);
                break;
            case GlobalRule:
                importGlobalRule((GlobalRuleEntity) entity);
                break;
            case Group:
                importGroup((GroupEntity) entity);
                break;
            case Manifest:
                ManifestEntity manifest = (ManifestEntity) entity;
                log.info("---------- Import Info ----------");
                log.info("System Name:    {}", manifest.systemName);
                log.info("System Desc:    {}", manifest.systemDescription);
                log.info("System Version: {}", manifest.systemVersion);
                log.info("Data exported on {} by user {}", manifest.exportedOn, manifest.exportedBy);
                log.info("---------- ----------- ----------");
                // Ignore the manifest for now.
                break;
            default:
                throw new RegistryStorageException("Unhandled entity type during import: " + entity.getEntityType());
        }
    }

    public Logger getLog() {
        return this.log;
    }
}
