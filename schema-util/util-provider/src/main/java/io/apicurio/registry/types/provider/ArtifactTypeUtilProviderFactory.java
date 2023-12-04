package io.apicurio.registry.types.provider;

import jakarta.ws.rs.core.MediaType;
import java.util.List;


public interface ArtifactTypeUtilProviderFactory {
    ArtifactTypeUtilProvider getArtifactTypeProvider(String type);

    List<String> getAllArtifactTypes();

    MediaType getArtifactMediaType(String type);
}
