package io.apicurio.authz;

import java.util.Set;

/**
 * The read grants of one subject for one resource type, expressed as resource-name patterns so a
 * caller can translate them into a storage query (search pre-filtering). The patterns are exactly
 * the ones {@link GrantsAuthorizer} evaluates for point access; they are not widened or narrowed.
 *
 * <p>A resource name is readable iff it is not denied and is allowed:</p>
 * <ul>
 * <li>denied: it equals a name in {@code deniedExact} or starts with a name in {@code deniedPrefix}
 * (an empty prefix denies everything)</li>
 * <li>allowed: {@code allowAll}, or it equals a name in {@code allowedExact}, or starts with a name
 * in {@code allowedPrefix}</li>
 * </ul>
 */
public record SearchFilterData(
        boolean allowAll,
        Set<String> allowedExact,
        Set<String> allowedPrefix,
        Set<String> deniedExact,
        Set<String> deniedPrefix) {

    public SearchFilterData {
        allowedExact = Set.copyOf(allowedExact);
        allowedPrefix = Set.copyOf(allowedPrefix);
        deniedExact = Set.copyOf(deniedExact);
        deniedPrefix = Set.copyOf(deniedPrefix);
    }

    public static SearchFilterData all() {
        return new SearchFilterData(true, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static SearchFilterData none() {
        return new SearchFilterData(false, Set.of(), Set.of(), Set.of(), Set.of());
    }

    /** @return true if no resource can be readable */
    public boolean allowsNothing() {
        return (!allowAll && allowedExact.isEmpty() && allowedPrefix.isEmpty())
                || deniedPrefix.contains("");
    }

    /** @return true if every resource is readable */
    public boolean allowsEverything() {
        return allowAll && deniedExact.isEmpty() && deniedPrefix.isEmpty();
    }

    /** Reference evaluation of the patterns, matching {@link GrantsAuthorizer} point access. */
    public boolean matches(String resourceName) {
        if (deniedExact.contains(resourceName)
                || deniedPrefix.stream().anyMatch(resourceName::startsWith)) {
            return false;
        }
        return allowAll || allowedExact.contains(resourceName)
                || allowedPrefix.stream().anyMatch(resourceName::startsWith);
    }
}
