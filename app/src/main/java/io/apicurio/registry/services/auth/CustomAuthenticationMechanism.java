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

package io.apicurio.registry.services.auth;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;
import io.apicurio.common.apps.logging.audit.AuditHttpRequestContext;
import io.apicurio.common.apps.logging.audit.AuditHttpRequestInfo;
import io.apicurio.common.apps.logging.audit.AuditLogService;
import io.apicurio.rest.client.JdkHttpClientProvider;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.auth.exception.AuthException;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.apicurio.rest.client.error.ApicurioRestClientException;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.quarkus.oidc.runtime.BearerAuthenticationMechanism;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@Alternative
@Priority(1)
@ApplicationScoped
public class CustomAuthenticationMechanism implements HttpAuthenticationMechanism {

    @ConfigProperty(name = "registry.auth.enabled")
    @Info(category = "auth", description = "Enable auth", availableSince = "2.2.6-SNAPSHOT")
    boolean authEnabled;

    @Dynamic(label = "HTTP basic authentication", description = "When selected, users are permitted to authenticate using HTTP basic authentication (in addition to OAuth).", requires = "registry.auth.enabled=true")
    @ConfigProperty(name = "registry.auth.basic-auth-client-credentials.enabled", defaultValue = "false")
    @Info(category = "auth", description = "Enable basic auth client credentials", availableSince = "2.1.0.Final")
    Supplier<Boolean> fakeBasicAuthEnabled;

    @ConfigProperty(name = "registry.auth.basic-auth-client-credentials.cache-expiration", defaultValue = "10")
    @Info(category = "auth", description = "Client credentials token expiration time", availableSince = "2.2.6.Final")
    Integer accessTokenExpiration;

    @ConfigProperty(name = "registry.auth.token.endpoint")
    @Info(category = "auth", description = "Auth token endpoint", availableSince = "2.1.0.Final")
    String authServerUrl;

    @ConfigProperty(name = "registry.auth.client-secret")
    @Info(category = "auth", description = "Auth client secret", availableSince = "2.1.0.Final")
    Optional<String> clientSecret;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    @Info(category = "auth", description = "OIDC client ID", availableSince = "2.0.0.Final")
    String clientId;

    @Inject
    OidcAuthenticationMechanism oidcAuthenticationMechanism;

    @Inject
    AuditLogService auditLog;

    @Inject
    Logger log;

    private BearerAuthenticationMechanism bearerAuth;
    private ApicurioHttpClient httpClient;

    private ConcurrentHashMap<String, WrappedValue<String>> cachedAccessTokens;
    private ConcurrentHashMap<String, WrappedValue<ApicurioRestClientException>> cachedAuthFailures;

    @PostConstruct
    public void init() {
        if (authEnabled) {
            cachedAccessTokens = new ConcurrentHashMap<>();
            cachedAuthFailures = new ConcurrentHashMap<>();
            httpClient = new JdkHttpClientProvider().create(authServerUrl, Collections.emptyMap(), null, new AuthErrorHandler());
            bearerAuth = new BearerAuthenticationMechanism();
        }
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (authEnabled) {
            setAuditLogger(context);
            if (fakeBasicAuthEnabled.get()) {
                final Pair<String, String> clientCredentials = CredentialsHelper.extractCredentialsFromContext(context);
                if (null != clientCredentials) {
                    try {
                        return authenticateWithClientCredentials(clientCredentials, context, identityProviderManager);
                    } catch (AuthException | NotAuthorizedException ex) {
                        log.warn(String.format("Exception trying to get an access token with client credentials with client id: %s", clientCredentials.getLeft()), ex);
                        return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
                    }
                } else {
                    return customAuthentication(context, identityProviderManager);
                }
            } else {
                //Once we're done with it in the auth layer, the context must be cleared.
                return customAuthentication(context, identityProviderManager);
            }
        } else {
            return Uni.createFrom().nullItem();
        }
    }

