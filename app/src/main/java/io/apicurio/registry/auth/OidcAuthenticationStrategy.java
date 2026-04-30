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

import io.apicurio.registry.logging.audit.AuditHttpRequestContext;
import io.apicurio.registry.logging.audit.AuditHttpRequestInfo;
import io.apicurio.registry.logging.audit.AuditLogService;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Authentication strategy that encapsulates the full OIDC authentication flow, including audit
 * logging, client credentials grant, and password grant authentication.
 */
public class OidcAuthenticationStrategy implements AuthenticationStrategy {

    private final OidcAuthenticationMechanism oidcAuthenticationMechanism;
    private final AuthConfig authConfig;
    private final AuditLogService auditLog;
    private final WebClient webClient;
    private final Logger log;
    private final AppAuthenticationMechanism parent;

    private final ConcurrentHashMap<String, WrappedValue<String>> cachedAccessTokens;
    private final ConcurrentHashMap<String, WrappedValue<RuntimeException>> cachedAuthFailures;
    private final String oidcTokenUrl;

    /**
     * Creates a new OIDC authentication strategy.
     *
     * @param oidcAuthenticationMechanism the Quarkus OIDC mechanism to delegate to
     * @param authConfig authentication configuration
     * @param auditLog audit logging service
     * @param webClient HTTP client for token requests
     * @param log logger
     * @param parent the parent CDI bean (hosts {@code getAccessToken} for @Retry support)
     */
    public OidcAuthenticationStrategy(OidcAuthenticationMechanism oidcAuthenticationMechanism,
            AuthConfig authConfig, AuditLogService auditLog, WebClient webClient,
            Logger log, AppAuthenticationMechanism parent) {
        this.oidcAuthenticationMechanism = oidcAuthenticationMechanism;
        this.authConfig = authConfig;
        this.auditLog = auditLog;
        this.webClient = webClient;
        this.log = log;
        this.parent = parent;

        this.cachedAccessTokens = new ConcurrentHashMap<>();
        this.cachedAuthFailures = new ConcurrentHashMap<>();

        if (authConfig.oidcTokenPath.startsWith("http")) {
            this.oidcTokenUrl = authConfig.oidcTokenPath;
        } else {
            this.oidcTokenUrl = authConfig.authServerUrl + authConfig.oidcTokenPath;
        }
    }

    @Override
    public String name() {
        return "oidc";
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        setAuditLogger(context);
        if (authConfig.basicClientCredentialsAuthEnabled.get()) {
            final Pair<String, String> clientCredentials = CredentialsHelper
                    .extractCredentialsFromContext(context);
            if (null != clientCredentials) {
                try {
                    return authenticateWithClientCredentials(clientCredentials, context,
                            identityProviderManager);
                } catch (OidcAuthException | io.quarkus.security.UnauthorizedException ex) {
                    log.warn(String.format(
                            "Exception trying to get an access token with client credentials with client id: %s",
                            clientCredentials.getLeft()), ex);
                    return oidcAuthenticationMechanism.authenticate(context,
                            identityProviderManager);
                }
            } else {
                return customAuthentication(context, identityProviderManager);
            }
        } else {
            return customAuthentication(context, identityProviderManager);
        }
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return oidcAuthenticationMechanism.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return oidcAuthenticationMechanism.getCredentialTypes();
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return oidcAuthenticationMechanism.getCredentialTransport(context);
    }

