/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.auth;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.interceptor.InvocationContext;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class RoleBasedAccessController extends AbstractAccessController {

    @Inject
    StorageRoleProvider storageRoleProvider;

    @Inject
    TokenRoleProvider tokenRoleProvider;

    /**
     * @see io.apicurio.registry.auth.IAccessController#isAuthorized(javax.interceptor.InvocationContext)
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
        } else {
            throw new RuntimeException("Unsupported RBAC role source: " + authConfig.roleSource);
        }
    }

}
