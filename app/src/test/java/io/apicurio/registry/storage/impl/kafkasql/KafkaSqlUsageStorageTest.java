package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.noprofile.storage.AbstractUsageStorageTest;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.storage.impl.sql.UsageRows;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;

import java.util.List;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(KafkasqlTestProfile.class)
@Typed(KafkaSqlUsageStorageTest.class)
public class KafkaSqlUsageStorageTest extends AbstractUsageStorageTest {

    @Inject
    KafkaSqlRegistryStorage storage;

    @Inject
    HandleFactory handles;

    @Override
    protected RegistryStorage storage() {
        return storage;
    }

    @Override
    protected List<String> rows(long globalId, String clientPrefix) {
        return UsageRows.rows(handles, globalId, clientPrefix);
    }
}
