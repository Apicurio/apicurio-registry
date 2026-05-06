/*
 * Copyright 2021 Red Hat
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

import io.quarkus.vertx.http.runtime.security.BasicAuthenticationMechanism;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import io.apicurio.registry.logging.audit.AuditLogService;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Alternative
@Priority(1)
@ApplicationScoped
@Unremovable
public class AppAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    AuthConfig authConfig;

    @Inject
    BasicAuthenticationMechanism basicAuthenticationMechanism;

    @Inject
    OidcAuthenticationMechanism oidcAuthenticationMechanism;

    @Inject
    ProxyHeaderAuthenticationMechanism proxyHeaderAuthenticationMechanism;

    @Inject
    AuditLogService auditLog;

    @Inject
    Logger log;

    @Inject
    WebClient webClient;

    @Inject
    DefaultJWTParser jwtParser;

    private List<AuthenticationStrategy> authChain;

    @PostConstruct
    public void init() {
        authChain = buildAuthChain();
        if (authChain.isEmpty()) {
            log.info("Authentication chain: [none] (no mechanisms enabled)");
        } else {
            log.info("Authentication chain: {}",
                    authChain.stream().map(AuthenticationStrategy::name)
                            .collect(Collectors.joining(" -> ")));
        }
    }

    /**
     * Builds the ordered authentication chain from the configured mechanism priority list.
     * Each mechanism name in {@code apicurio.authn.mechanism.priority} is resolved to a
     * strategy factory. Only mechanisms that are both listed in the priority and enabled via
     * their respective config property are included. Unknown names are logged as warnings and
     * skipped; disabled mechanisms are silently omitted.
     *
     * @return an unmodifiable, ordered list of enabled authentication strategies
     */
    List<AuthenticationStrategy> buildAuthChain() {
        Map<String, Supplier<AuthenticationStrategy>> strategyFactories = new LinkedHashMap<>();
        strategyFactories.put("basic", () -> authConfig.basicAuthEnabled
                ? new DelegatingAuthenticationStrategy("basic", basicAuthenticationMechanism)
                : null);
        strategyFactories.put("proxy-header", () -> authConfig.proxyHeaderAuthEnabled
                ? new DelegatingAuthenticationStrategy("proxy-header",
                        proxyHeaderAuthenticationMechanism)
                : null);
        strategyFactories.put("oidc", () -> {
            if (!authConfig.oidcAuthEnabled) {
                return null;
            }
            return new OidcAuthenticationStrategy(oidcAuthenticationMechanism, authConfig,
                    auditLog, webClient, log, this);
        });

        List<AuthenticationStrategy> chain = new ArrayList<>();
        for (String name : authConfig.getMechanismPriorityList()) {
            Supplier<AuthenticationStrategy> factory = strategyFactories.get(name);
            if (factory == null) {
                log.warn("Unknown authentication mechanism name in priority list: '{}'. "
                        + "Valid values are: basic, proxy-header, oidc", name);
                continue;
            }
            AuthenticationStrategy strategy = factory.get();
            if (strategy != null) {
                chain.add(strategy);
            } else {
                log.debug("Mechanism '{}' is in priority list but not enabled, skipping.",
                        name);
            }
        }
        return Collections.unmodifiableList(chain);
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        Uni<SecurityIdentity> chain = Uni.createFrom().nullItem();
        for (AuthenticationStrategy strategy : authChain) {
            chain = chain.onItem().ifNull()
                    .switchTo(() -> strategy.authenticate(context, identityProviderManager));
        }
        return chain;
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        if (authChain.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return authChain.get(authChain.size() - 1).getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        Set<Class<? extends AuthenticationRequest>> credentialTypes = new HashSet<>();
        for (AuthenticationStrategy strategy : authChain) {
            credentialTypes.addAll(strategy.getCredentialTypes());
        }
        return credentialTypes;
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        if (authChain.isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return authChain.get(authChain.size() - 1).getCredentialTransport(context);
    }

    /**
     * Obtains an access token using client credentials grant. This method is hosted on this CDI
     * bean (rather than on {@link OidcAuthenticationStrategy}) so that the MicroProfile
     * {@link Retry} interceptor is applied.
     *
     * @param clientCredentials the client ID and secret
     * @param credentialsHash hash key for the token cache
     * @param cachedAccessTokens token cache (owned by the OIDC strategy)
     * @param cachedAuthFailures failure cache (owned by the OIDC strategy)
     * @param oidcTokenUrl the token endpoint URL (computed by the OIDC strategy at init)
     */
    @Retry(retryOn = OidcAuthException.class, maxRetries = 4, delay = 1,
            delayUnit = ChronoUnit.SECONDS)
    public String getAccessToken(Pair<String, String> clientCredentials, String credentialsHash,
            ConcurrentHashMap<String, WrappedValue<String>> cachedAccessTokens,
            ConcurrentHashMap<String, WrappedValue<RuntimeException>> cachedAuthFailures,
            String oidcTokenUrl) {
        String clientId = clientCredentials.getLeft();
        String clientSecret = clientCredentials.getRight();

        String scopeForClient = authConfig.getScopeForClient(clientId);

        MultiMap form = MultiMap.caseInsensitiveMultiMap()
                .add("grant_type", "client_credentials")
                .add("client_id", clientId)
                .add("client_secret", clientSecret);
        if (scopeForClient != null) {
            form.add("scope", scopeForClient);
        }

        try {
            var response = webClient.postAbs(oidcTokenUrl)
                    .sendForm(form)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .join();

            int statusCode = response.statusCode();
            if (statusCode == 401) {
                var ex = new io.quarkus.security.UnauthorizedException(
                        "OIDC token request returned 401");
                cachedAuthFailures.put(credentialsHash,
                        new WrappedValue<>(
                                OidcAuthenticationStrategy.getAccessTokenExpiration(null,
                                        authConfig, jwtParser, log),
                                Instant.now(), ex));
                throw ex;
            } else if (statusCode == 403) {
                var ex = new io.quarkus.security.ForbiddenException(
                        "OIDC token request returned 403");
                cachedAuthFailures.put(credentialsHash,
                        new WrappedValue<>(
                                OidcAuthenticationStrategy.getAccessTokenExpiration(null,
                                        authConfig, jwtParser, log),
                                Instant.now(), ex));
                throw ex;
            } else if (statusCode < 200 || statusCode >= 300) {
                throw new OidcAuthException(
                        "OIDC token request failed with status " + statusCode);
            }

            JsonObject json = response.bodyAsJsonObject();
            String jwtToken = json.getString("access_token");
            cachedAccessTokens.put(credentialsHash,
                    new WrappedValue<>(
                            OidcAuthenticationStrategy.getAccessTokenExpiration(jwtToken,
                                    authConfig, jwtParser, log),
                            Instant.now(), jwtToken));
            return jwtToken;
        } catch (io.quarkus.security.UnauthorizedException
                | io.quarkus.security.ForbiddenException ex) {
            throw ex;
        } catch (RuntimeException e) {
            throw new OidcAuthException("Failed to obtain access token", e);
        }
    }
}
