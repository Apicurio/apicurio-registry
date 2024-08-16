package io.apicurio.registry;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

@QuarkusTest
public class MigrationTest extends AbstractResourceTestBase {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void migrateData() throws Exception {
        storage.deleteAllUserData();

        InputStream originalData = getClass().getResource("rest/v3/destination_original_data.zip")
                .openStream();
        InputStream migratedData = getClass().getResource("rest/v3/migration_test_data_dump.zip")
                .openStream();

        clientV3.admin().importEscaped().post(originalData, config -> {
            // TODO: this header should be injected by Kiota
            config.headers.add("Content-Type", "application/zip");
        });
        clientV3.admin().importEscaped().post(migratedData, config -> {
            // TODO: this header should be injected by Kiota
            config.headers.add("Content-Type", "application/zip");
            config.headers.add("X-Registry-Preserve-GlobalId", "false");
            config.headers.add("X-Registry-Preserve-ContentId", "false");
            config.queryParameters.requireEmptyRegistry = false;
        });
    }
}
