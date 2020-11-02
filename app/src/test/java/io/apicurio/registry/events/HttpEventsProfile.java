package io.apicurio.registry.events;

import java.util.Collections;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class HttpEventsProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.singletonMap("registry.events.sink.testsink", "http://localhost:8888/");
    }

}