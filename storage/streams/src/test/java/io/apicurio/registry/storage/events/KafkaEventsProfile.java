package io.apicurio.registry.storage.events;

import java.util.Collections;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class KafkaEventsProfile implements QuarkusTestProfile {

    public static final String EVENTS_TOPIC = "registry-events";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("registry.events.kafka.topic", EVENTS_TOPIC);
    }

}