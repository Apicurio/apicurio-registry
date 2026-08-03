package io.apicurio.registry.limits;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class RegistryLimitsNullLabelValueTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.limits.config.max-property-value-size.bytes", "100");
        return props;
    }

}
