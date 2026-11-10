package io.apicurio.registry.resolver.strategy;

/**
 * Group and artifact ID chosen by a {@link ReferenceNamingStrategy}.
 *
 * @param groupId the group, or null for the default group
 * @param artifactId the artifact ID
 */
public record ReferenceCoordinates(String groupId, String artifactId) {
}