    private Uni<SecurityIdentity> customAuthentication(RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        if (authConfig.clientSecret.isEmpty()) {
            return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else {
            final Pair<String, String> credentialsFromContext = CredentialsHelper
                    .extractCredentialsFromContext(context);
            if (credentialsFromContext != null) {
                String jwtToken = obtainAccessTokenPasswordGrant(
                        credentialsFromContext.getLeft(), credentialsFromContext.getRight());
                if (jwtToken != null) {
                    context.request().headers().set("Authorization", "Bearer " + jwtToken);
                    return oidcAuthenticationMechanism.authenticate(context,
                            identityProviderManager);
                }
            } else {
                return oidcAuthenticationMechanism.authenticate(context,
                        identityProviderManager);
            }
        }
        return Uni.createFrom().nullItem();
    }

    private String obtainAccessTokenPasswordGrant(String username, String password) {
        MultiMap form = MultiMap.caseInsensitiveMultiMap()
                .add("grant_type", "password")
                .add("client_id", authConfig.clientId)
                .add("client_secret", authConfig.clientSecret.get())
                .add("username", username)
                .add("password", password);

        Buffer responseBody = webClient.postAbs(oidcTokenUrl)
                .sendForm(form)
                .toCompletionStage()
                .toCompletableFuture()
                .join()
                .bodyAsBuffer();

        if (responseBody == null) {
            return null;
        }
        JsonObject json = responseBody.toJsonObject();
        return json.getString("access_token");
    }

    private void setAuditLogger(RoutingContext context) {
        BiConsumer<RoutingContext, Throwable> failureHandler = context
                .get(QuarkusHttpUser.AUTH_FAILURE_HANDLER);
        BiConsumer<RoutingContext, Throwable> auditWrapper = (ctx, ex) -> {
            if (failureHandler != null) {
                failureHandler.accept(ctx, ex);
            }
            if (ctx.response().getStatusCode() >= 400) {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("method", ctx.request().method().name());
                metadata.put("path", ctx.request().path());
                metadata.put("response_code", String.valueOf(ctx.response().getStatusCode()));
                if (ex != null) {
                    metadata.put("error_msg", ex.getMessage());
                }

                auditLog.log(authConfig.auditLogPrefix, "authenticate",
                        AuditHttpRequestContext.FAILURE, metadata,
                        new AuditHttpRequestInfo() {
                            @Override
                            public String getSourceIp() {
                                return ctx.request().remoteAddress().toString();
                            }

                            @Override
                            public String getForwardedFor() {
                                return ctx.request().getHeader(
                                        AuditHttpRequestContext.X_FORWARDED_FOR_HEADER);
                            }
                        });
            }
        };

        context.put(QuarkusHttpUser.AUTH_FAILURE_HANDLER, auditWrapper);
    }

    private Uni<SecurityIdentity> authenticateWithClientCredentials(
            Pair<String, String> clientCredentials, RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        String jwtToken;
        String credentialsHash = getCredentialsHash(
                clientCredentials.getLeft() + clientCredentials.getRight());
        if (authFailureIsCached(credentialsHash)) {
            throw cachedAuthFailures.get(credentialsHash).getValue();
        } else if (accessTokenIsCached(credentialsHash)) {
            jwtToken = cachedAccessTokens.get(credentialsHash).getValue();
        } else {
            jwtToken = parent.getAccessToken(clientCredentials, credentialsHash,
                    cachedAccessTokens, cachedAuthFailures, oidcTokenUrl);
        }
        context.request().headers().set("Authorization", "Bearer " + jwtToken);
        return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
    }

    private boolean authFailureIsCached(String credentialsHash) {
        return cachedAuthFailures.containsKey(credentialsHash)
                && !cachedAuthFailures.get(credentialsHash).isExpired();
    }

    private boolean accessTokenIsCached(String credentialsHash) {
        return cachedAccessTokens.containsKey(credentialsHash)
                && !cachedAccessTokens.get(credentialsHash).isExpired();
    }

    private String getCredentialsHash(String credentials) {
        return DigestUtils.sha256Hex(credentials);
    }

    /**
     * Calculates how long to cache a given JWT. The token can be null (if authentication fails),
     * in which case the configured default expiration time will be used.
     */
    static Duration getAccessTokenExpiration(String jwtToken, AuthConfig authConfig,
            DefaultJWTParser jwtParser, Logger log) {
        if (jwtToken == null) {
            return Duration.ofMinutes(authConfig.accessTokenExpiration);
        }
        try {
            JsonWebToken parsedToken = jwtParser.parseOnly(jwtToken);

            Instant expirationInstant = Instant.ofEpochSecond(parsedToken.getExpirationTime())
                    .minusSeconds(authConfig.accessTokenExpirationOffset);
            Instant nowInstant = Instant.now();

            Duration timeUntilExpiration = Duration.between(nowInstant, expirationInstant);
            return timeUntilExpiration;
        } catch (ParseException e) {
            log.error("Error parsing JWT from auth server (client credentials grant).", e);
            return Duration.ofMinutes(authConfig.accessTokenExpiration);
        }
    }
}
