package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KafkasqlRecoverFromSnapshotTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.storage.kind", "kafkasql", "apicurio.datasource.url", "jdbc:h2:mem:" + UUID.randomUUID());
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(
                    new TestResourceEntry(KafkaTestContainerManager.class));
        }
        else {
            return Collections.emptyList();
        }
    }

}
