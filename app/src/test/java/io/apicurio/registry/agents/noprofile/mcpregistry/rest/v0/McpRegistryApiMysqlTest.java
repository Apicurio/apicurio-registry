package io.apicurio.registry.agents.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.agents.AgentFeaturesMysqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the MCP Registry API tests against MySQL.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMysqlProfile.class)
public class McpRegistryApiMysqlTest extends McpRegistryApiTest {
}
