package io.apicurio.registry.auth;

import jakarta.inject.Singleton;
import jakarta.interceptor.InvocationContext;

@Singleton
public class OwnerBasedAccessController extends AbstractAccessController {

    /**
     * @see io.apicurio.registry.auth.IAccessController#isAuthorized(jakarta.interceptor.InvocationContext)
     */
    @Override
    public boolean isAuthorized(InvocationContext context) {
        Authorized annotation = context.getMethod().getAnnotation(Authorized.class);
        AuthorizedLevel level = annotation.level();

        // Only protect level == Write operations
        if (level != AuthorizedLevel.Write) {
            return true;
        }

        return isOwner(context);
    }

}
