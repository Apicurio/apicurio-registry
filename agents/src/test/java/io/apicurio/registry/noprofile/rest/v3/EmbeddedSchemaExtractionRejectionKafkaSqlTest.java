package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaExtractionRejectionTest} against KafkaSQL.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class EmbeddedSchemaExtractionRejectionKafkaSqlTest extends EmbeddedSchemaExtractionRejectionTest {
}
