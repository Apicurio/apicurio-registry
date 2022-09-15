package io.apicurio.registry.util;

import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.utils.impexp.ArtifactRuleEntity;
import io.apicurio.registry.utils.impexp.ArtifactVersionEntity;
import io.apicurio.registry.utils.impexp.ContentEntity;
import io.apicurio.registry.utils.impexp.Entity;
import io.apicurio.registry.utils.impexp.GlobalRuleEntity;
import io.apicurio.registry.utils.impexp.GroupEntity;

public interface DataImporter {

    void importEntity(Entity entity) throws RegistryStorageException;

    void importArtifactRule(ArtifactRuleEntity entity);

    void importArtifactVersion(ArtifactVersionEntity entity);

    void importContent(ContentEntity entity);

    void importGlobalRule(GlobalRuleEntity entity);

    void importGroup(GroupEntity entity);

}
