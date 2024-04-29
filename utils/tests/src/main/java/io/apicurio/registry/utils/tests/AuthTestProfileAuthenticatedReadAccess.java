package io.apicurio.registry.utils.tests;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AuthTestProfileAuthenticatedReadAccess implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("apicurio.auth.authenticated-read-access.enabled", "true", "smallrye.jwt.sign.key.location", "privateKey.jwk");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.singletonList(
                new TestResourceEntry(JWKSMockServer.class));
    }
}
