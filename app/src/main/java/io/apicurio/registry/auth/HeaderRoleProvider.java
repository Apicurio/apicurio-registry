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
