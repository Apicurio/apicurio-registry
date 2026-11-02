package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link AuthorizationFilterStorageTest} against PostgreSQL. The escaping, the
 * {@code ESCAPE '!'} clause, and collation-dependent matching are exactly what differs between
 * database variants (see {@link io.apicurio.registry.storage.impl.sql.repositories.SqlAuthorizationFilter}).
 */
@QuarkusTest
@TestProfile(PostgresqlTestProfile.class)
class AuthorizationFilterStoragePostgresqlTest extends AuthorizationFilterStorageTest {
}
