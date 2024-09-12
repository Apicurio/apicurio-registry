package io.apicurio.registry.storage.importing.v3;

import io.apicurio.registry.storage.error.RegistryStorageException;
import io.apicurio.registry.storage.importing.DataImporter;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.ManifestEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.v3.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.v3.BranchEntity;
import io.apicurio.registry.utils.impexp.v3.CommentEntity;
import io.apicurio.registry.utils.impexp.v3.ContentEntity;
import io.apicurio.registry.utils.impexp.v3.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.v3.GroupEntity;
import io.apicurio.registry.utils.impexp.v3.GroupRuleEntity;
import org.slf4j.Logger;

public abstract class AbstractDataImporter implements DataImporter {

    protected final Logger log;

    public AbstractDataImporter(Logger log) {
        this.log = log;
    }

    /**
     * WARNING: Must be executed within a transaction!
     */
    @Override
    public void importEntity(Entity entity) {
        switch (entity.getEntityType()) {
            case ArtifactRule:
                importArtifactRule((ArtifactRuleEntity) entity);
                break;
            case Artifact:
                importArtifact((ArtifactEntity) entity);
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
            case GroupRule:
                importGroupRule((GroupRuleEntity) entity);
                break;
            case Comment:
                importComment((CommentEntity) entity);
                break;
            case Branch:
                importBranch((BranchEntity) entity);
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
                throw new RegistryStorageException(
                        "Unhandled entity type during import: " + entity.getEntityType());
        }
    }

    protected abstract void importArtifactRule(ArtifactRuleEntity entity);

    protected abstract void importArtifact(ArtifactEntity entity);

    protected abstract void importArtifactVersion(ArtifactVersionEntity entity);

    protected abstract void importComment(CommentEntity entity);

    protected abstract void importContent(ContentEntity entity);

    protected abstract void importGlobalRule(GlobalRuleEntity entity);

    protected abstract void importGroup(GroupEntity entity);

    protected abstract void importGroupRule(GroupRuleEntity entity);

    protected abstract void importBranch(BranchEntity entity);
}
