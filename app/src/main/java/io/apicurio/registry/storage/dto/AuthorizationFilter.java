package io.apicurio.registry.storage.dto;

import java.util.Set;

/**
 * Restricts a search to the resources a caller may read under per-resource authorization. Names
 * are grants resource names: {@code groupId/artifactId} for artifacts and versions, {@code groupId}
 * for groups, where the default group is named {@code default}.
 *
 * <p>A resource matches iff {@code owner} owns it, or it is not denied and is allowed:</p>
 * <ul>
 * <li>denied: name equals one of {@code denyExact} or starts with one of {@code denyPrefix}</li>
 * <li>allowed: {@code allowAll}, or name equals one of {@code allowExact} or starts with one of
 * {@code allowPrefix}</li>
 * </ul>
 *
 * @param owner the caller; resources it owns always match (null for anonymous callers). Search
 *        engines that cannot match artifact ownership exactly ignore it, which only narrows results.
 */
public record AuthorizationFilter(
        boolean allowAll,
        Set<String> allowExact,
        Set<String> allowPrefix,
        Set<String> denyExact,
        Set<String> denyPrefix,
        String owner) {

    public AuthorizationFilter {
        allowExact = Set.copyOf(allowExact);
        allowPrefix = Set.copyOf(allowPrefix);
        denyExact = Set.copyOf(denyExact);
        denyPrefix = Set.copyOf(denyPrefix);
    }
}
