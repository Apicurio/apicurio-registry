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

import javax.inject.Singleton;
import javax.interceptor.InvocationContext;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class OwnerBasedAccessController extends AbstractAccessController {

    /**
     * @see io.apicurio.registry.auth.IAccessController#isAuthorized(javax.interceptor.InvocationContext)
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
