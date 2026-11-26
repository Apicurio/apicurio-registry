package io.apicurio.registry.agents.noprofile.references;

import io.apicurio.registry.agents.AgentFeaturesMssqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the agent reference tests against SQL Server.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMssqlProfile.class)
public class AgentReferencesMssqlTest extends AgentReferencesTest {
}
