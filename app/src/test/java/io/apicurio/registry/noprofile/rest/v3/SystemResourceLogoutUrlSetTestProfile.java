package io.apicurio.registry.noprofile.rest.v3;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

public class SystemResourceLogoutUrlSetTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.oidc.tenant-enabled", "true",
                "quarkus.oidc.discovery-enabled", "false",
                "quarkus.oidc.auth-server-url", "http://localhost:1/realms/test",
                "quarkus.oidc.jwks-path", "/protocol/openid-connect/certs",
                "apicurio.ui.auth.oidc.client-id", "test-client",
                "apicurio.ui.auth.oidc.logout-url", "https://example.com/logout"
        );
    }
}
