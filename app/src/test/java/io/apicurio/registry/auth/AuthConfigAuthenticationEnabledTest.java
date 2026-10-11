package io.apicurio.registry.auth;

import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuthConfig#isAuthenticationEnabled()} is the single switch the authorization interceptor and the
 * Agent Card visibility filter use to decide whether access rules apply at all, so every authentication
 * mechanism must turn it on.
 */
class AuthConfigAuthenticationEnabledTest {

    @Test
    void testDisabledWhenNoMechanismIsEnabled() {
        assertFalse(new AuthConfig().isAuthenticationEnabled());
    }

    @Test
    void testOidc() {
        assertEnabledWith(c -> c.oidcAuthEnabled = true);
    }

    @Test
    void testBasic() {
        assertEnabledWith(c -> c.basicAuthEnabled = true);
    }

    @Test
    void testProxyHeader() {
        assertEnabledWith(c -> c.proxyHeaderAuthEnabled = true);
    }

    @Test
    void testKubernetes() {
        assertEnabledWith(c -> c.kubernetesAuthEnabled = true);
    }

    @Test
    void testForm() {
        assertEnabledWith(c -> c.formAuthEnabled = true);
    }

    private static void assertEnabledWith(Consumer<AuthConfig> mechanism) {
        AuthConfig config = new AuthConfig();
        mechanism.accept(config);
        assertTrue(config.isAuthenticationEnabled());
    }
}
