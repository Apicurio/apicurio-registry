package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;

/**
 * Test profile for MCP basic authentication tests.
 * This profile starts Keycloak and Registry containers using testcontainers,
 * and configures the MCP server to use basic authentication.
 */
public class McpBasicAuthTestProfile implements QuarkusTestProfile {

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(BasicAuthTestContainersManager.class));
    }
}
