package io.apicurio.registry.agents.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.agents.AgentFeaturesMssqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the MCP Registry API tests against SQL Server.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMssqlProfile.class)
public class McpRegistryApiMssqlTest extends McpRegistryApiTest {
}
