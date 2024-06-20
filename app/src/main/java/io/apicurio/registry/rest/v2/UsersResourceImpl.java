package io.apicurio.registry.rest.v2;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.AdminOverride;
import io.apicurio.registry.auth.AuthConfig;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.auth.RoleBasedAccessController;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.v2.beans.UserInfo;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import org.slf4j.Logger;

@ApplicationScoped
@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class UsersResourceImpl implements UsersResource {

    @Inject
    Logger log;

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    RoleBasedAccessController rbac;

    @Inject
    AdminOverride adminOverride;

    /**
     * @see io.apicurio.registry.rest.v2.UsersResource#getCurrentUserInfo()
     */
    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.None)
    public UserInfo getCurrentUserInfo() {
        UserInfo info = new UserInfo();
        info.setUsername(securityIdentity.getPrincipal().getName());
        info.setDisplayName(securityIdentity.getPrincipal().getName()); // TODO need a better implementation
                                                                        // of this, maybe use claims
                                                                        // first_name and last_name
        if (authConfig.isRbacEnabled()) {
            info.setAdmin(rbac.isAdmin());
            info.setDeveloper(rbac.isDeveloper());
            info.setViewer(rbac.isReadOnly());
        } else {
            info.setAdmin(true);
            info.setDeveloper(false);
            info.setViewer(false);
        }
        if (authConfig.isAdminOverrideEnabled() && adminOverride.isAdmin()) {
            info.setAdmin(true);
        }
        if (securityIdentity.isAnonymous() && authConfig.isAnonymousReadsEnabled()) {
            info.setViewer(true);
        }
        if (!securityIdentity.isAnonymous() && authConfig.isAuthenticatedReadsEnabled()) {
            info.setViewer(true);
        }
        return info;
    }

}
