package io.apicurio.registry;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class MigrationTest extends AbstractResourceTestBase {


    @Test
    public void migrateData() throws Exception {

        InputStream originalData = getClass().getResource("rest/v3/destination_original_data.zip").openStream();
        InputStream migratedData = getClass().getResource("rest/v3/migration_test_data_dump.zip").openStream();

        clientV3.admin().importEscaped().post(originalData, config -> {
            // TODO: this header should be injected by Kiota
            config.headers.add("Content-Type", "application/zip");
        }).get(10, TimeUnit.SECONDS);
        clientV3.admin().importEscaped().post(migratedData, config -> {
            // TODO: this header should be injected by Kiota
            config.headers.add("Content-Type", "application/zip");
            config.headers.add("X-Registry-Preserve-GlobalId", "false");
            config.headers.add("X-Registry-Preserve-ContentId", "false");
        }).get(40, TimeUnit.SECONDS);
    }
}
