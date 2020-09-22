package io.apicurio.registry.storage.impl.panache.repository;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.storage.impl.panache.entity.Artifact;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ArtifactRepository implements PanacheRepository<Artifact> {

    @Inject
    private VersionRepository versionRepository;

    public Artifact findById(String artifactId) {
        return find("artifactId", artifactId).firstResult();
    }

    public ArtifactMetaData updateLatestVersion(String artifactId, long latest) {
        update("latest = ?1 where artifactId = ?2", latest, artifactId);
        return versionRepository.getArtifactMetadata(artifactId);
    }
}
