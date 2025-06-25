package io.apicurio.registry.resolver.client;

import io.apicurio.registry.resolver.strategy.ArtifactReference;

import java.util.List;

public interface RegistrySDK extends AutoCloseable {

    String getSchemaByContentId(Long contentId);

    List<RegistryArtifactReference> getReferencesByContentId(long contentId);

    String getSchemaByGlobalId(long globalId, boolean dereferenced);

    List<RegistryArtifactReference> getReferencesByGlobalId(long globalId);

    String getSchemaByGAV(String groupId, String artifactId, String version);

    List<RegistryArtifactReference> getReferencesByGAV(String groupId, String artifactId, String version);

    String getSchemaByContentHash(String contentHash);

    List<RegistryArtifactReference> getReferencesByContentHash(String contentHash);

    List<RegistryVersionCoordinates> searchVersionsByContent(String schemaString, String artifactType,
            ArtifactReference reference, boolean canonical);

    RegistryVersionCoordinates createArtifact(String artifactType, String groupId, String artifactId, String version,
            String autoCreateBehavior, boolean canonical, String schemaString, List<RegistryArtifactReference> references);

    RegistryVersionCoordinates getVersionCoordinatesByGAV(String groupId, String artifactId, String version);

    Object getClient();
}
