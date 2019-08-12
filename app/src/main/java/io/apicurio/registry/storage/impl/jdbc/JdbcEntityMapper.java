package io.apicurio.registry.storage.impl.jdbc;

import io.apicurio.registry.storage.StoredArtifact;
import io.apicurio.registry.storage.impl.jdbc.entity.Artifact;

import javax.enterprise.context.Dependent;

@Dependent
public class JdbcEntityMapper {

    public StoredArtifact toStoredArtifact(Artifact artifact) {
        return StoredArtifact.builder()
                .id(artifact.getGlobalId())
                .version(artifact.getVersion())
                .content(artifact.getContent())
                .build();
    }
}
