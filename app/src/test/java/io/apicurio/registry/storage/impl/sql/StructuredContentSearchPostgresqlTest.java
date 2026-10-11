package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(PostgresqlTestProfile.class)
class StructuredContentSearchPostgresqlTest extends StructuredContentSearchTest {
}
