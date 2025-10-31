package io.apicurio.tests.debezium;

import io.apicurio.registry.utils.tests.DebeziumContainerResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Test profile for Debezium integration tests.
 * Configures DebeziumContainerResource which provides:
 * - Kafka (KRaft mode)
 * - PostgreSQL with Debezium
 * - MySQL with Debezium
 * - Debezium Connect
 */
public class DebeziumIntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
            "apicurio.storage.sql.kind", "postgresql",
            "apicurio.rest.deletion.artifact.enabled", "true",
            "apicurio.rest.deletion.artifact-version.enabled", "true",
            "apicurio.rest.deletion.group.enabled", "true"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(DebeziumContainerResource.class));
    }
}
