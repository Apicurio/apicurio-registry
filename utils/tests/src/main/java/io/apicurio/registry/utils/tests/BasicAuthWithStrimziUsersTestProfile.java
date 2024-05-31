package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicAuthWithStrimziUsersTestProfile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = new HashMap<>();
        map.put("quarkus.http.auth.basic", "true");
        map.put("apicurio.auth.enabled", "true");
        map.put("apicurio.auth.owner-only-authorization", "true");
        map.put("apicurio.auth.basic-auth-with-strimzi-user.enabled", "true");
        return map;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.emptyList();
    }
}
