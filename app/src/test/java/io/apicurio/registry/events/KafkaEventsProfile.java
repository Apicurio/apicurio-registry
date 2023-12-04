package io.apicurio.registry.events;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTestProfile;


public class KafkaEventsProfile implements QuarkusTestProfile {

    public static final String EVENTS_TOPIC = "registry-events";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.emptyMap();
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Arrays.asList(
                new TestResourceEntry(KafkaEventsTestResource.class),
                new TestResourceEntry(KafkaTestContainerManager.class));
    }

}
