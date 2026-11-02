package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.noprofile.storage.AbstractUsageStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(KafkasqlTestProfile.class)
@Typed(KafkaSqlUsageStorageTest.class)
public class KafkaSqlUsageStorageTest extends AbstractUsageStorageTest {

    @Inject
    KafkaSqlRegistryStorage storage;

    @Override
    protected RegistryStorage storage() {
        return storage;
    }
}
