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
import io.fabric8.kubernetes.api.model.authentication.UserInfo;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

public class KubernetesAuthenticationStrategy implements AuthenticationStrategy {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MAX_CACHE_SIZE = 10_000;

    private final KubernetesClient kubernetesClient;
    private final AuthConfig authConfig;
    private final Logger log;
    private final AsyncCache<String, TokenReviewResult> cache;

    public KubernetesAuthenticationStrategy(KubernetesClient kubernetesClient,
            AuthConfig authConfig, Logger log) {
        this.kubernetesClient = kubernetesClient;
        this.authConfig = authConfig;
        this.log = log;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfter(new Expiry<String, TokenReviewResult>() {
                    @Override
                    public long expireAfterCreate(String key, TokenReviewResult value, long currentTime) {
                        if (value.isApiError) {
                            return Duration.ofSeconds(30).toNanos();
                        }
                        return Duration.ofMinutes(authConfig.kubernetesTokenCacheExpiration).toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, TokenReviewResult value, long currentTime, long currentDuration) {
                        return expireAfterCreate(key, value, currentTime);
                    }

                    @Override
                    public long expireAfterRead(String key, TokenReviewResult value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .buildAsync();
    }

    @Override
    public String name() {
        return "kubernetes";
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {

        String authHeader = context.request().getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0,
                BEARER_PREFIX.length())) {
            return Uni.createFrom().nullItem();
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return Uni.createFrom().nullItem();
        }

        String tokenHash = sha256(token);

        CompletableFuture<TokenReviewResult> future = cache.get(tokenHash, (k, executor) -> 
                CompletableFuture.supplyAsync(() -> performTokenReview(token), Infrastructure.getDefaultWorkerPool())
        );

        return Uni.createFrom().completionStage(future)
                .onItem().transformToUni(result -> {
                    if (!result.authenticated) {
                        return Uni.createFrom().nullItem();
                    }
                    return identityProviderManager.authenticate(
                            new KubernetesAuthenticationRequest(
                                    result.username, result.uid, result.groups));
                });
    }

    private TokenReviewResult performTokenReview(String token) {
        try {
            TokenReviewSpec spec = new TokenReviewSpec();
            spec.setToken(token);

            List<String> audiences = authConfig.getKubernetesApiAudiences();
            if (!audiences.isEmpty()) {
                spec.setAudiences(audiences);
            }

            TokenReview review = new TokenReview();
            review.setSpec(spec);
            TokenReview response = kubernetesClient.tokenReviews().create(review);

            TokenReviewStatus status = response.getStatus();
            if (status == null || !Boolean.TRUE.equals(status.getAuthenticated())) {
                log.debug("Kubernetes TokenReview: authentication failed");
                return new TokenReviewResult(false, null, null, Collections.emptySet());
            }

            UserInfo user = status.getUser();
            String username = user != null ? user.getUsername() : "unknown";
            String uid = user != null ? user.getUid() : null;
            Set<String> groups = new HashSet<>();
            if (user != null && user.getGroups() != null) {
                groups.addAll(user.getGroups());
            }

            log.debug("Kubernetes TokenReview: authenticated user '{}'", username);

            return new TokenReviewResult(true, username, uid, groups);

        } catch (Exception e) {
            log.warn("Kubernetes TokenReview API call failed: {}", e.getMessage());
            return new TokenReviewResult(false, null, null, Collections.emptySet(), true);
        }
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate",
                "Bearer realm=\"kubernetes\""));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(KubernetesAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(
                HttpCredentialTransport.Type.AUTHORIZATION, "bearer"));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                int val = b & 0xff;
                if (val < 16) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(val));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static final class TokenReviewResult {
        final boolean authenticated;
        final String username;
        final String uid;
        final Set<String> groups;
        final boolean isApiError;

        TokenReviewResult(boolean authenticated, String username, String uid, Set<String> groups) {
            this(authenticated, username, uid, groups, false);
        }

        TokenReviewResult(boolean authenticated, String username, String uid, Set<String> groups, boolean isApiError) {
            this.authenticated = authenticated;
            this.username = username;
            this.uid = uid;
            this.groups = groups;
            this.isApiError = isApiError;
        }
    }
}
