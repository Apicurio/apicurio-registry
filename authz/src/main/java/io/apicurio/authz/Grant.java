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

    public boolean impliesOperation(String op) {
        if (operation.equals(op)) {
            return true;
        }
        if ("admin".equals(operation)) {
            return true;
        }
        return "write".equals(operation) && "read".equals(op);
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

    public String extractGroupFromPattern(String separator) {
        if ("prefix".equals(resourcePatternType) || "exact".equals(resourcePatternType)) {
            int idx = resourcePattern.indexOf(separator);
            if (idx > 0) {
                return resourcePattern.substring(0, idx);
            }
        }
        return null;
    }

    /**
     * Returns true if this is a "prefix" grant that covers an entire group (e.g. {@code "team-a/"})
     * rather than a sub-path within a group (e.g. {@code "team-a/secret/"}). Only full-group prefix
     * grants can be safely collapsed to a group-level search filter; sub-path prefixes must be kept
     * as a scoped artifact-prefix filter to avoid over-granting search visibility beyond what
     * point-access allows.
     */
    public boolean isFullGroupPrefix(String separator) {
        if (!"prefix".equals(resourcePatternType)) {
            return false;
        }
        int idx = resourcePattern.indexOf(separator);
        return idx > 0 && idx == resourcePattern.length() - separator.length();
    }

}
