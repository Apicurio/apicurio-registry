/*
 * Copyright 2023 Red Hat
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

import io.apicurio.common.apps.config.Info;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import java.util.Objects;

@RequestScoped
public class HeaderRoleProvider implements RoleProvider {

    @ConfigProperty(name = "registry.auth.role-source.header.name")
    @Info(category = "auth", description = "Header authorization name", availableSince = "2.4.3.Final")
    String roleHeader;

    @Inject
    AuthConfig authConfig;

    @Inject
    @Context
    HttpServletRequest request;

    @Override
    public boolean isReadOnly() {
        return Objects.equals(request.getHeader(roleHeader), authConfig.readOnlyRole);
    }

    @Override
    public boolean isDeveloper() {
        return Objects.equals(request.getHeader(roleHeader), authConfig.developerRole);
    }

    @Override
    public boolean isAdmin() {
        return Objects.equals(request.getHeader(roleHeader), authConfig.adminRole);
    }
}
