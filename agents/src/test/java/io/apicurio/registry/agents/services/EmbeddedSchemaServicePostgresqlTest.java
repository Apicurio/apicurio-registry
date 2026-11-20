package io.apicurio.registry.agents.services;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link EmbeddedSchemaServiceTest} against PostgreSQL.
 */
@QuarkusTest
@TestProfile(PostgresqlTestProfile.class)
public class EmbeddedSchemaServicePostgresqlTest extends EmbeddedSchemaServiceTest {
}
