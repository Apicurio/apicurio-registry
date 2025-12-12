package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;

/**
 * Test profile for MCP authentication tests.
 * This profile starts Keycloak and Registry containers using testcontainers.
 */
public class McpAuthTestProfile implements QuarkusTestProfile {

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(AuthTestContainersManager.class));
    }
}
