package io.apicurio.registry.content.refs;

public class AbstractReferenceArtifactIdentifierExtractor implements ReferenceArtifactIdentifierExtractor {
    @Override
    public String extractArtifactId(String fullReference) {
        return fullReference;
    }

    @Override
    public String extractGroupId(String fullReference) {
        return null;
    }
}
