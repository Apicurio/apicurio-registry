package io.apicurio.registry.agents.noprofile.rest.mcptools;

import io.apicurio.registry.agents.AgentFeaturesMysqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the MCP tool discovery tests against MySQL.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMysqlProfile.class)
public class WellKnownMcpToolsMysqlTest extends WellKnownMcpToolsTest {
}
