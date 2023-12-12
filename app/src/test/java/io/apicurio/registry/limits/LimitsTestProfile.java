package io.apicurio.registry.limits;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class LimitsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("registry.limits.config.max-total-schemas", "2");
        props.put("registry.limits.config.max-artifact-properties", "2");
        props.put("registry.limits.config.max-property-key-size", "4"); // use text test
        props.put("registry.limits.config.max-property-value-size", "4");
        props.put("registry.limits.config.max-artifact-labels", "2");
        props.put("registry.limits.config.max-label-size", "4");
        props.put("registry.limits.config.max-name-length", "512");
        props.put("registry.limits.config.max-description-length", "1024");

        // this will do nothing, no server will be available, it's just to test the usage of two decorators at
        // the same time
        props.put("registry.events.sink.testsink", "http://localhost:8888/thisisfailingonpurpose");

        return props;
    }

}