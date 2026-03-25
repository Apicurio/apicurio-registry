package io.apicurio.registry.contracts.tags;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class TagExtractorFactory {
    @Inject
    Instance<TagExtractor> extractors;

    public Optional<TagExtractor> getExtractor(String artifactType) {
        if (artifactType == null) {
            return Optional.empty();
        }
        String normalized = artifactType.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return extractors.stream()
                .filter(extractor -> normalized.equalsIgnoreCase(extractor.getArtifactType()))
                .findFirst();
    }
}
