package io.apicurio.registry.auth;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;

@Singleton
public class RoleBasedAccessController extends AbstractAccessController {

    @Inject
    StorageRoleProvider storageRoleProvider;

    @Inject
    TokenRoleProvider tokenRoleProvider;

    @Inject
    HeaderRoleProvider headerRoleProvider;

    /**
     * @see io.apicurio.registry.auth.IAccessController#isAuthorized(jakarta.interceptor.InvocationContext)
     */
    @Override
    public boolean isAuthorized(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedLevel level = annotation.level();

        switch (level) {
            case Admin:
                return isAdmin();
            case None:
                return true;
            case Read:
                return isReadOnly() || isDeveloper() || isAdmin();
            case Write:
                return isDeveloper() || isAdmin();
            case AdminOrOwner:
                return isAdmin() || isOwner(context);
            default:
                throw new RuntimeException("Unhandled case: " + level);
        }
    }

    public boolean isAdmin() {
        return getRoleProvider().isAdmin();
    }

    public boolean isDeveloper() {
        return getRoleProvider().isDeveloper();
    }

    public boolean isReadOnly() {
        return getRoleProvider().isReadOnly();
    }

    private RoleProvider getRoleProvider() {
        if ("token".equals(authConfig.roleSource)) {
            return tokenRoleProvider;
        } else if ("application".equals(authConfig.roleSource)) {
            return storageRoleProvider;
        } else if ("header".equals(authConfig.roleSource)) {
            return headerRoleProvider;
        } else {
            throw new RuntimeException("Unsupported RBAC role source: " + authConfig.roleSource);
        }
    }

}
