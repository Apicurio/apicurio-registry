package io.apicurio.registry.content.refs.resolve;

/**
 * Identifies an artifact in the registry.
 *
 * @param groupId the group id (may be {@code null} for the default group)
 * @param artifactId the artifact id
 */
public record ArtifactCoordinates(String groupId, String artifactId) {
}
