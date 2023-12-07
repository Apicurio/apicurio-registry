package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import jakarta.inject.Inject;

import io.apicurio.registry.noprofile.storage.AbstractRegistryStorageTest;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class DefaultRegistryStorageTest extends AbstractRegistryStorageTest {

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * @see io.apicurio.registry.storage.AbstractRegistryStorageTest#storage()
     */
    @Override
    protected RegistryStorage storage() {
        return storage;
    }

}