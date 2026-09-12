package io.apicurio.registry.promotion;

/**
 * Artifact version fetched from a promotion source (another registry, or this instance).
 */
public record RemoteArtifactVersion(String groupId, String artifactId, String version, String artifactType,
        String contentType, String content, String name, String description) {
}
