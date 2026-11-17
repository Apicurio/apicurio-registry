package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs {@link AuthorizationFilterStorageTest} against MySQL, whose default collations are
 * case-insensitive: per-resource search must still match names case-sensitively, like point access.
 */
@QuarkusTest
@TestProfile(MysqlTestProfile.class)
class AuthorizationFilterStorageMysqlTest extends AuthorizationFilterStorageTest {
}
