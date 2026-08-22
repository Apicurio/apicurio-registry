package io.apicurio.registry.noprofile.rest.v3;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class SystemResourceLogoutUrlUnsetTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.oidc.tenant-enabled", "true",
                "quarkus.oidc.auth-server-url", "https://example.com/realms/test",
                "apicurio.ui.auth.oidc.client-id", "test-client"
        );
    }
}