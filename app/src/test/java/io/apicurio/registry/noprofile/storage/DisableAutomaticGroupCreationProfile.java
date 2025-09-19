package io.apicurio.registry.noprofile.storage;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class DisableAutomaticGroupCreationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.storage.enable-automatic-group-creation", "false");
        return props;
    }
}
