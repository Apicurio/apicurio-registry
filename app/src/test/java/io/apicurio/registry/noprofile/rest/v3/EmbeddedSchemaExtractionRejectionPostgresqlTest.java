package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaExtractionRejectionTest} against PostgreSQL.
 */
@QuarkusTest
@TestProfile(PostgresqlTestProfile.class)
public class EmbeddedSchemaExtractionRejectionPostgresqlTest extends EmbeddedSchemaExtractionRejectionTest {
}
