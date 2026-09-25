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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.InOutCreateable;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenReviewClientTest {

    @Mock
    KubernetesClient kubernetesClient;

    @Mock
    Instance<KubernetesClient> kubernetesClientInstance;

    @Mock
    @SuppressWarnings("rawtypes")
    InOutCreateable tokenReviewsOp;

    private AuthConfig authConfig;
    private TokenReviewClient client;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        authConfig = new AuthConfig();
        authConfig.kubernetesApiAudiences = Optional.empty();
        client = new TokenReviewClient();
        client.kubernetesClient = kubernetesClientInstance;
        client.authConfig = authConfig;
        when(kubernetesClientInstance.get()).thenReturn(kubernetesClient);
        when(kubernetesClient.tokenReviews()).thenReturn(tokenReviewsOp);
    }

    @SuppressWarnings("unchecked")
    @Test
    void testReturnsStatusOfResponse() {
        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(true)
                .endStatus()
                .build();
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        assertEquals(Boolean.TRUE, client.review("some-token").getAuthenticated());

        ArgumentCaptor<TokenReview> captor = ArgumentCaptor.forClass(TokenReview.class);
        verify(tokenReviewsOp).create(captor.capture());
        assertEquals("some-token", captor.getValue().getSpec().getToken());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testUnauthenticatedStatusIsANormalReturnNotAFailure() {
        // An authenticated=false response is a healthy API reply: it must return normally, so it
        // never counts as a failure toward the circuit breaker (only thrown exceptions do).
        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(false)
                .endStatus()
                .build();
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        assertFalse(client.review("invalid-token").getAuthenticated());
    }

    @SuppressWarnings("unchecked")
    @Test
    void testThrowsWhenApiReturnsNoResponse() {
        // A null response must be thrown (not returned) so it registers as a failure with the
        // MicroProfile Fault Tolerance circuit breaker.
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> client.review("some-token"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testIncludesAudiencesWhenConfigured() {
        authConfig.kubernetesApiAudiences = Optional.of("api,https://registry.example.com");

        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(false)
                .endStatus()
                .build();
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        client.review("token-with-audiences");

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
    void testOmitsAudiencesWhenNotConfigured() {
        TokenReview response = new TokenReviewBuilder()
                .withNewStatus()
                .withAuthenticated(false)
                .endStatus()
                .build();
        when(tokenReviewsOp.create(any(TokenReview.class))).thenReturn(response);

        client.review("token-without-audiences");

        ArgumentCaptor<TokenReview> captor = ArgumentCaptor.forClass(TokenReview.class);
        verify(tokenReviewsOp).create(captor.capture());
        assertTrue(captor.getValue().getSpec().getAudiences() == null
                || captor.getValue().getSpec().getAudiences().isEmpty());
    }
}
