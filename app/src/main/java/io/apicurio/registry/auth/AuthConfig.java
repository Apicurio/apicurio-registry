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

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class AuthConfig {

    @ConfigProperty(name = "registry.auth.enabled", defaultValue = "false")
    public boolean authenticationEnabled;

    @ConfigProperty(name = "registry.auth.role-based-authorization", defaultValue = "false")
    public boolean roleBasedAuthorizationEnabled;

    @ConfigProperty(name = "registry.auth.owner-only-authorization", defaultValue = "false")
    public boolean ownerOnlyAuthorizationEnabled;

    @ConfigProperty(name = "registry.auth.roles.readonly", defaultValue = "sr-readonly")
    public String readOnlyRole;

    @ConfigProperty(name = "registry.auth.roles.developer", defaultValue = "sr-developer")
    public String developerRole;

    @ConfigProperty(name = "registry.auth.roles.admin", defaultValue = "sr-admin")
    public String adminRole;

    @ConfigProperty(name = "registry.auth.role-source", defaultValue = "token")
    public String roleSource;

    @ConfigProperty(name = "registry.auth.admin-override.enabled", defaultValue = "false")
    public boolean adminOverrideEnabled;

    @ConfigProperty(name = "registry.auth.admin-override.from", defaultValue = "token")
    public String adminOverrideFrom;

    @ConfigProperty(name = "registry.auth.admin-override.type", defaultValue = "role")
    public String adminOverrideType;

    @ConfigProperty(name = "registry.auth.admin-override.role", defaultValue = "sr-admin")
    public String adminOverrideRole;

    @ConfigProperty(name = "registry.auth.admin-override.claim", defaultValue = "org-admin")
    public String adminOverrideClaim;

    @ConfigProperty(name = "registry.auth.admin-override.claim-value", defaultValue = "true")
    public String adminOverrideClaimValue;

}
