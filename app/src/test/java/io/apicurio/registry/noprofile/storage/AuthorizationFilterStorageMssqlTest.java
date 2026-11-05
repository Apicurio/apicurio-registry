package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.storage.util.MssqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link AuthorizationFilterStorageTest} against SQL Server, whose default collations are
 * case-insensitive: per-resource search must still match names case-sensitively, like point access.
 */
@QuarkusTest
@TestProfile(MssqlTestProfile.class)
class AuthorizationFilterStorageMssqlTest extends AuthorizationFilterStorageTest {
}
