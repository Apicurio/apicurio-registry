package io.apicurio.registry.agents.noprofile.references;

import io.apicurio.registry.agents.AgentFeaturesMysqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the agent reference tests against MySQL.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMysqlProfile.class)
public class AgentReferencesMysqlTest extends AgentReferencesTest {
}
