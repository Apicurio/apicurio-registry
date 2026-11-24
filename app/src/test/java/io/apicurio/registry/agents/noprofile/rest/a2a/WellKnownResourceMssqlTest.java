package io.apicurio.registry.agents.noprofile.rest.a2a;

import io.apicurio.registry.agents.AgentFeaturesMssqlProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the A2A discovery tests against SQL Server.
 */
@QuarkusTest
@TestProfile(AgentFeaturesMssqlProfile.class)
public class WellKnownResourceMssqlTest extends WellKnownResourceTest {
}
