package io.apicurio.tests.debezium;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Test profile for Debezium PostgreSQL CDC integration tests using locally built converters.
 * Configures Apicurio Registry with PostgreSQL storage and enables deletion capabilities
 * for test cleanup. Uses locally built Apicurio converter SNAPSHOT libraries instead of
 * downloading them from Maven Central, ensuring tests validate the current codebase.
 */
public class DebeziumPostgreSQLLocalConvertersTestProfile implements QuarkusTestProfile {

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
        // Use the DebeziumLocalConvertersResource which manages:
        // - Kafka container
        // - PostgreSQL container
        // - Debezium Connect container with LOCALLY BUILT Apicurio converters
        return List.of(new TestResourceEntry(DebeziumLocalConvertersResource.class));
    }

    @Override
    public String[] commandLineParameters() {
        return new String[]{};
    }
}
