package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.AgentFeaturesMysqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the MCP tool import tests against MySQL.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMysqlProfile.class)
public class McpImportMysqlTest extends McpImportTest {
}
