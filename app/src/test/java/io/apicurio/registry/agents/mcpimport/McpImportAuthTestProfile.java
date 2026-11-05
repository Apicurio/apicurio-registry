package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.utils.tests.AuthTestProfile;

import java.util.Map;

/**
 * Keycloak-backed authentication and role-based authorization, with MCP import enabled as in
 * {@link McpImportTestProfile}.
 */
public class McpImportAuthTestProfile extends AuthTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = super.getConfigOverrides();
        props.putAll(McpImportTestProfile.importOverrides());
        return props;
    }
}
