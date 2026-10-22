/*
 * Copyright 2025 Red Hat
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

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

@ApplicationScoped
public class KubernetesRoleProvider implements RoleProvider {

    @Inject
    AuthConfig authConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Override
    public boolean isAdmin() {
        return hasAnyGroup(authConfig.getKubernetesAdminGroups());
    }

    @Override
    public boolean isDeveloper() {
        return hasAnyGroup(authConfig.getKubernetesDeveloperGroups());
    }

    @Override
    public boolean isReadOnly() {
        return hasAnyGroup(authConfig.getKubernetesReadOnlyGroups());
    }

    private boolean hasAnyGroup(Set<String> configuredGroups) {
        if (configuredGroups.isEmpty()) {
            return false;
        }
        Set<String> userRoles = securityIdentity.getRoles();
        for (String group : configuredGroups) {
            if (userRoles.contains(group)) {
                return true;
            }
        }
        return false;
    }
}
