package io.apicurio.registry.agents.noprofile.rest.mcptools;

import io.apicurio.registry.agents.AgentFeaturesMssqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the MCP tool discovery tests against SQL Server.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMssqlProfile.class)
public class WellKnownMcpToolsMssqlTest extends WellKnownMcpToolsTest {
}
