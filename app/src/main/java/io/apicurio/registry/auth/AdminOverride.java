package io.apicurio.registry.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Optional;

@ApplicationScoped
public class AdminOverride {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    Instance<JsonWebToken> jsonWebToken;

    public boolean isAdmin() {
        if (!authConfig.adminOverrideEnabled) {
            return false;
        }

        if ("token".equals(authConfig.adminOverrideFrom)) {
            if ("role".equals(authConfig.adminOverrideType)) {
                return hasAdminRole();
            } else if ("claim".equals(authConfig.adminOverrideType)) {
                return hasAdminClaim();
            } else if ("user".equals(authConfig.adminOverrideType)) {
                return isAdminUser();
            }
        }
        return false;
    }

    private boolean isAdminUser() {
        return authConfig.adminOverrideUser.equals(securityIdentity.getPrincipal().getName());
    }

    private boolean hasAdminRole() {
        return securityIdentity.hasRole(authConfig.adminOverrideRole);
    }

    private boolean hasAdminClaim() {
        final Optional<Object> claimValue = jsonWebToken.get().claim(authConfig.adminOverrideClaim);
        if (claimValue.isPresent()) {
            return authConfig.adminOverrideClaimValue.equals(claimValue.get().toString());
        }
        return false;
    }

}
