package io.apicurio.registry.content.refs;

public class AvroReferenceArtifactIdentifierExtractor extends AbstractReferenceArtifactIdentifierExtractor {
    @Override
    public String extractArtifactId(String fullReference) {
        if (fullReference == null || fullReference.isEmpty()) {
            return null;
        }
        int lastDot = fullReference.lastIndexOf(".");
        return fullReference.substring(lastDot + 1);
    }

    @Override
    public String extractGroupId(String fullReference) {
        if (fullReference == null || fullReference.isEmpty()) {
            return null;
        }
        int lastDot = fullReference.lastIndexOf(".");
        return lastDot < 0 ? null : fullReference.substring(0, lastDot);
    }
}
