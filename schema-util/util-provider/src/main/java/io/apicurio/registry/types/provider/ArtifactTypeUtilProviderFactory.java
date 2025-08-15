package io.apicurio.registry.types.provider;

import java.util.List;

public interface ArtifactTypeUtilProviderFactory {

    ArtifactTypeUtilProvider getArtifactTypeProvider(String type);

    List<String> getAllArtifactTypes();

    List<ArtifactTypeUtilProvider> getAllArtifactTypeProviders();

}
