package io.apicurio.registry.events;

import java.util.Collections;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class KafkaEventsTestResource implements QuarkusTestResourceLifecycleManager {

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#start()
     */
    @Override
    public Map<String, String> start() {
        return Collections.singletonMap("registry.events.kafka.topic", KafkaEventsProfile.EVENTS_TOPIC);
    }

    /**
     * @see io.quarkus.test.common.QuarkusTestResourceLifecycleManager#stop()
     */
    @Override
    public void stop() {
    }

}
