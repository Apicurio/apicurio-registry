package io.apicurio.registry.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class TokenRoleProvider implements RoleProvider {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Checks if the security identity has any of the specified roles.
     * This supports multiple role mappings (e.g., Azure AD groups and app roles).
     *
     * @param roles the set of role names to check
     * @return true if the user has any of the roles
     */
    private boolean hasAnyRole(Set<String> roles) {
        return roles.stream().anyMatch(securityIdentity::hasRole);
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isAdmin()
     */
    @Override
    public boolean isAdmin() {
        return hasAnyRole(authConfig.getAdminRoles());
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isDeveloper()
     */
    @Override
    public boolean isDeveloper() {
        return hasAnyRole(authConfig.getDeveloperRoles());
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isReadOnly()
     */
    @Override
    public boolean isReadOnly() {
        return hasAnyRole(authConfig.getReadOnlyRoles());
    }

}
