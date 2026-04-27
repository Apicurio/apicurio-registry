package io.apicurio.authz;

import java.util.Set;

public record Grant(
        String principal,
        String principalRole,
        String operation,
        String resourceType,
        String resourcePatternType,
        String resourcePattern) {

    public boolean matchesPrincipal(String user, Set<String> roles) {
        if (user.equals(principal)) {
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

    public String extractGroupFromPattern(String separator) {
        if (("prefix".equals(resourcePatternType) || "exact".equals(resourcePatternType))
                && resourcePattern.contains(separator)) {
            return resourcePattern.substring(0, resourcePattern.indexOf(separator));
        }
        return null;
    }
}
