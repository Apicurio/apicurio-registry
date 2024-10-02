package io.apicurio.registry.event.kafkasql;

import io.apicurio.registry.utils.tests.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

public class KafkaSqlEventsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.events.enabled", "true", "apicurio.storage.kind", "kafkasql",
                "apicurio.rest.deletion.artifact.enabled", "true",
                "apicurio.rest.deletion.artifact-version.enabled", "true");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(KafkaTestContainerManager.class));
    }
}