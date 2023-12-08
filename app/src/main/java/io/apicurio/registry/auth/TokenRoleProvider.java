package io.apicurio.registry.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.security.identity.SecurityIdentity;

@ApplicationScoped
public class TokenRoleProvider implements RoleProvider {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    private boolean hasRole(String role) {
        return securityIdentity.hasRole(role);
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isAdmin()
     */
    @Override
    public boolean isAdmin() {
        return hasRole(authConfig.adminRole);
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isDeveloper()
     */
    @Override
    public boolean isDeveloper() {
        return hasRole(authConfig.developerRole);
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isReadOnly()
     */
    @Override
    public boolean isReadOnly() {
        return hasRole(authConfig.readOnlyRole);
    }

}
