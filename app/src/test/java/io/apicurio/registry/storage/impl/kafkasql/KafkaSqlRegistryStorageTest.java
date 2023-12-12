package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.noprofile.storage.AbstractRegistryStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.util.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Typed(KafkaSqlRegistryStorageTest.class)
public class KafkaSqlRegistryStorageTest extends AbstractRegistryStorageTest {

    @Inject
    KafkaSqlRegistryStorage storage;

    /**
     * @see AbstractRegistryStorageTest#storage()
     */
    @Override
    protected RegistryStorage storage() {
        return storage;
    }

}