    public Uni<SecurityIdentity> customAuthentication(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (clientSecret.isEmpty()) {
            //if no secret is present, try to authenticate with oidc provider
            return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
        } else {
            final Pair<String, String> credentialsFromContext = CredentialsHelper.extractCredentialsFromContext(context);
            if (credentialsFromContext != null) {
                OidcAuth oidcAuth = new OidcAuth(httpClient, clientId, clientSecret.get());
                String jwtToken = oidcAuth.obtainAccessTokenPasswordGrant(credentialsFromContext.getLeft(), credentialsFromContext.getRight());
                oidcAuth.close();
                if (jwtToken != null) {
                    //If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
                    context.request().headers().set("Authorization", "Bearer " + jwtToken);
                    return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
                }
            } else {
                //If we cannot get a token, then try to authenticate using oidc provider as last resource
                return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
            }
        }
        return Uni.createFrom().nullItem();
    }

    private void setAuditLogger(RoutingContext context) {
        BiConsumer<RoutingContext, Throwable> failureHandler = context.get(QuarkusHttpUser.AUTH_FAILURE_HANDLER);
        BiConsumer<RoutingContext, Throwable> auditWrapper = (ctx, ex) -> {
            //this sends the http response
            failureHandler.accept(ctx, ex);
            //if it was an error response log it
            if (ctx.response().getStatusCode() >= 400) {
                Map<String, String> metadata = new HashMap<>();
                metadata.put("method", ctx.request().method().name());
                metadata.put("path", ctx.request().path());
                metadata.put("response_code", String.valueOf(ctx.response().getStatusCode()));
                if (ex != null) {
                    metadata.put("error_msg", ex.getMessage());
                }

                //request context for AuditHttpRequestContext does not exist at this point
                auditLog.log("registry.audit", "authenticate", AuditHttpRequestContext.FAILURE, metadata, new AuditHttpRequestInfo() {
                    @Override
                    public String getSourceIp() {
                        return ctx.request().remoteAddress().toString();
                    }

                    @Override
                    public String getForwardedFor() {
                        return ctx.request().getHeader(AuditHttpRequestContext.X_FORWARDED_FOR_HEADER);
                    }
                });
            }
        };

        context.put(QuarkusHttpUser.AUTH_FAILURE_HANDLER, auditWrapper);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return bearerAuth.getChallenge(context);
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(TokenAuthenticationRequest.class);
    }

    @Override
    public HttpCredentialTransport getCredentialTransport() {
        return new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "bearer");
    }

    private Uni<SecurityIdentity> authenticateWithClientCredentials(Pair<String, String> clientCredentials, RoutingContext context, IdentityProviderManager identityProviderManager) {
        String jwtToken;
        String credentialsHash = getCredentialsHash(clientCredentials.getLeft() + clientCredentials.getRight());
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
        return cachedAuthFailures.containsKey(credentialsHash) && !cachedAuthFailures.get(credentialsHash).isExpired();
    }

    private boolean accessTokenIsCached(String credentialsHash) {
        return cachedAccessTokens.containsKey(credentialsHash) && !cachedAccessTokens.get(credentialsHash).isExpired();
    }

    @Retry(retryOn = AuthException.class, maxRetries = 4)
    public String getAccessToken(Pair<String, String> clientCredentials, String credentialsHash) {
        OidcAuth oidcAuth = new OidcAuth(httpClient, clientCredentials.getLeft(), clientCredentials.getRight());
        try {
            String jwtToken = oidcAuth.authenticate();//If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
            cachedAccessTokens.put(credentialsHash, new WrappedValue<>(Duration.ofMinutes(accessTokenExpiration), Instant.now(), jwtToken));
            return jwtToken;
        } catch (NotAuthorizedException | ForbiddenException ex) {
            cachedAuthFailures.put(credentialsHash, new WrappedValue<>(Duration.ofMinutes(accessTokenExpiration), Instant.now(), ex));
            throw ex;
        }
    }

    private String getCredentialsHash(String credentials) {
        return DigestUtils.sha256Hex(credentials);
    }
}
