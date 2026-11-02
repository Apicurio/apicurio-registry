package io.apicurio.authz;

import java.util.Set;

import com.google.re2j.Pattern;

/**
 * The read access of one subject to one resource type, expressed as resource-name patterns so a
 * caller can translate them into a storage query (search pre-filtering). The patterns are exactly
 * the ones the access rules evaluate for point access; they are not widened or narrowed.
 *
 * <p>A resource name is readable iff it is not denied and is allowed:</p>
 * <ul>
 * <li>denied: it equals a name in {@code deniedExact}, starts with a name in {@code deniedPrefix}
 * (an empty prefix denies everything), or fully matches a regular expression in
 * {@code deniedPatterns}</li>
 * <li>allowed: {@code allowAll}, or it equals a name in {@code allowedExact}, starts with a name
 * in {@code allowedPrefix}, or fully matches a regular expression in {@code allowedPatterns}</li>
 * </ul>
 *
 * <p>Patterns use RE2 syntax, as the access rules do. A storage query that cannot evaluate them
 * must not ignore them: see {@link #hasPatterns()}.</p>
 */
public record SearchFilterData(
        boolean allowAll,
        Set<String> allowedExact,
        Set<String> allowedPrefix,
        Set<String> allowedPatterns,
        Set<String> deniedExact,
        Set<String> deniedPrefix,
        Set<String> deniedPatterns) {

    public SearchFilterData {
        allowedExact = Set.copyOf(allowedExact);
        allowedPrefix = Set.copyOf(allowedPrefix);
        allowedPatterns = Set.copyOf(allowedPatterns);
        deniedExact = Set.copyOf(deniedExact);
        deniedPrefix = Set.copyOf(deniedPrefix);
        deniedPatterns = Set.copyOf(deniedPatterns);
    }

    /** A scope without regular expressions. */
    public SearchFilterData(boolean allowAll, Set<String> allowedExact, Set<String> allowedPrefix,
            Set<String> deniedExact, Set<String> deniedPrefix) {
        this(allowAll, allowedExact, allowedPrefix, Set.of(), deniedExact, deniedPrefix, Set.of());
    }

    public static SearchFilterData all() {
        return new SearchFilterData(true, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static SearchFilterData none() {
        return new SearchFilterData(false, Set.of(), Set.of(), Set.of(), Set.of());
    }

    /**
     * @return true if the scope has regular expressions. A storage query that cannot evaluate
     *         them must fail closed: ignoring a denied pattern would expose resources, ignoring an
     *         allowed one would hide them.
     */
    public boolean hasPatterns() {
        return !allowedPatterns.isEmpty() || !deniedPatterns.isEmpty();
    }

    /** @return true if no resource can be readable */
    public boolean allowsNothing() {
        return (!allowAll && allowedExact.isEmpty() && allowedPrefix.isEmpty() && allowedPatterns.isEmpty())
                || deniedPrefix.contains("");
    }

    /** @return true if every resource is readable */
    public boolean allowsEverything() {
        return allowAll && deniedExact.isEmpty() && deniedPrefix.isEmpty() && deniedPatterns.isEmpty();
    }

    /** Reference evaluation of the patterns, matching point access. */
    public boolean matches(String resourceName) {
        if (deniedExact.contains(resourceName)
                || deniedPrefix.stream().anyMatch(resourceName::startsWith)
                || deniedPatterns.stream().anyMatch(p -> Pattern.matches(p, resourceName))) {
            return false;
        }
        return allowAll || allowedExact.contains(resourceName)
                || allowedPrefix.stream().anyMatch(resourceName::startsWith)
                || allowedPatterns.stream().anyMatch(p -> Pattern.matches(p, resourceName));
    }
}
