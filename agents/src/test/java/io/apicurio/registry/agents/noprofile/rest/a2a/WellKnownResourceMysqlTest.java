package io.apicurio.registry.agents.noprofile.rest.a2a;

import io.apicurio.registry.agents.AgentFeaturesMysqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the A2A discovery tests against MySQL.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMysqlProfile.class)
public class WellKnownResourceMysqlTest extends WellKnownResourceTest {
}
