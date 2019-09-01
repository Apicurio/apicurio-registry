package io.apicurio.registry.storage.impl.jpa;

import javax.enterprise.context.Dependent;

import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.impl.jpa.entity.Artifact;

@Dependent
public class JPAEntityMapper {

    public StoredArtifact toStoredArtifact(Artifact artifact) {
        return StoredArtifact.builder()
                .id(artifact.getGlobalId())
                .version(artifact.getVersion())
                .content(artifact.getContent())
                .build();
    }
}
