package io.apicurio.registry.resolver.cache;

/**
 * Cache key for auto-registering a schema: the content (with its nested references) and the coordinates it is
 * registered under. Identical content registered under two different coordinates gets two entries, so each
 * target is registered.
 *
 * @param content the schema content and nested references
 * @param groupId the target group (null for the default group)
 * @param artifactId the target artifact ID
 * @param version the requested version, or null if the registry assigns it
 */
public record AutoCreateKey(ContentWithReferences content, String groupId, String artifactId, String version) {
}
