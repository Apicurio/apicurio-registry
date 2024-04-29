package io.apicurio.registry.storage.util;

import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class KafkasqlTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("apicurio.storage.kind", "kafkasql");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(
                    new TestResourceEntry(KafkaTestContainerManager.class));
        } else {
            return Collections.emptyList();
        }
    }

}
