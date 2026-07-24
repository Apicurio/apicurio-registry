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
import io.fabric8.kubernetes.api.model.authentication.TokenReviewSpec;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;

import java.util.List;

/**
 * Thin CDI wrapper around the Kubernetes TokenReview API call, guarded by a MicroProfile
 * Fault Tolerance circuit breaker.
 *
 * <p>The breaker opens after 5 consecutive API failures (rolling window of 5 with a failure
 * ratio of 1.0) and stays open for 30 seconds before allowing probe calls — mirroring the
 * "N consecutive failures, skip for M seconds, then probe" behavior requested in issue #8462.
 * While the circuit is open, calls fail fast with
 * {@link org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException},
 * which {@link KubernetesAuthenticationStrategy} maps to the unauthenticated (fail-open to the
 * next mechanism) contract.
 *
 * <p>Only genuine API failures (thrown exceptions or a missing response) count toward the
 * breaker: a TokenReview response with {@code authenticated=false} is a healthy API reply and
 * returns normally.
 *
 * <p>The breaker is tuned or disabled with standard MicroProfile Fault Tolerance configuration,
 * e.g. {@code io.apicurio.registry.auth.TokenReviewClient/review/CircuitBreaker/enabled=false}
 * to disable it, or {@code .../CircuitBreaker/delay=60000} to change the open window.
 *
 * <p>Security note: while the circuit is open, requests carrying Kubernetes tokens are treated
 * as unauthenticated. If anonymous read access is enabled
 * ({@code apicurio.auth.anonymous-read-access.enabled}), those requests are served as anonymous
 * read-only requests instead of being rejected.
 */
@ApplicationScoped
public class TokenReviewClient {

    @Inject
    Instance<KubernetesClient> kubernetesClient;

    @Inject
    AuthConfig authConfig;

    /**
     * Performs a TokenReview API call for the given bearer token.
     *
     * @return the status of the TokenReview response (may be {@code null} if the API returned a
     *         response without a status)
     * @throws IllegalStateException if the API returned no response — thrown (rather than
     *         returning {@code null}) so the call registers as a failure with the circuit breaker
     */
    @CircuitBreaker(requestVolumeThreshold = 5, failureRatio = 1.0, delay = 30_000)
    public TokenReviewStatus review(String token) {
        TokenReviewSpec spec = new TokenReviewSpec();
        spec.setToken(token);

        List<String> audiences = authConfig.getKubernetesApiAudiences();
        if (!audiences.isEmpty()) {
            spec.setAudiences(audiences);
        }

        TokenReview review = new TokenReview();
        review.setSpec(spec);
        TokenReview response = kubernetesClient.get().tokenReviews().create(review);
        if (response == null) {
            throw new IllegalStateException("Kubernetes TokenReview API returned no response");
        }
        return response.getStatus();
    }
}
