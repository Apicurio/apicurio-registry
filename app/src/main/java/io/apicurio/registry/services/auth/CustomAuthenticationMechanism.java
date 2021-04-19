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

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkus.oidc.AccessTokenCredential;
import io.quarkus.oidc.runtime.BearerAuthenticationMechanism;
import io.quarkus.oidc.runtime.OidcAuthenticationMechanism;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.inject.Inject;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Alternative
@Priority(1)
@ApplicationScoped
public class CustomAuthenticationMechanism implements HttpAuthenticationMechanism {


    @Inject
    OidcAuthenticationMechanism oidcAuthenticationMechanism;

    @ConfigProperty(name = "registry.keycloak.url")
    String authServerUrl;

    @ConfigProperty(name = "registry.keycloak.realm")
    String authRealm;

    @ConfigProperty(name = "quarkus.oidc.client-secret")
    String clientSecret;

    @ConfigProperty(name = "quarkus.oidc.client-id")
    String clientId;

    @ConfigProperty(name = "registry.auth.enabled")
    boolean authEnabled;

    private final BearerAuthenticationMechanism bearerAuth = new BearerAuthenticationMechanism();

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (authEnabled) {
            //Extracts username, password pair from the header and request a token to keycloak
            String jwtToken = new BearerTokenExtractor(context, authServerUrl, authRealm, clientId, clientSecret).getBearerToken();
            if (jwtToken != null) {
                //If we manage to get a token from basic credentials, try to authenticate it using the fetched token using the identity provider manager
                return identityProviderManager
                        .authenticate(new TokenAuthenticationRequest(new AccessTokenCredential(jwtToken, context)));
            } else {
                return oidcAuthenticationMechanism.authenticate(context, identityProviderManager);
            }
        } else {
            return Uni.createFrom().nullItem();
        }
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


    //Extracts username, password pair from header if exists and request an access token to keycloak using those credentials
    //Once Quarkus support both basic and bearer working at the same time against Keycloak, we can remove this
    private static class BearerTokenExtractor {

        private final String BASIC = "basic";
        private final String BASIC_PREFIX = BASIC + " ";
        private final String LOWERCASE_BASIC_PREFIX = BASIC_PREFIX.toLowerCase(Locale.ENGLISH);
        private final int PREFIX_LENGTH = BASIC_PREFIX.length();
        private final Charset charset;
        private final AuthzClient keycloakClient;
        private final RoutingContext context;

        public BearerTokenExtractor(RoutingContext context, final String authServerUrl, String realmName, String clientId, String clientSecret) {
            this.context = context;
            this.charset = StandardCharsets.UTF_8;
            final HashMap<String, Object> credentials = new HashMap<>();
            credentials.put("secret", clientSecret);
            final Configuration keycloakConfiguration = new Configuration(authServerUrl, realmName, clientId, credentials, null);
            this.keycloakClient = AuthzClient.create(keycloakConfiguration);
        }

        protected String getBearerToken() {
            List<String> authHeaders = context.request().headers().getAll(HttpHeaderNames.AUTHORIZATION);
            if (authHeaders != null) {
                for (String current : authHeaders) {
                    if (current.toLowerCase(Locale.ENGLISH).startsWith(LOWERCASE_BASIC_PREFIX)) {

                        String base64Challenge = current.substring(PREFIX_LENGTH);
                        String plainChallenge;
                        byte[] decode = Base64.getDecoder().decode(base64Challenge);

                        plainChallenge = new String(decode, charset);
                        int colonPos;
                        String COLON = ":";
                        if ((colonPos = plainChallenge.indexOf(COLON)) > -1) {
                            String userName = plainChallenge.substring(0, colonPos);
                            String password = plainChallenge.substring(colonPos + 1);

                            return authenticateRequest(userName, password);
                        }
                    }
                }
            }
            //XX Intended null return
            return null;
        }

        private String authenticateRequest(String username, String password) {

            return keycloakClient.obtainAccessToken(username, password).getToken();
        }
    }
}
