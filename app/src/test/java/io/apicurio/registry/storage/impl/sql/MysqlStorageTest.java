package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(MysqlTestProfile.class)
@Typed(MysqlStorageTest.class)
public class MysqlStorageTest extends DefaultRegistryStorageTest {
}
