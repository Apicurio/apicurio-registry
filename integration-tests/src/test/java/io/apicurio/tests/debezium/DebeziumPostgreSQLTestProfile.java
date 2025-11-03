package io.apicurio.tests.debezium;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Test profile for Debezium PostgreSQL CDC integration tests.
 * Configures Apicurio Registry with PostgreSQL storage and enables
 * deletion capabilities for test cleanup.
 */
public class DebeziumPostgreSQLTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                // Use PostgreSQL storage
                "apicurio.storage.sql.kind", "postgresql",

                // Enable deletion for test cleanup
                "apicurio.rest.deletion.artifact.enabled", "true",
                "apicurio.rest.deletion.artifact-version.enabled", "true",
                "apicurio.rest.deletion.group.enabled", "true",

                // Optimize for testing
                "apicurio.ccompat.legacy-id-mode.enabled", "true"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        // Use the DebeziumContainerResource which manages:
        // - Kafka container
        // - PostgreSQL container
        // - Debezium Connect container with Apicurio converters enabled
        return List.of(new TestResourceEntry(DebeziumContainerResource.class));
    }

    @Override
    public String[] commandLineParameters() {
        return new String[]{};
    }
}
