package io.apicurio.registry.types.provider;

import java.util.List;
import java.util.Map;

public interface ArtifactTypeUtilProviderFactory {

    ArtifactTypeUtilProvider getArtifactTypeProvider(String type);

    List<String> getAllArtifactTypes();

    List<ArtifactTypeUtilProvider> getAllArtifactTypeProviders();

    /**
     * Returns a map from normalized file extension (lowercase, with leading dot)
     * to the artifact type that claims it. Built once during validation; lookups are O(1).
     */
    Map<String, String> getFileExtensionToTypeMap();

}
