package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.MysqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(MysqlTestProfile.class)
@Typed(MysqlStorageTest.class)
@DisabledOnOs(value = OS.MAC)
public class MysqlStorageTest extends DefaultRegistryStorageTest {
}
