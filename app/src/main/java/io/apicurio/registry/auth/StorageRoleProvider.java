package io.apicurio.registry.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RoleType;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;


@ApplicationScoped
public class StorageRoleProvider implements RoleProvider {

    @Inject
    SecurityIdentity securityIdentity;

    //We need to inject the identityToken so we can check some claims when needed.
    @Inject
    Instance<JsonWebToken> identityToken;

    private static final String AZP_CLAIM = "azp";

    @Inject
    @Current
    RegistryStorage storage;

    private boolean hasRole(String role) {
        String role4principal = storage.getRoleForPrincipal(securityIdentity.getPrincipal().getName());
        boolean hasRole = role.equals(role4principal);
        //Check for Keycloak service accounts since they're prefixed with service-account.
        if (!hasRole && tokenHasAzpClaim()) {
            hasRole = role.equals(storage.getRoleForPrincipal(identityToken.get().getClaim(AZP_CLAIM)));
        }
        return hasRole;
    }

    private boolean tokenHasAzpClaim() {
        return identityToken.isResolvable() && identityToken.get().getClaim(AZP_CLAIM) != null;
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isDeveloper()
     */
    @Override
    public boolean isDeveloper() {
        return hasRole(RoleType.DEVELOPER.name());
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isReadOnly()
     */
    @Override
    public boolean isReadOnly() {
        return hasRole(RoleType.READ_ONLY.name());
    }

    /**
     * @see io.apicurio.registry.auth.RoleProvider#isAdmin()
     */
    @Override
    public boolean isAdmin() {
        return hasRole(RoleType.ADMIN.name());
    }

}
