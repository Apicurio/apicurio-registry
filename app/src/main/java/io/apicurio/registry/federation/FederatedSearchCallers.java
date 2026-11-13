package io.apicurio.registry.federation;

import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.RoleBasedAccessController;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tells a federated search who its caller is.
 */
@ApplicationScoped
public class FederatedSearchCallers {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    AuthConfig authConfig;

    @Inject
    AdminOverride adminOverride;

    @Inject
    RoleBasedAccessController rbac;

    /**
     * The caller of the current request.
     * <p>
     * An authenticated caller is told apart from others by principal name, and an anonymous one by
     * the network address the request came from.
     * <p>
     * The list of peers is for administrators only, so only an administrator, by role or by admin
     * override, is shown which peer is which and why one failed. With authentication off everyone
     * may list the peers, and so everyone is shown.
     */
    public FederatedSearchCaller current(String remoteAddress) {
        boolean authenticated = securityIdentity != null && !securityIdentity.isAnonymous();
        String key = authenticated ? "principal:" + securityIdentity.getPrincipal().getName()
                : "address:" + remoteAddress;
        boolean admin = !authConfig.isAuthenticationEnabled() || (authenticated
                && ((authConfig.isRbacEnabled() && rbac.isAdmin()) || adminOverride.isAdmin()));
        return new FederatedSearchCaller(key, admin);
    }
}
