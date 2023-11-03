package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.util.MssqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(MssqlTestProfile.class)
@Typed(MssqlStorageTest.class)
public class MssqlStorageTest extends DefaultRegistryStorageTest {
}
