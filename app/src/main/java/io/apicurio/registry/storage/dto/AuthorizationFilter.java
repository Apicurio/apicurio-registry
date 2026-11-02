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
 * @param owner the caller; resources it owns always match (null for anonymous callers)
 * @param ownedArtifacts names of artifacts owned by {@code owner}, resolved from the primary
 *        storage for search engines that do not index ownership (always match)
 */
public record AuthorizationFilter(
        boolean allowAll,
        Set<String> allowExact,
        Set<String> allowPrefix,
        Set<String> denyExact,
        Set<String> denyPrefix,
        String owner,
        Set<String> ownedArtifacts) {

    public AuthorizationFilter {
        allowExact = Set.copyOf(allowExact);
        allowPrefix = Set.copyOf(allowPrefix);
        denyExact = Set.copyOf(denyExact);
        denyPrefix = Set.copyOf(denyPrefix);
        ownedArtifacts = Set.copyOf(ownedArtifacts);
    }

    public AuthorizationFilter(boolean allowAll, Set<String> allowExact, Set<String> allowPrefix,
            Set<String> denyExact, Set<String> denyPrefix, String owner) {
        this(allowAll, allowExact, allowPrefix, denyExact, denyPrefix, owner, Set.of());
    }

    /** @return a filter that matches nothing by grants, only resources owned by {@code owner} */
    public static AuthorizationFilter ownedBy(String owner) {
        return new AuthorizationFilter(false, Set.of(), Set.of(), Set.of(), Set.of(), owner);
    }

    public AuthorizationFilter withOwnedArtifacts(Set<String> names) {
        return new AuthorizationFilter(allowAll, allowExact, allowPrefix, denyExact, denyPrefix, owner, names);
    }
}
