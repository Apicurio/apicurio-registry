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
public class RoleBasedAccessController implements IAccessController {

    @Inject
    AuthConfig authConfig;

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
                return getRoleProvider().isAdmin();
            case None:
                return true;
            case Read:
                return getRoleProvider().isReadOnly() || getRoleProvider().isDeveloper() || getRoleProvider().isAdmin();
            case Write:
                return getRoleProvider().isDeveloper() || getRoleProvider().isAdmin();
            default:
                throw new RuntimeException("Unhandled case: " + level);
        }
    }

    private RoleProvider getRoleProvider() {
        if ("token".equals(authConfig.roleSource)) {
            return tokenRoleProvider;
        } else {
            return storageRoleProvider;
        }
    }

}
