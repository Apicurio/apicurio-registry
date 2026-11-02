package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.noprofile.storage.AbstractUsageStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@Typed(DefaultUsageStorageTest.class)
public class DefaultUsageStorageTest extends AbstractUsageStorageTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Override
    protected RegistryStorage storage() {
        return storage;
    }
}
