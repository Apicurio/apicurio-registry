package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaExtractionRejectionTest} against MySQL.
 */
@QuarkusTest
@TestProfile(MysqlTestProfile.class)
public class EmbeddedSchemaExtractionRejectionMysqlTest extends EmbeddedSchemaExtractionRejectionTest {
}
