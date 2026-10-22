package io.apicurio.registry.auth;

import io.apicurio.common.apps.config.Info;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@RequestScoped
public class HeaderRoleProvider implements RoleProvider {

    @ConfigProperty(name = "apicurio.auth.role-source.header.name")
    @Info(category = CATEGORY_AUTH, description = "Header authorization name", availableSince = "2.4.3.Final")
    String roleHeader;

    @Inject
    AuthConfig authConfig;

    @Inject
    @Context
    HttpServletRequest request;

    /**
     * Checks if the header value matches any of the configured roles.
     * This supports multiple role mappings (e.g., Azure AD groups and app roles).
     */
    @Override
    public boolean isReadOnly() {
        String headerValue = request.getHeader(roleHeader);
        return headerValue != null && authConfig.getReadOnlyRoles().contains(headerValue);
    }

    /**
     * Checks if the header value matches any of the configured developer roles.
     * This supports multiple role mappings (e.g., Azure AD groups and app roles).
     */
    @Override
    public boolean isDeveloper() {
        String headerValue = request.getHeader(roleHeader);
        return headerValue != null && authConfig.getDeveloperRoles().contains(headerValue);
    }

    /**
     * Checks if the header value matches any of the configured admin roles.
     * This supports multiple role mappings (e.g., Azure AD groups and app roles).
     */
    @Override
    public boolean isAdmin() {
        String headerValue = request.getHeader(roleHeader);
        return headerValue != null && authConfig.getAdminRoles().contains(headerValue);
    }
}
