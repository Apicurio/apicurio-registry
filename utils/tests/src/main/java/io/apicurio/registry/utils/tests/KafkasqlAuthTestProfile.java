package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class KafkasqlAuthTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.storage.kind", "kafkasql", "apicurio.rest.deletion.group.enabled", "true",
                "apicurio.rest.deletion.artifact.enabled", "true",
                "apicurio.rest.deletion.artifact-version.enabled", "true");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(KafkaTestContainerManager.class),
                    new TestResourceEntry(KeycloakTestContainerManager.class));
        } else {
            return Collections.emptyList();
        }
    }

}
