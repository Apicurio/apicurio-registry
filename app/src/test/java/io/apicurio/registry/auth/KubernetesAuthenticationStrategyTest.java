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

import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.fabric8.kubernetes.api.model.authentication.UserInfo;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.InOutCreateable;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KubernetesAuthenticationStrategyTest {

    @Mock
    KubernetesClient kubernetesClient;

    @Mock
    RoutingContext routingContext;

    @Mock
    HttpServerRequest httpRequest;

    @Mock
    IdentityProviderManager identityProviderManager;

    @Mock
    @SuppressWarnings("rawtypes")
    InOutCreateable tokenReviewsOp;

    private AuthConfig authConfig;
    private KubernetesAuthenticationStrategy strategy;

    @BeforeEach
    void setUp() {
        authConfig = new AuthConfig();
        authConfig.kubernetesAuthEnabled = true;
        authConfig.kubernetesApiAudiences = "";
        authConfig.kubernetesTokenCacheExpiration = 5;
        strategy = new KubernetesAuthenticationStrategy(kubernetesClient, authConfig,
                LoggerFactory.getLogger(KubernetesAuthenticationStrategyTest.class));
        when(routingContext.request()).thenReturn(httpRequest);
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
        verify(kubernetesClient, never()).tokenReviews();
    }

    @Test
    void testReturnsNullWhenNotBearerAuth() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(kubernetesClient, never()).tokenReviews();
    }

    @Test
    void testReturnsNullWhenEmptyBearerToken() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer ");

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(kubernetesClient, never()).tokenReviews();
    }

    @SuppressWarnings("unchecked")
    @Test
    void testAuthenticatesValidToken() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer valid-token-123");

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername("system:serviceaccount:default:my-sa");
        userInfo.setUid("sa-uid-456");
        userInfo.setGroups(List.of("system:serviceaccounts", "developers"));

        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(true)
                .withUser(userInfo)
                .endStatus()
                .build();

        when(kubernetesClient.tokenReviews()).thenReturn(tokenReviewsOp);
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

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

    @SuppressWarnings("unchecked")
    @Test
    void testReturnsNullWhenTokenNotAuthenticated() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer invalid-token");

        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(false)
                .endStatus()
                .build();

        when(kubernetesClient.tokenReviews()).thenReturn(tokenReviewsOp);
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
        verify(identityProviderManager, never()).authenticate(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testTokenReviewIncludesAudiencesWhenConfigured() {
        authConfig.kubernetesApiAudiences = "api,https://registry.example.com";
        strategy = new KubernetesAuthenticationStrategy(kubernetesClient, authConfig,
                LoggerFactory.getLogger(KubernetesAuthenticationStrategyTest.class));

        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer token-with-audiences");

        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(false)
                .endStatus()
                .build();

        when(kubernetesClient.tokenReviews()).thenReturn(tokenReviewsOp);
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        ArgumentCaptor<TokenReview> captor = ArgumentCaptor.forClass(TokenReview.class);
        verify(tokenReviewsOp).create(captor.capture());
        TokenReview reviewRequest = captor.getValue();
        assertNotNull(reviewRequest.getSpec().getAudiences());
        assertEquals(2, reviewRequest.getSpec().getAudiences().size());
        assertEquals("api", reviewRequest.getSpec().getAudiences().get(0));
        assertEquals("https://registry.example.com", reviewRequest.getSpec().getAudiences().get(1));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testCachesTokenReviewResult() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer cached-token");

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername("cached-user");
        userInfo.setUid("uid-cached");
        userInfo.setGroups(Collections.emptyList());

        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(true)
                .withUser(userInfo)
                .endStatus()
                .build();

        when(kubernetesClient.tokenReviews()).thenReturn(tokenReviewsOp);
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        SecurityIdentity mockIdentity = QuarkusSecurityIdentity.builder()
                .setPrincipal(() -> "cached-user")
                .build();
        when(identityProviderManager.authenticate(any(KubernetesAuthenticationRequest.class)))
                .thenReturn(Uni.createFrom().item(mockIdentity));

        // First call — should hit K8s API
        strategy.authenticate(routingContext, identityProviderManager).await().indefinitely();

        // Second call with same token — should use cache
        strategy.authenticate(routingContext, identityProviderManager).await().indefinitely();

        // TokenReview API should only be called once
        verify(tokenReviewsOp).create(any(TokenReview.class));
        // Identity provider should be called twice (once per authenticate)
        verify(identityProviderManager, org.mockito.Mockito.times(2))
                .authenticate(any(KubernetesAuthenticationRequest.class));
    }

    @Test
    void testReturnsNullWhenTokenReviewApiCallFails() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer error-token");
        when(kubernetesClient.tokenReviews()).thenThrow(
                new RuntimeException("Connection refused"));

        SecurityIdentity result = strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        assertNull(result);
    }

    @Test
    void testBearerPrefixIsCaseInsensitive() {
        when(httpRequest.getHeader("Authorization")).thenReturn("bearer lowercase-token");

        // This should still be recognized as a Bearer token.
        // The strategy uses regionMatches with ignoreCase=true
        // If the token review returns unauthenticated, we get null but the K8s API was called
        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(false)
                .endStatus()
                .build();

        when(kubernetesClient.tokenReviews()).thenReturn(tokenReviewsOp);
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        strategy.authenticate(routingContext, identityProviderManager)
                .await().indefinitely();

        // Verify the K8s API was actually called (proving the Bearer prefix was recognized)
        verify(tokenReviewsOp).create(any(TokenReview.class));
    }
}
