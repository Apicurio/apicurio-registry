package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AuthTestProfileWithLocalRoles implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("smallrye.jwt.sign.key.location", "privateKey.jwk", "registry.auth.role-source",
                "application");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.singletonList(new TestResourceEntry(JWKSMockServer.class));
    }
}