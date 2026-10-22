package io.apicurio.registry.mcp;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;

/**
 * Test profile for MCP HTTP transport with OIDC and token forwarding.
 */
public class McpHttpAuthTestProfile implements QuarkusTestProfile {

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(HttpAuthTestContainersManager.class));
    }
}
