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
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import java.util.Collections;
import java.util.Set;

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

    @ConfigProperty(name = "registry.keycloak.url")
    String authServerUrl;

    @ConfigProperty(name = "registry.keycloak.realm")
    String authRealm;

    private String authServerUrlWithRealm;

    @PostConstruct
    public void init() {
        this.authServerUrlWithRealm = authServerUrl + "/realms/" + authRealm;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (authEnabled) {
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
        final String jwtToken = new OidcAuth(authServerUrlWithRealm, clientCredentials.getLeft(), clientCredentials.getRight()).authenticate();//If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
        return identityProviderManager
                .authenticate(new TokenAuthenticationRequest(new AccessTokenCredential(jwtToken, context)));
    }
}
