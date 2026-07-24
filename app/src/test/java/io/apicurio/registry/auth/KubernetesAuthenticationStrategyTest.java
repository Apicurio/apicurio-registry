/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.auth;

import io.fabric8.kubernetes.api.model.authentication.TokenReviewStatus;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewStatusBuilder;
import io.fabric8.kubernetes.api.model.authentication.UserInfo;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KubernetesAuthenticationStrategyTest {

    @Mock
    TokenReviewClient tokenReviewClient;

    @Mock
    RoutingContext routingContext;

    @Mock
    HttpServerRequest httpRequest;

    @Mock
    IdentityProviderManager identityProviderManager;

    private AuthConfig authConfig;
    private KubernetesAuthenticationStrategy strategy;

    @BeforeEach
    void setUp() {
        authConfig = new AuthConfig();
        authConfig.kubernetesAuthEnabled = true;
        authConfig.kubernetesTokenCacheExpiration = 5;
        strategy = new KubernetesAuthenticationStrategy(tokenReviewClient, authConfig,
                LoggerFactory.getLogger(KubernetesAuthenticationStrategyTest.class));
        when(routingContext.request()).thenReturn(httpRequest);
    }

    private static TokenReviewStatus authenticatedStatus(String username, String uid,
            List<String> groups) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        userInfo.setUid(uid);
        userInfo.setGroups(groups);
        return new TokenReviewStatusBuilder()
                .withAuthenticated(true)
                .withUser(userInfo)
                .build();
    }

    private static TokenReviewStatus unauthenticatedStatus() {
        return new TokenReviewStatusBuilder()
                .withAuthenticated(false)
                .build();
    }

    @Test
    void testNameReturnsKubernetes() {
        assertEquals("kubernetes", strategy.name());
    }

    @Test
    void testReturnsNullWhenNoAuthorizationHeader() {
        when(httpRequest.getHeader("Authorization")).thenReturn(null);

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(tokenReviewClient, never()).review(anyString());
    }

    @Test
    void testReturnsNullWhenNotBearerAuth() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(tokenReviewClient, never()).review(anyString());
    }

    @Test
    void testReturnsNullWhenEmptyBearerToken() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer ");

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(tokenReviewClient, never()).review(anyString());
    }

    @Test
    void testAuthenticatesValidToken() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer valid-token-123");
        when(tokenReviewClient.review("valid-token-123")).thenReturn(authenticatedStatus(
                "system:serviceaccount:default:my-sa", "sa-uid-456",
                List.of("system:serviceaccounts", "developers")));

        SecurityIdentity mockIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "system:serviceaccount:default:my-sa")
                .build();
        when(identityProviderManager.authenticate(any(KubernetesAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(mockIdentity));

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("system:serviceaccount:default:my-sa", result.getPrincipal().getName());

        ArgumentCaptor<KubernetesAuthenticationRequest> captor =
                ArgumentCaptor.forClass(KubernetesAuthenticationRequest.class);
        verify(identityProviderManager).authenticate(captor.capture());
        KubernetesAuthenticationRequest captured = captor.getValue();
        assertEquals("system:serviceaccount:default:my-sa", captured.getUsername());
        assertEquals("sa-uid-456", captured.getUid());
        assertEquals(2, captured.getGroups().size());
    }

    @Test
    void testReturnsNullWhenTokenNotAuthenticated() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(tokenReviewClient.review("invalid-token")).thenReturn(unauthenticatedStatus());

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(identityProviderManager, never()).authenticate(any());
    }

    @Test
    void testReturnsNullWhenStatusMissing() {
        // A response without a status must be treated as unauthenticated, not as an error.
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer statusless-token");
        when(tokenReviewClient.review("statusless-token")).thenReturn(null);

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(identityProviderManager, never()).authenticate(any());
    }

    @Test
    void testCachesTokenReviewResult() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer cached-token");
        when(tokenReviewClient.review("cached-token")).thenReturn(authenticatedStatus(
                "cached-user", "uid-cached", Collections.emptyList()));

        SecurityIdentity mockIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "cached-user")
                .build();
        when(identityProviderManager.authenticate(any(KubernetesAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(mockIdentity));

        // First call — should hit the TokenReview client.
        strategy.authenticate(routingContext, identityProviderManager).await().indefinitely();

        // Second call with same token — should use the cache.
        strategy.authenticate(routingContext, identityProviderManager).await().indefinitely();

        // The TokenReview client should only be called once.
        verify(tokenReviewClient).review("cached-token");
        // Identity provider should be called twice (once per authenticate).
        verify(identityProviderManager, times(2))
                .authenticate(any(KubernetesAuthenticationRequest.class));
    }

    @Test
    void testReturnsNullWhenTokenReviewApiCallFails() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer error-token");
        when(tokenReviewClient.review("error-token"))
                .thenThrow(new RuntimeException("Connection refused"));

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(identityProviderManager, never()).authenticate(any());
    }

    @Test
    void testCircuitBreakerOpenFailsOpenToNextMechanism() {
        // When the circuit is open, the strategy must return null (unauthenticated) so the auth
        // chain can fall through to the next mechanism — the same contract as an API error.
        when(httpRequest.getHeader("Authorization"))
                .thenReturn("Bearer token-a", "Bearer token-b");
        when(tokenReviewClient.review(anyString()))
                .thenThrow(new CircuitBreakerOpenException("circuit breaker is open"));

        SecurityIdentity first = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();
        SecurityIdentity second = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(first);
        assertNull(second);
        verify(identityProviderManager, never()).authenticate(any());
        // Open-circuit rejections are not negatively cached: each request re-consults the client,
        // so traffic resumes the moment the breaker admits calls again.
        verify(tokenReviewClient, times(2)).review(anyString());
    }

    @Test
    void testRecoversAfterCircuitCloses() {
        // Once the breaker admits calls again (probe succeeded), authentication must work
        // immediately — no residual negative state in the strategy.
        when(httpRequest.getHeader("Authorization"))
                .thenReturn("Bearer recover-blocked", "Bearer recover-ok");
        when(tokenReviewClient.review("recover-blocked"))
                .thenThrow(new CircuitBreakerOpenException("circuit breaker is open"));
        when(tokenReviewClient.review("recover-ok")).thenReturn(authenticatedStatus(
                "recovered-user", "uid-r", Collections.emptyList()));

        SecurityIdentity mockIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "recovered-user")
                .build();
        when(identityProviderManager.authenticate(any(KubernetesAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(mockIdentity));

        SecurityIdentity blocked = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();
        SecurityIdentity recovered = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(blocked);
        assertNotNull(recovered);
        assertEquals("recovered-user", recovered.getPrincipal().getName());
    }

    @Test
    void testSingleApiFailureDoesNotBlockOtherTokens() {
        // A single API failure must not penalize other tokens (no negative caching of API errors).
        when(httpRequest.getHeader("Authorization"))
                .thenReturn("Bearer token-a", "Bearer token-b", "Bearer token-a");
        when(tokenReviewClient.review("token-a"))
                .thenThrow(new RuntimeException("blip"))
                .thenReturn(authenticatedStatus("user-a", "uid-a", Collections.emptyList()));
        when(tokenReviewClient.review("token-b")).thenReturn(
                authenticatedStatus("user-b", "uid-b", Collections.emptyList()));

        SecurityIdentity mockIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "user-b")
                .build();
        when(identityProviderManager.authenticate(any(KubernetesAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(mockIdentity));

        // Token A fails once...
        SecurityIdentity aFirst = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();
        // ...token B still authenticates, not penalized by A's failure...
        SecurityIdentity bResult = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();
        // ...and token A retried still reaches the API (no negative caching of API errors).
        SecurityIdentity aRetry = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(aFirst);
        assertNotNull(bResult);
        assertNotNull(aRetry);
        verify(tokenReviewClient, times(2)).review("token-a");
        verify(tokenReviewClient, times(1)).review("token-b");
    }

    @Test
    void testBearerPrefixIsCaseInsensitive() {
        when(httpRequest.getHeader("Authorization")).thenReturn("bearer lowercase-token");
        when(tokenReviewClient.review("lowercase-token")).thenReturn(unauthenticatedStatus());

        strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        // Verify the TokenReview client was actually called (proving the prefix was recognized).
        verify(tokenReviewClient).review("lowercase-token");
    }
}
