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

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_AUTH;

@Alternative
@Priority(1)
@ApplicationScoped
@Unremovable
public class AppAuthenticationMechanism implements HttpAuthenticationMechanism {

    @ConfigProperty(name = "quarkus.oidc.tenant-enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable auth", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.0.0.Final", studioAvailableSince = "1.0.0")
    boolean oidcAuthEnabled;

    // back to fake auth and use another property
    @Dynamic(label = "HTTP basic authentication", description = "When selected, users are permitted to authenticate using HTTP basic authentication (in addition to OAuth).", requires = "apicurio.authn.enabled=true")
    @ConfigProperty(name = "apicurio.authn.basic-client-credentials.enabled", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable basic auth client credentials", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    Supplier<Boolean> basicClientCredentialsAuthEnabled;

    @ConfigProperty(name = "quarkus.http.auth.basic", defaultValue = "false")
    @Info(category = CATEGORY_AUTH, description = "Enable basic auth", availableSince = "1.1.X-SNAPSHOT", registryAvailableSince = "3.X.X.Final", studioAvailableSince = "1.0.0")
    boolean basicAuthEnabled;

    // TODO: Add suffix?
    @ConfigProperty(name = "apicurio.authn.basic-client-credentials.cache-expiration", defaultValue = "10")
    @Info(category = CATEGORY_AUTH, description = "Default client credentials token expiration time in minutes.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.2.6.Final", studioAvailableSince = "1.0.0")
    Integer accessTokenExpiration;

    // TODO: Add suffix?
    @ConfigProperty(name = "apicurio.authn.basic-client-credentials.cache-expiration-offset", defaultValue = "10")
    @Info(category = CATEGORY_AUTH, description = "Client credentials token expiration offset from JWT expiration, in seconds.", availableSince = "0.2.7", registryAvailableSince = "2.5.9.Final", studioAvailableSince = "1.0.0")
    Integer accessTokenExpirationOffset;

    @ConfigProperty(name = "apicurio.authn.basic.scope")
    @Info(category = CATEGORY_AUTH, description = "Client credentials scope.", availableSince = "0.1.21-SNAPSHOT", registryAvailableSince = "2.5.0.Final", studioAvailableSince = "1.0.0")
    Optional<String> scope;

    @ConfigProperty(name = "apicurio.authn.audit.log.prefix", defaultValue = "audit")
    @Info(category = CATEGORY_AUTH, description = "Prefix used for application audit logging.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.2.6", studioAvailableSince = "1.0.0")

    String auditLogPrefix;

    @ConfigProperty(name = "quarkus.oidc.token-path", defaultValue = "")
    @Info(category = CATEGORY_AUTH, description = "Authentication server token endpoint.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    String authServerUrl;

    @ConfigProperty(name = "quarkus.oidc.client-secret")
    @Info(category = CATEGORY_AUTH, description = "Client secret used by the server for authentication.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.1.0.Final", studioAvailableSince = "1.0.0")
    Optional<String> clientSecret;

    @ConfigProperty(name = "quarkus.oidc.client-id", defaultValue = "")
    @Info(category = CATEGORY_AUTH, description = "Client identifier used by the server for authentication.", availableSince = "0.1.18-SNAPSHOT", registryAvailableSince = "2.0.0.Final", studioAvailableSince = "1.0.0")
    String clientId;

    @Inject
    BasicAuthenticationMechanism basicAuthenticationMechanism;

    @Inject
    OidcAuthenticationMechanism oidcAuthenticationMechanism;

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
        if (oidcAuthEnabled) {
            cachedAccessTokens = new ConcurrentHashMap<>();
            cachedAuthFailures = new ConcurrentHashMap<>();
            httpClient = new VertxHttpClientProvider(vertx).create(authServerUrl, Collections.emptyMap(),
                    null, new AuthErrorHandler());
        }
    }

    private HttpAuthenticationMechanism selectEnabledAuth() {
        if (basicAuthEnabled) {
            return basicAuthenticationMechanism;
        } else if (oidcAuthEnabled) {
            return oidcAuthenticationMechanism;
        } else {
            return null;
        }
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context,
            IdentityProviderManager identityProviderManager) {
        if (basicAuthEnabled) {
            return basicAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else if (oidcAuthEnabled) {
            setAuditLogger(context);
            if (basicClientCredentialsAuthEnabled.get()) {
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
        if (clientSecret.isEmpty()) {
            // if no secret is present, try to authenticate with oidc provider
            return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else {
            final Pair<String, String> credentialsFromContext = CredentialsHelper
                    .extractCredentialsFromContext(context);
            if (credentialsFromContext != null) {
                OidcAuth oidcAuth = new OidcAuth(httpClient, clientId, clientSecret.get());
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
            failureHandler.accept(ctx, ex);
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
                auditLog.log(auditLogPrefix, "authenticate", AuditHttpRequestContext.FAILURE, metadata,
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
                clientCredentials.getRight(), Duration.ofSeconds(1), scope.orElse(null));
        try {
            String jwtToken = oidcAuth.authenticate();// If we manage to get a token from basic credentials,
                                                      // try to authenticate it using the fetched token using
                                                      // the identity provider manager
            cachedAccessTokens.put(credentialsHash,
                    new WrappedValue<>(getAccessTokenExpiration(jwtToken), Instant.now(), jwtToken));
            return jwtToken;
        } catch (NotAuthorizedException | ForbiddenException ex) {
            cachedAuthFailures.put(credentialsHash,
                    new WrappedValue<>(getAccessTokenExpiration(null), Instant.now(), ex));
            throw ex;
        }
    }

    /**
     * Figure out how long to cache a given JWT. The token can be null (if authentication fails), in which
     * case the configured default expiration time will be used.
     */
    protected Duration getAccessTokenExpiration(String jwtToken) {
        if (jwtToken == null) {
            return Duration.ofMinutes(accessTokenExpiration);
        }
        try {
            JsonWebToken parsedToken = jwtParser.parseOnly(jwtToken);

            // Convert the expiration to an Instant, and subtract the offset (we want to stop using it N
            // seconds before it expires).
            Instant expirationInstant = Instant.ofEpochSecond(parsedToken.getExpirationTime())
                    .minusSeconds(accessTokenExpirationOffset);
            Instant nowInstant = Instant.now();

            // Convert the expiration instant to a duration
            Duration timeUntilExpiration = Duration.between(nowInstant, expirationInstant);
            return timeUntilExpiration;
        } catch (ParseException e) {
            // Could not parse the JWT, just return the default expiration.
            log.error("Error parsing JWT from auth server (client credentials grant).", e);
            return Duration.ofMinutes(accessTokenExpiration);
        }
    }

    private String getCredentialsHash(String credentials) {
        return DigestUtils.sha256Hex(credentials);
    }
}
