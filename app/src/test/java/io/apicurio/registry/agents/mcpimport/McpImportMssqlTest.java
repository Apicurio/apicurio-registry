package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.agents.AgentFeaturesMssqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the MCP tool import tests against SQL Server.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMssqlProfile.class)
public class McpImportMssqlTest extends McpImportTest {
}
