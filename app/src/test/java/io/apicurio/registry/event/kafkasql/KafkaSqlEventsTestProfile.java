package io.apicurio.registry.event.kafkasql;

import io.apicurio.registry.utils.tests.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * Test profile for KafkaSQL event streaming tests.
 * Uses KafkaTestContainerManager to start a Kafka container.
 * Note: Kafka DevServices requires quarkus-kafka-client extension which this
 * project doesn't use (uses plain kafka-clients for custom configuration).
 */
public class KafkaSqlEventsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.storage.kind", "kafkasql", "apicurio.rest.deletion.artifact.enabled", "true",
                "apicurio.rest.deletion.artifact-version.enabled", "true",
                "apicurio.rest.deletion.group.enabled", "true");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(KafkaTestContainerManager.class));
    }
}