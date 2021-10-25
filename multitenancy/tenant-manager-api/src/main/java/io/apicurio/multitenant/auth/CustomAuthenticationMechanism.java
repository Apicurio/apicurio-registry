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

package io.apicurio.multitenant.auth;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;

import org.slf4j.Logger;

import io.apicurio.multitenant.logging.audit.AuditHttpRequestContext;
import io.apicurio.multitenant.logging.audit.AuditHttpRequestInfo;
import io.apicurio.multitenant.logging.audit.AuditLogService;
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

/**
 * Custom HttpAuthenticationMechanism that simply wraps OidcAuthenticationMechanism.
 * The only purpose of this HttpAuthenticationMechanism is to handle authentication errors in order to generate audit logs.
 *
 * @author Fabian Martinez
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class CustomAuthenticationMechanism implements HttpAuthenticationMechanism {

    @Inject
    Logger log;

    @Inject
    OidcAuthenticationMechanism oidcAuthenticationMechanism;

    @Inject
    AuditLogService auditLog;

    private final BearerAuthenticationMechanism bearerAuth = new BearerAuthenticationMechanism();;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {


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
                auditLog.log("authenticate", AuditHttpRequestContext.FAILURE, metadata, new AuditHttpRequestInfo() {
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

        return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
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
}
