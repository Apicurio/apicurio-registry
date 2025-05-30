package io.apicurio.registry.content.refs;

public interface ReferenceArtifactIdentifierExtractor {
    String extractArtifactId(String fullReference);

    String extractGroupId(String fullReference);
}
