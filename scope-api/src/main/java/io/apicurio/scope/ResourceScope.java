package io.apicurio.scope;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

/**
 * The resources of one type that a subject may access with one operation.
 *
 * <p>Three cases, so consumers handle the short cuts first: {@link Unrestricted} (every name),
 * {@link Forbidden} (no name) and {@link Scoped} (selectors to evaluate). {@link #of} returns the
 * short cuts whenever the selectors make them evident (see there), but a {@link Scoped} may still
 * select every name or none, for example when the same name is both allowed and denied. Consumers
 * must evaluate a {@link Scoped}, not assume it is non-empty.</p>
 *
 * <p>Matching rules, which every provider and consumer must follow:</p>
 * <ul>
 *     <li>names are opaque, caller-defined strings, compared exactly and case-sensitively;</li>
 *     <li>exact names match by equality, prefixes by {@code startsWith}, patterns by a full match
 *     of the RE2 regular expression ({@code java.util.regex} is not used: RE2 runs in linear
 *     time, so user-supplied patterns can't cause catastrophic backtracking);</li>
 *     <li>a name is in scope if no deny selector matches it and either {@code allowAll} is set or
 *     an allow selector matches it.</li>
 * </ul>
 */
public sealed interface ResourceScope permits ResourceScope.Unrestricted, ResourceScope.Forbidden,
        ResourceScope.Scoped {

    /** @return true if {@code name} is in scope */
    boolean matches(String name);

    /** Every name is in scope. */
    record Unrestricted() implements ResourceScope {
        @Override
        public boolean matches(String name) {
            return true;
        }
    }

    /** No name is in scope. */
    record Forbidden() implements ResourceScope {
        @Override
        public boolean matches(String name) {
            return false;
        }
    }

    /** @return the scope of every name */
    static ResourceScope unrestricted() {
        return new Unrestricted();
    }

    /** @return the scope of no name */
    static ResourceScope forbidden() {
        return new Forbidden();
    }

    /**
     * Builds a scope. Returns {@link Forbidden} when nothing is allowed or the empty prefix is
     * denied, and {@link Unrestricted} when everything is allowed ({@code allowAll} or the empty
     * prefix) and nothing is denied. Otherwise returns a {@link Scoped}, which is not otherwise
     * checked for emptiness.
     *
     * @param allowAll allow every name that no deny selector matches
     * @throws IllegalArgumentException if a pattern is not a valid RE2 regular expression
     */
    static ResourceScope of(boolean allowAll, Set<String> allowExact, Set<String> allowPrefix,
            Set<String> allowPatterns, Set<String> denyExact, Set<String> denyPrefix, Set<String> denyPatterns) {
        // The empty prefix matches every name
        if (allowPrefix.contains("")) {
            allowAll = true;
            allowPrefix = Set.of();
        }
        boolean deniesNothing = denyExact.isEmpty() && denyPrefix.isEmpty() && denyPatterns.isEmpty();
        boolean allowsNothing = !allowAll && allowExact.isEmpty() && allowPrefix.isEmpty()
                && allowPatterns.isEmpty();
        if (allowsNothing || denyPrefix.contains("")) {
            return forbidden();
        }
        if (allowAll && deniesNothing) {
            return unrestricted();
        }
        return new Scoped(allowAll, allowExact, allowPrefix, allowPatterns, denyExact, denyPrefix, denyPatterns);
    }

    /**
     * Some, but not all, names are in scope. Created through {@link ResourceScope#of}.
     *
     * <p>A consumer that cannot evaluate patterns, such as a query in a store without RE2, must
     * fail closed when {@link #hasPatterns()} is true: ignoring a deny pattern would expose
     * resources, and ignoring an allow pattern would hide them.</p>
     */
    final class Scoped implements ResourceScope {
        private final boolean allowAll;
        private final Set<String> allowExact;
        private final Set<String> allowPrefix;
        private final Set<String> allowPatterns;
        private final Set<String> denyExact;
        private final Set<String> denyPrefix;
        private final Set<String> denyPatterns;
        private final List<Pattern> compiledAllow;
        private final List<Pattern> compiledDeny;

        private Scoped(boolean allowAll, Set<String> allowExact, Set<String> allowPrefix, Set<String> allowPatterns,
                Set<String> denyExact, Set<String> denyPrefix, Set<String> denyPatterns) {
            this.allowAll = allowAll;
            this.allowExact = Set.copyOf(allowExact);
            this.allowPrefix = Set.copyOf(allowPrefix);
            this.allowPatterns = Set.copyOf(allowPatterns);
            this.denyExact = Set.copyOf(denyExact);
            this.denyPrefix = Set.copyOf(denyPrefix);
            this.denyPatterns = Set.copyOf(denyPatterns);
            this.compiledAllow = compile(this.allowPatterns);
            this.compiledDeny = compile(this.denyPatterns);
        }

        private static List<Pattern> compile(Set<String> patterns) {
            try {
                return patterns.stream().map(Pattern::compile).toList();
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid RE2 pattern: " + e.getMessage(), e);
            }
        }

        @Override
        public boolean matches(String name) {
            if (denyExact.contains(name) || denyPrefix.stream().anyMatch(name::startsWith)
                    || compiledDeny.stream().anyMatch(p -> p.matches(name))) {
                return false;
            }
            return allowAll || allowExact.contains(name) || allowPrefix.stream().anyMatch(name::startsWith)
                    || compiledAllow.stream().anyMatch(p -> p.matches(name));
        }

        /** @return true if any allow or deny pattern is present */
        public boolean hasPatterns() {
            return !allowPatterns.isEmpty() || !denyPatterns.isEmpty();
        }

        /** @return true if every name that no deny selector matches is in scope */
        public boolean allowAll() {
            return allowAll;
        }

        public Set<String> allowExact() {
            return allowExact;
        }

        public Set<String> allowPrefix() {
            return allowPrefix;
        }

        public Set<String> allowPatterns() {
            return allowPatterns;
        }

        public Set<String> denyExact() {
            return denyExact;
        }

        public Set<String> denyPrefix() {
            return denyPrefix;
        }

        public Set<String> denyPatterns() {
            return denyPatterns;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Scoped s && allowAll == s.allowAll && allowExact.equals(s.allowExact)
                    && allowPrefix.equals(s.allowPrefix) && allowPatterns.equals(s.allowPatterns)
                    && denyExact.equals(s.denyExact) && denyPrefix.equals(s.denyPrefix)
                    && denyPatterns.equals(s.denyPatterns);
        }

        @Override
        public int hashCode() {
            return Objects.hash(allowAll, allowExact, allowPrefix, allowPatterns, denyExact, denyPrefix, denyPatterns);
        }

        @Override
        public String toString() {
            return "Scoped[allowAll=" + allowAll + ", allowExact=" + allowExact + ", allowPrefix=" + allowPrefix
                    + ", allowPatterns=" + allowPatterns + ", denyExact=" + denyExact + ", denyPrefix=" + denyPrefix
                    + ", denyPatterns=" + denyPatterns + "]";
        }
    }
}
