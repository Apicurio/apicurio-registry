package io.apicurio.registry.auth;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OidcAuthenticationStrategyTest {

    @Test
    void testOidcTokenUrlConstruction() throws Exception {
        // Case 1: Relative Path
        AuthConfig config1 = new AuthConfig();
        config1.authServerUrl = "http://keycloak/auth";
        config1.oidcTokenPath = "/token";
        OidcAuthenticationStrategy strategy1 = new OidcAuthenticationStrategy(null, config1, null, null, null, null);
        assertEquals("http://keycloak/auth/token", getOidcTokenUrl(strategy1));

        // Case 2: Relative Path with Trailing Slash
        AuthConfig config2 = new AuthConfig();
        config2.authServerUrl = "http://keycloak/auth";
        config2.oidcTokenPath = "/token/";
        OidcAuthenticationStrategy strategy2 = new OidcAuthenticationStrategy(null, config2, null, null, null, null);
        assertEquals("http://keycloak/auth/token", getOidcTokenUrl(strategy2));

        // Case 3: Absolute URL
        AuthConfig config3 = new AuthConfig();
        config3.authServerUrl = "http://keycloak/auth"; // Should be ignored
        config3.oidcTokenPath = "http://authentik/token";
        OidcAuthenticationStrategy strategy3 = new OidcAuthenticationStrategy(null, config3, null, null, null, null);
        assertEquals("http://authentik/token", getOidcTokenUrl(strategy3));

        // Case 4: Absolute URL with Trailing Slash
        AuthConfig config4 = new AuthConfig();
        config4.authServerUrl = "http://keycloak/auth"; // Should be ignored
        config4.oidcTokenPath = "http://authentik/token/";
        OidcAuthenticationStrategy strategy4 = new OidcAuthenticationStrategy(null, config4, null, null, null, null);
        assertEquals("http://authentik/token/", getOidcTokenUrl(strategy4));
    }

    private String getOidcTokenUrl(OidcAuthenticationStrategy strategy) throws Exception {
        Field field = OidcAuthenticationStrategy.class.getDeclaredField("oidcTokenUrl");
        field.setAccessible(true);
        return (String) field.get(strategy);
    }
}
