package io.apicurio.registry.auth;

import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;

/**
 * This class implements authorization logic for the registry.  It is driven by a combination of the
 * security identity (authenticated user) and configured security level of the operation the user is
 * attempting to perform. This interceptor will be triggered
 * for any method that is annotated with the {@link Authorized} annotation. Please ensure that all
 * JAX-RS operations are propertly annotated.
 *
 */
@Authorized
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuthorizedInterceptor {

    @Inject
    Logger log;

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    Instance<JsonWebToken> jsonWebToken;

    @Inject
    AdminOverride adminOverride;

    @Inject
    RoleBasedAccessController rbac;

    @Inject
    OwnerBasedAccessController obac;

    @AroundInvoke
    public Object authorizeMethod(InvocationContext context) throws Exception {

        // If the user is trying to invoke a role-mapping operation, deny it if
        // database based RBAC is not enabled.
        RoleBasedAccessApiOperation rbacOpAnnotation = context.getMethod().getAnnotation(RoleBasedAccessApiOperation.class);
        if (rbacOpAnnotation != null) {
            if (!authConfig.isApplicationRbacEnabled()) {
                log.warn("Access to /admin/roleMappings denied because application managed RBAC is not enabled.");
                throw new ForbiddenException("Application RBAC not enabled.");
            }
        }

        // If authentication is not enabled, just do it.
        if (!authConfig.authenticationEnabled) {
            return context.proceed();
        }

        log.trace("Authentication enabled, protected resource: " + context.getMethod());

        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);

        // If the securityIdentity is not set (or is anonymous)...
        try {
            if (securityIdentity == null || securityIdentity.isAnonymous()) {
                System.out.println("=====> Identity was null or anon: " + securityIdentity);
    
                // Anonymous users are allowed to perform "None" operations.
                if (annotation.level() == AuthorizedLevel.None) {
                    log.trace("Anonymous user is being granted access to unprotected operation.");
                    return context.proceed();
                }
    
                // Anonymous users are allowed to perform read-only operations, but only if
                // apicurio.auth.anonymous-read-access.enabled is set to 'true'
                if (authConfig.anonymousReadAccessEnabled.get() && annotation.level() == AuthorizedLevel.Read) {
                    log.trace("Anonymous user is being granted access to read-only operation.");
                    return context.proceed();
                }
    
                // Otherwise just fail - auth was enabled but no credentials provided.
                log.warn("Authentication credentials missing and required for protected endpoint.");
                throw new UnauthorizedException("User is not authenticated.");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }

        log.info("principalId:" + securityIdentity.getPrincipal().getName());
        log.info("roles:" + securityIdentity.getRoles());

        // If the user is authenticated and the operation auth level is None, allow it
        if (annotation.level() == AuthorizedLevel.None) {
            return context.proceed();
        }

        // If the user is an admin (via the admin-override check) then there's no need to
        // check rbac or obac.
        if (adminOverride.isAdmin()) {
            log.trace("Admin override successful.");
            return context.proceed();
        }

        // If Authenticated read access is enabled, and the operation auth level is Read, allow it.
        if (authConfig.authenticatedReadAccessEnabled.get() && annotation.level() == AuthorizedLevel.Read) {
            return context.proceed();
        }

        // If RBAC is enabled, apply role based rules
        if (authConfig.roleBasedAuthorizationEnabled && !rbac.isAuthorized(context)) {
            log.warn("RBAC enabled and required role missing.");
            throw new ForbiddenException("User " + securityIdentity.getPrincipal().getName() + " is not authorized to perform the requested operation.");
        }

        // If Owner-only is enabled, apply ownership rules
        if (authConfig.ownerOnlyAuthorizationEnabled.get() && !obac.isAuthorized(context)) {
            log.warn("OBAC enabled and operation not permitted due to wrong owner.");
            throw new ForbiddenException("User " + securityIdentity.getPrincipal().getName() + " is not authorized to perform the requested operation.");
        }

        return context.proceed();
    }
}
