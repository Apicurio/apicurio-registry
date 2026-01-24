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

import io.apicurio.registry.logging.audit.AuditHttpRequestContext;
import io.apicurio.registry.logging.audit.AuditHttpRequestInfo;
import io.apicurio.registry.logging.audit.AuditLogService;
import io.apicurio.rest.client.VertxHttpClientProvider;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.AuthException;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.error.ApicurioRestClientException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.*;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

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
    Vertx vertx;

    @Inject
    DefaultJWTParser jwtParser;

    private ApicurioHttpClient httpClient;

    private ConcurrentHashMap<String, WrappedValue<String>> cachedAccessTokens;
    private ConcurrentHashMap<String, WrappedValue<ApicurioRestClientException>> cachedAuthFailures;

    @PostConstruct
    public void init() {
        if (authConfig.oidcAuthEnabled) {
            cachedAccessTokens = new ConcurrentHashMap<>();
            cachedAuthFailures = new ConcurrentHashMap<>();
            String oidcTokenUrl;
            if (authConfig.oidcTokenPath.startsWith("http")) {
                oidcTokenUrl = authConfig.oidcTokenPath;
            } else {
                oidcTokenUrl = authConfig.authServerUrl + authConfig.oidcTokenPath;
            }

            httpClient = new VertxHttpClientProvider(vertx).create(oidcTokenUrl, Collections.emptyMap(),
                    null, new AuthErrorHandler());
        }
    }

    private HttpAuthenticationMechanism selectEnabledAuth() {
        if (authConfig.basicAuthEnabled) {
            return basicAuthenticationMechanism;
        } else if (authConfig.oidcAuthEnabled) {
            return oidcAuthenticationMechanism;
        } else if (authConfig.proxyHeaderAuthEnabled) {
            return proxyHeaderAuthenticationMechanism;
        } else {
            return null;
        }
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
                                              IdentityProviderManager identityProviderManager) {
        if (authConfig.basicAuthEnabled) {
            return basicAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else if (authConfig.proxyHeaderAuthEnabled) {
            return proxyHeaderAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else if (authConfig.oidcAuthEnabled) {
            setAuditLogger(context);
            if (authConfig.basicClientCredentialsAuthEnabled.get()) {
                final Pair<String, String> clientCredentials = CredentialsHelper
                        .extractCredentialsFromContext(context);
                if (null != clientCredentials) {
                    try {
                        return authenticateWithClientCredentials(clientCredentials, context,
                                identityProviderManager);
                    } catch (AuthException | NotAuthorizedException ex) {
                        log.warn(String.format(
                                "Exception trying to get an access token with client credentials with client id: %s",
                                clientCredentials.getLeft()), ex);
                        return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
                    }
                } else {
                    return customAuthentication(context, identityProviderManager);
                }
            } else {
                // Once we're done with it in the auth layer, the context must be cleared.
                return customAuthentication(context, identityProviderManager);
            }
        } else {
            return Uni.createFrom().nullItem();
        }
    }

    public Uni<SecurityIdentity> customAuthentication(RoutingContext context,
                                                      IdentityProviderManager identityProviderManager) {
        if (authConfig.clientSecret.isEmpty()) {
            // if no secret is present, try to authenticate with oidc provider
            return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else {
            final Pair<String, String> credentialsFromContext = CredentialsHelper
                    .extractCredentialsFromContext(context);
            if (credentialsFromContext != null) {
                OidcAuth oidcAuth = new OidcAuth(httpClient, authConfig.clientId, authConfig.clientSecret.get());
                String jwtToken = oidcAuth.obtainAccessTokenPasswordGrant(credentialsFromContext.getLeft(),
                        credentialsFromContext.getRight());
                if (jwtToken != null) {
                    // If we manage to get a token from basic credentials, try to authenticate it using the
                    // fetched token using the identity provider manager
                    context.request().headers().set("Authorization", "Bearer " + jwtToken);
                    return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
                }
            } else {
                // If we cannot get a token, then try to authenticate using oidc provider as last resource
                return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
            }
        }
        return Uni.createFrom().nullItem();
    }

    private void setAuditLogger(RoutingContext context) {
        BiConsumer<RoutingContext, Throwable> failureHandler = context
                .get(QuarkusHttpUser.AUTH_FAILURE_HANDLER);
        BiConsumer<RoutingContext, Throwable> auditWrapper = (ctx, ex) -> {
            // this sends the http response
            if (failureHandler != null) {
                failureHandler.accept(ctx, ex);
            }
            // if it was an error response log it
            if (ctx.response().getStatusCode() >= 400) {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("method", ctx.request().method().name());
                metadata.put("path", ctx.request().path());
                metadata.put("response_code", String.valueOf(ctx.response().getStatusCode()));
                if (ex != null) {
                    metadata.put("error_msg", ex.getMessage());
                }

                // request context for AuditHttpRequestContext does not exist at this point
                auditLog.log(authConfig.auditLogPrefix, "authenticate", AuditHttpRequestContext.FAILURE, metadata,
                        new AuditHttpRequestInfo() {
                            @Override
                            public String getSourceIp() {
                                return ctx.request().remoteAddress().toString();
                            }

                            @Override
                            public String getForwardedFor() {
                                return ctx.request()
                                        .getHeader(AuditHttpRequestContext.X_FORWARDED_FOR_HEADER);
                            }
                        });
            }
        };

        context.put(QuarkusHttpUser.AUTH_FAILURE_HANDLER, auditWrapper);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        var enabledAuth = selectEnabledAuth();
        if (enabledAuth != null) {
            return enabledAuth.getChallenge(context);
        } else {
            return Uni.createFrom().nullItem();
        }
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        Set<Class<? extends AuthenticationRequest>> credentialTypes = new HashSet<>();
        credentialTypes.addAll(oidcAuthenticationMechanism.getCredentialTypes());
        credentialTypes.addAll(basicAuthenticationMechanism.getCredentialTypes());
        credentialTypes.addAll(proxyHeaderAuthenticationMechanism.getCredentialTypes());
        return credentialTypes;
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        var enabledAuth = selectEnabledAuth();
        if (enabledAuth != null) {
            return enabledAuth.getCredentialTransport(context);
        } else {
            return Uni.createFrom().nullItem();
        }
    }

    private Uni<SecurityIdentity> authenticateWithClientCredentials(Pair<String, String> clientCredentials,
                                                                    RoutingContext context, IdentityProviderManager identityProviderManager) {
        String jwtToken;
        String credentialsHash = getCredentialsHash(
                clientCredentials.getLeft() + clientCredentials.getRight());
        if (authFailureIsCached(credentialsHash)) {
            throw cachedAuthFailures.get(credentialsHash).getValue();
        } else if (accessTokenIsCached(credentialsHash)) {
            jwtToken = cachedAccessTokens.get(credentialsHash).getValue();
        } else {
            jwtToken = getAccessToken(clientCredentials, credentialsHash);
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

    @Retry(retryOn = AuthException.class, maxRetries = 4, delay = 1, delayUnit = ChronoUnit.SECONDS)
    public String getAccessToken(Pair<String, String> clientCredentials, String credentialsHash) {
        OidcAuth oidcAuth = new OidcAuth(httpClient, clientCredentials.getLeft(),
                clientCredentials.getRight(), Duration.ofSeconds(1), authConfig.scope.orElse(null));
        try {
            String jwtToken = oidcAuth.authenticate();
            // If we manage to get a token from basic credentials,
            // try to authenticate it using the fetched token using
            // the identity provider manager
            cachedAccessTokens.put(credentialsHash,
                    new WrappedValue<>(getAccessTokenExpiration(jwtToken), Instant.now(), jwtToken));
            return jwtToken;
        } catch (NotAuthorizedException | ForbiddenException ex) {
            cachedAuthFailures.put(credentialsHash,
                    new WrappedValue<>(getAccessTokenExpiration(null), Instant.now(), ex));
            throw ex;
        } catch (RuntimeException e) {
            throw e;
        }
    }

    /**
     * Figure out how long to cache a given JWT. The token can be null (if authentication fails), in which
     * case the configured default expiration time will be used.
     */
    protected Duration getAccessTokenExpiration(String jwtToken) {
        if (jwtToken == null) {
            return Duration.ofMinutes(authConfig.accessTokenExpiration);
        }
        try {
            JsonWebToken parsedToken = jwtParser.parseOnly(jwtToken);

            // Convert the expiration to an Instant, and subtract the offset (we want to stop using it N
            // seconds before it expires).
            Instant expirationInstant = Instant.ofEpochSecond(parsedToken.getExpirationTime())
                    .minusSeconds(authConfig.accessTokenExpirationOffset);
            Instant nowInstant = Instant.now();

            // Convert the expiration instant to a duration
            Duration timeUntilExpiration = Duration.between(nowInstant, expirationInstant);
            return timeUntilExpiration;
        } catch (ParseException e) {
            // Could not parse the JWT, just return the default expiration.
            log.error("Error parsing JWT from auth server (client credentials grant).", e);
            return Duration.ofMinutes(authConfig.accessTokenExpiration);
        }
    }

    private String getCredentialsHash(String credentials) {
        return DigestUtils.sha256Hex(credentials);
    }
}
