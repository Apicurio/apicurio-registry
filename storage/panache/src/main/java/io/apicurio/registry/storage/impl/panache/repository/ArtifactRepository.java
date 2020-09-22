package io.apicurio.registry.storage.impl.panache.repository;

import io.apicurio.registry.storage.impl.panache.entity.Artifact;
import io.apicurio.registry.storage.impl.panache.result.ArtifactMetaData;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ArtifactRepository implements PanacheRepository<Artifact> {

    public Artifact findById(String artifactId) {
        return find("artifactId", artifactId).firstResult();
    }

    public ArtifactMetaData updateLatestVersion(String artifactId, long latest) {
        update("latest = ?1 where artifactId = ?2", latest, artifactId);
        return getArtifactMetadata(artifactId);
    }

    public ArtifactMetaData getArtifactMetadata(String artifactId) {

        return getEntityManager().createQuery("SELECT new io.apicurio.registry.storage.impl.panache.result.ArtifactMetaData (a.artifactId, a.artifactType, v.globalId, v.version, v.state, v.name, v.description, v.labelsStr, v.propertiesStr, v.createdBy, v.createdOn) FROM Artifact a JOIN Version v ON a.latest = v.globalId WHERE a.artifactId = :artifactId", ArtifactMetaData.class)
                .setParameter("artifactId", artifactId)
                .getSingleResult();
    }
}
