package io.apicurio.registry.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile for testing multi-role value configuration.
 * Configures comma-separated role values to verify multi-role parsing.
 */
public class MultiRoleAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            // Configure multiple role values (comma-separated)
            "apicurio.auth.roles.admin", "sr-admin,azure-admin-group,AppRole.Admin",
            "apicurio.auth.roles.developer", "sr-developer,azure-dev-group,AppRole.Developer",
            "apicurio.auth.roles.readonly", "sr-readonly,azure-readonly-group,AppRole.Reader",
            "apicurio.auth.admin-override.role", "sr-admin,azure-admin-group",
            // Configure client-specific scopes
            "apicurio.authn.basic.scope", "api://default/.default",
            "apicurio.authn.basic.scope.test-client-1", "api://app1/.default",
            "apicurio.authn.basic.scope.test-client-2", "api://app2/.default,api://shared/.default"
        );
    }

    @Override
    public String getConfigProfile() {
        return "test";
    }
}
