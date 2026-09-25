package io.apicurio.registry.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the REAL MicroProfile Fault Tolerance {@code @Retry} on
 * {@link AppAuthenticationMechanism#getAccessToken} through the CDI proxy.
 * Proves the interceptor is actually wired (a classic MP-FT footgun is an
 * annotation on a non-CDI class or invoked via {@code this.} that silently
 * never applies).
 *
 * <p>Uses a deliberately unreachable URL so every attempt throws
 * {@link OidcAuthException}. With {@code maxRetries = 2} (overridden via
 * MP-FT config) and 50 ms delay, the method should be invoked 3 times total
 * (1 original + 2 retries) and take at least 100 ms.
 */
@QuarkusTest
@TestProfile(OidcRetryQuarkusTest.ShortRetryProfile.class)
class OidcRetryQuarkusTest {

    /**
     * Shortens retry delay and count so the test runs quickly while still
     * proving the interceptor fires. The config keys follow the standard
     * MP-FT override pattern documented in the MicroProfile Fault Tolerance
     * specification (section 5.1).
     */
    public static class ShortRetryProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "io.apicurio.registry.auth.AppAuthenticationMechanism"
                            + "/getAccessToken/Retry/maxRetries", "2",
                    "io.apicurio.registry.auth.AppAuthenticationMechanism"
                            + "/getAccessToken/Retry/delay", "50",
                    "io.apicurio.registry.auth.AppAuthenticationMechanism"
                            + "/getAccessToken/Retry/delayUnit", "MILLIS");
        }
    }

    @Inject
    AppAuthenticationMechanism mechanism;

    @Test
    void retryInterceptorFiresOnOidcAuthException() {
        // Point at an unreachable URL to force OidcAuthException on every attempt.
        // Port 1 is reserved (tcpmux) and will be refused or time out immediately.
        String unreachableUrl = "http://127.0.0.1:1/token";
        Pair<String, String> creds = Pair.of("retry-test-client", "secret");

        long startMs = System.currentTimeMillis();
        OidcAuthException thrown = assertThrows(OidcAuthException.class,
                () -> mechanism.getAccessToken(creds, unreachableUrl),
                "getAccessToken must throw OidcAuthException after retries are exhausted");
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertNotNull(thrown.getMessage());

        // With 2 retries at 50 ms delay each, the minimum wall-clock time is
        // 100 ms. Without @Retry (or if the interceptor is not wired), the
        // method would fail on the first call with near-zero delay.
        assertTrue(elapsedMs >= 80,
                "Expected at least 80 ms of retry delay (2 retries * 50 ms), "
                        + "but only " + elapsedMs + " ms elapsed. "
                        + "This suggests @Retry is not firing through the CDI proxy.");
    }
}
