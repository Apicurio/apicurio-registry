package io.apicurio.authz;

import java.util.Set;

public record Grant(
        String principal,
        String principalRole,
        String operation,
        String resourceType,
        String resourcePatternType,
        String resourcePattern,
        boolean deny) {

    public boolean matchesPrincipal(String user, Set<String> roles) {
        if (user != null && user.equals(principal)) {
            return true;
        }
        return !principalRole.isEmpty() && roles.contains(principalRole);
    }

    public boolean matchesResourceType(String type) {
        return type.equals(resourceType);
    }

    /**
     * Allow semantics: true if this grant's operation covers {@code op}. {@code admin} implies
     * {@code write}, which implies {@code read}.
     */
    public boolean impliesOperation(String op) {
        return implies(operation, op);
    }

    /**
     * Deny semantics: true if denying this grant's operation denies {@code op}, i.e. if
     * {@code op} implies it. Denying {@code read} denies everything; denying {@code write} leaves
     * read access, making a resource read-only.
     */
    public boolean deniesOperation(String op) {
        return implies(op, operation);
    }

    private static boolean implies(String granted, String requested) {
        if (granted.equals(requested)) {
            return true;
        }
        if ("admin".equals(granted)) {
            return "write".equals(requested) || "read".equals(requested);
        }
        return "write".equals(granted) && "read".equals(requested);
    }

    public boolean isWildcard() {
        return "*".equals(resourcePattern);
    }

    public boolean matchesResource(String resourceName) {
        if (isWildcard()) {
            return true;
        }
        if ("exact".equals(resourcePatternType)) {
            return resourcePattern.equals(resourceName);
        }
        if ("prefix".equals(resourcePatternType)) {
            return resourceName.startsWith(resourcePattern);
        }
        return resourcePattern.equals(resourceName);
    }
}
