package io.apicurio.registry.agents.noprofile.references;

import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class AgentReferencesKafkaSqlTest extends AgentReferencesTest {
}
