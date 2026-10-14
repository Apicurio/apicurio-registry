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

class OidcCredentialStateTest {
    private static final Pair<String, String> CREDENTIALS = Pair.of("client", "secret");
    private static final String KEY = DigestUtils.sha256Hex("clientsecret");

    @Test
    void repeatedJoinerPublicationDoesNotRestartFailureLifetime() {
        OidcAuthenticationStrategy strategy = strategy(mock(AppAuthenticationMechanism.class));
        OidcAuthenticationStrategy.TokenState owner = pending();
        strategy.credentialStates.put(KEY, owner);
        OidcAuthException failure = new OidcAuthException("failed");
        strategy.recordFailure(KEY, owner, failure);
        Object recorded = strategy.credentialStates.get(KEY);
        strategy.recordFailure(KEY, owner, failure);
        assertSame(recorded, strategy.credentialStates.get(KEY),
                "A joiner must retain the original failure wrapper and its expiration");
    }

    @Test
    void staleJoinerCannotOverwriteNewerPendingFetch() {
        OidcAuthenticationStrategy strategy = strategy(mock(AppAuthenticationMechanism.class));
        OidcAuthenticationStrategy.TokenState old = pending();
        old.future().completeExceptionally(new OidcAuthException("old"));
        OidcAuthenticationStrategy.TokenState newer = pending();
        strategy.credentialStates.put(KEY, newer);
        strategy.recordFailure(KEY, old, new OidcAuthException("late"));
        assertSame(newer, strategy.credentialStates.get(KEY));
        assertFalse(newer.future().isDone());
    }

    @Test
    void staleJoinerCannotHideRecoveredToken() {
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        OidcAuthenticationStrategy strategy = strategy(parent);
        OidcAuthenticationStrategy.TokenState old = pending();
        old.future().completeExceptionally(new OidcAuthException("old"));
        strategy.credentialStates.put(KEY, old);
        when(parent.getAccessToken(any(Pair.class), anyString())).thenReturn(token("recovered"));
        assertEquals("Bearer recovered", authenticate(strategy));
        Object recovered = strategy.credentialStates.get(KEY);
        strategy.recordFailure(KEY, old, new OidcAuthException("late"));
        assertSame(recovered, strategy.credentialStates.get(KEY));
        assertEquals("Bearer recovered", authenticate(strategy));
        verify(parent, times(1)).getAccessToken(any(Pair.class), anyString());
    }

    @Test
    void validTokenIsReusedAndExpiredTokenIsReplaced() {
        AppAuthenticationMechanism parent = mock(AppAuthenticationMechanism.class);
        OidcAuthenticationStrategy strategy = strategy(parent);
        when(parent.getAccessToken(any(Pair.class), anyString())).thenReturn(token("fresh"));
        strategy.credentialStates.put(KEY, new OidcAuthenticationStrategy.TokenState(
                CompletableFuture.completedFuture(token("cached"))));
        assertEquals("Bearer cached", authenticate(strategy));
        verifyNoInteractions(parent);
        strategy.credentialStates.put(KEY, new OidcAuthenticationStrategy.TokenState(
                CompletableFuture.completedFuture(new WrappedValue<>(Duration.ZERO,
                        Instant.now().minusSeconds(1), "expired"))));
        assertEquals("Bearer fresh", authenticate(strategy));
        assertEquals("Bearer fresh", authenticate(strategy));
        verify(parent, times(1)).getAccessToken(any(Pair.class), anyString());
    }

    private static WrappedValue<String> token(String value) {
        return new WrappedValue<>(Duration.ofMinutes(10), Instant.now(), value);
    }

    private static OidcAuthenticationStrategy.TokenState pending() {
        return new OidcAuthenticationStrategy.TokenState(new CompletableFuture<>());
    }

    private static OidcAuthenticationStrategy strategy(AppAuthenticationMechanism parent) {
        AuthConfig config = new AuthConfig();
        config.authServerUrl = "http://localhost/realms/test";
        config.oidcTokenPath = "http://localhost/realms/test/token";
        return new OidcAuthenticationStrategy(mock(OidcAuthenticationMechanism.class), config,
                null, null, LoggerFactory.getLogger(OidcCredentialStateTest.class), parent);
    }

    private static String authenticate(OidcAuthenticationStrategy strategy) {
        RoutingContext context = mock(RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        when(context.request()).thenReturn(request);
        when(request.headers()).thenReturn(headers);
        strategy.authenticateWithClientCredentials(CREDENTIALS, context, mock(IdentityProviderManager.class));
        return headers.get("Authorization");
    }
}
