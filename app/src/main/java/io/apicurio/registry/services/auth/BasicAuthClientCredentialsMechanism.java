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

import io.apicurio.registry.logging.audit.AuditHttpRequestContext;
import io.apicurio.registry.logging.audit.AuditHttpRequestInfo;
import io.apicurio.registry.logging.audit.AuditLogService;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.security.AuthenticationFailedException;
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
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

@Alternative
@Priority(1)
@ApplicationScoped
public class BasicAuthClientCredentialsMechanism implements HttpAuthenticationMechanism {

    @Inject
    CustomAuthenticationMechanism customAuthenticationMechanism;

    @ConfigProperty(name = "registry.auth.enabled")
    boolean authEnabled;

    @ConfigProperty(name = "registry.auth.basic-auth-client-credentials.enabled")
    boolean fakeBasicAuthEnabled;

    @ConfigProperty(name = "registry.auth.token.endpoint")
    String authServerUrl;

    @Inject
    AuditLogService auditLog;

    @Inject
    Logger log;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (authEnabled) {
            setAuditLogger(context);
            if (fakeBasicAuthEnabled) {
                final Pair<String, String> clientCredentials = CredentialsHelper.extractCredentialsFromContext(context);
                if (null != clientCredentials) {
                    try {
                        return authenticateWithClientCredentials(clientCredentials, context, identityProviderManager);
                    } catch (NotAuthorizedException ex) {
                        //Ignore exception, wrong credentials passed
                        throw new AuthenticationFailedException();
                    }
                } else {
                    return customAuthenticationMechanism.authenticate(context, identityProviderManager);
                }
            } else {
                return customAuthenticationMechanism.authenticate(context, identityProviderManager);
            }
        } else {
            return Uni.createFrom().nullItem();
        }
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
        return customAuthenticationMechanism.getChallenge(context);
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
        OidcAuth oidcAuth = new OidcAuth(authServerUrl, clientCredentials.getLeft(), clientCredentials.getRight(), Optional.empty());
        try {
            final String jwtToken = oidcAuth.authenticate();//If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
            return identityProviderManager
                    .authenticate(new TokenAuthenticationRequest(new AccessTokenCredential(jwtToken, context)));
        } finally {
            oidcAuth.close();
        }
    }
}
