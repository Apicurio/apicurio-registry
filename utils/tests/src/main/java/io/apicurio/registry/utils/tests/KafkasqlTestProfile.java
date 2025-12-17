package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test profile for KafkaSQL storage tests.
 * Uses KafkaTestContainerManager to start a Kafka container.
 * Note: Kafka DevServices requires quarkus-kafka-client extension which this
 * project doesn't use (uses plain kafka-clients for custom configuration).
 */
public class KafkasqlTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("apicurio.storage.kind", "kafkasql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(KafkaTestContainerManager.class));
        } else {
            return Collections.emptyList();
        }
    }

}
