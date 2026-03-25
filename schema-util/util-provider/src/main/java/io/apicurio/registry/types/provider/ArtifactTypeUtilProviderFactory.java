package io.apicurio.registry.types.provider;

import io.apicurio.registry.types.ArtifactType;

import java.util.List;

public interface ArtifactTypeUtilProviderFactory {

    ArtifactTypeUtilProvider getArtifactTypeProvider(String type);

    default ArtifactTypeUtilProvider getArtifactTypeProvider(ArtifactType type) {
        return getArtifactTypeProvider(type.value());
    }

    List<String> getAllArtifactTypes();

    List<ArtifactTypeUtilProvider> getAllArtifactTypeProviders();

}
