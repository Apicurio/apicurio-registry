package io.apicurio.registry.agents.services;

import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaServiceTest} against KafkaSQL.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class EmbeddedSchemaServiceKafkaSqlTest extends EmbeddedSchemaServiceTest {
}
