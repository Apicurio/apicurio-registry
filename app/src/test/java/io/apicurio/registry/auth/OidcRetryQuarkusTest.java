package io.apicurio.registry.auth;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the REAL MicroProfile Fault Tolerance {@code @Retry} on
 * {@link AppAuthenticationMechanism#getAccessToken} through the CDI proxy.
 * Proves the interceptor is actually wired (a classic MP-FT footgun is an
 * annotation on a non-CDI class or invoked via {@code this.} that silently
 * never applies).
 *
 * <p>Points the method at a local endpoint that answers 500, so every attempt throws
 * {@link OidcAuthException}, and counts the requests the endpoint receives. With
 * {@code maxRetries = 2} (overridden via MP-FT config) the endpoint should see 3 requests
 * in total, 1 original and 2 retries.
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
    void retryInterceptorFiresOnOidcAuthException() throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/token", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String tokenUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";
            Pair<String, String> creds = Pair.of("retry-test-client", "secret");

            assertThrows(OidcAuthException.class,
                    () -> mechanism.getAccessToken(creds, tokenUrl),
                    "getAccessToken must throw OidcAuthException after retries are exhausted");

            // 1 original call + 2 retries. Without @Retry, or if the interceptor is not
            // wired through the CDI proxy, the endpoint would see a single request.
            assertEquals(3, requests.get(),
                    "Expected the original attempt plus 2 retries to reach the endpoint");
        } finally {
            server.stop(0);
        }
    }
}
