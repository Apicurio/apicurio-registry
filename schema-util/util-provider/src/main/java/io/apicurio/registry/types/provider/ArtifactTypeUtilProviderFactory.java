package io.apicurio.registry.types.provider;

import io.apicurio.registry.types.ArtifactType;

import java.util.List;

public interface ArtifactTypeUtilProviderFactory {

    ArtifactTypeUtilProvider getArtifactTypeProvider(ArtifactType type);

    List<ArtifactType> getAllArtifactTypes();

    List<ArtifactTypeUtilProvider> getAllArtifactTypeProviders();

}
