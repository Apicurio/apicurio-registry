package io.apicurio.registry.auth;

import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests credential cache transitions by constructing specific states directly.
 * This makes delayed failure publication reproducible without thread scheduling.
 */
class OidcCredentialStateTest {

    private static final Pair<String, String> CREDENTIALS = Pair.of("client", "secret");

    // Match the hash calculated by the strategy for these credentials.
    private static final String KEY = DigestUtils.sha256Hex("clientsecret");

    @Test void repeatedJoinerPublicationDoesNotRestartFailureLifetime() {
        OidcAuthenticationStrategy strategy = strategy(mock(AppAuthenticationMechanism.class));
        OidcAuthenticationStrategy.TokenState owner = pending();
        strategy.credentialStates.put(KEY, owner);

        // The first caller publishes a failure with its original expiration time.
        OidcAuthException failure = new OidcAuthException("failed");
        strategy.recordFailure(KEY, owner, failure);
        Object recorded = strategy.credentialStates.get(KEY);

        // Another caller reports the same failed fetch after publication.
        strategy.recordFailure(KEY, owner, failure);

        // Keeping the same state prevents repeated reports from extending the failure TTL.
        assertSame(recorded, strategy.credentialStates.get(KEY),
                "A joiner must retain the original failure wrapper and its expiration");
    }

    @Test void staleJoinerCannotOverwriteNewerPendingFetch() {
        OidcAuthenticationStrategy strategy = strategy(mock(AppAuthenticationMechanism.class));
        OidcAuthenticationStrategy.TokenState old = pending();
        old.future().completeExceptionally(new OidcAuthException("old"));

        // A retry has replaced the old fetch and is still waiting for a token.
        OidcAuthenticationStrategy.TokenState newer = pending();
        strategy.credentialStates.put(KEY, newer);

        // Simulate a delayed failure report from a caller of the old fetch.
        strategy.recordFailure(KEY, old, new OidcAuthException("late"));

        // The old caller must neither replace nor complete the newer fetch.
        assertSame(newer, strategy.credentialStates.get(KEY));
        assertFalse(newer.future().isDone());
    }

    @Test void staleJoinerCannotHideRecoveredToken() {
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        OidcAuthenticationStrategy strategy = strategy(parent);

        // An exceptionally completed fetch is eligible for replacement.
        OidcAuthenticationStrategy.TokenState old = pending();
        old.future().completeExceptionally(new OidcAuthException("old"));
        strategy.credentialStates.put(KEY, old);

        when(parent.getAccessToken(any(Pair.class), anyString())).thenReturn(token("recovered"));

        // A later request retries successfully and caches the recovered token.
        assertEquals("Bearer recovered", authenticate(strategy));
        Object recovered = strategy.credentialStates.get(KEY);

        // The old fetch's failure arrives after recovery has already succeeded.
        strategy.recordFailure(KEY, old, new OidcAuthException("late"));

        // Recovery must remain visible, and subsequent requests must reuse its token.
        assertSame(recovered, strategy.credentialStates.get(KEY));
        assertEquals("Bearer recovered", authenticate(strategy));
        verify(parent, times(1)).getAccessToken(any(Pair.class), anyString());
    }

    @Test void validTokenIsReusedAndExpiredTokenIsReplaced() {
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        OidcAuthenticationStrategy strategy = strategy(parent);
        when(parent.getAccessToken(any(Pair.class), anyString())).thenReturn(token("fresh"));

        // A valid cached token should satisfy the request without fetching again.
        strategy.credentialStates.put(KEY, new OidcAuthenticationStrategy.TokenState(
                CompletableFuture.completedFuture(token("cached"))));

        assertEquals("Bearer cached", authenticate(strategy));
        verifyNoInteractions(parent);

        // Put expiration in the past so the test does not need to sleep.
        strategy.credentialStates.put(KEY, new OidcAuthenticationStrategy.TokenState(
                CompletableFuture.completedFuture(
                        new WrappedValue<>(Duration.ZERO, Instant.now().minusSeconds(1), "expired"))));

        // The first request refreshes the token; the second reuses that result.
        assertEquals("Bearer fresh", authenticate(strategy));
        assertEquals("Bearer fresh", authenticate(strategy));
        verify(parent, times(1)).getAccessToken(any(Pair.class), anyString());
    }

    private static WrappedValue<String> token(String value) {
        return new WrappedValue<>(Duration.ofMinutes(10), Instant.now(), value);
    }

    private static OidcAuthenticationStrategy.TokenState pending() {
        // An incomplete future represents a token fetch that is still running.
        return new OidcAuthenticationStrategy.TokenState(new CompletableFuture<>());
    }

    private static OidcAuthenticationStrategy strategy(AppAuthenticationMechanism parent) {
        // Fetching is mocked, so these URLs do not require a running server.
        AuthConfig config = new AuthConfig();
        config.authServerUrl = "http://localhost/realms/test";
        config.oidcTokenPath = "http://localhost/realms/test/token";

        return new OidcAuthenticationStrategy(mock(OidcAuthenticationMechanism.class), config, null, null,
                LoggerFactory.getLogger(OidcCredentialStateTest.class), parent);
    }

    private static String authenticate(OidcAuthenticationStrategy strategy) {
        // Real headers let the test inspect what the strategy attaches to the request.
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        when(context.request()).thenReturn(request);
        when(request.headers()).thenReturn(headers);

        // Only cache behavior and header assignment are checked here.
        // The final OIDC authentication mechanism is mocked.
        strategy.authenticateWithClientCredentials(CREDENTIALS, context, mock(IdentityProviderManager.class));

        return headers.get("Authorization");
    }
}
