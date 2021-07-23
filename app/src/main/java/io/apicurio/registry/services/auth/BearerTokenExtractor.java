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
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

//Extracts username, password pair from header if exists and request an access token to keycloak using those credentials
//Once Quarkus support both basic and bearer working at the same time against Keycloak, we can remove this
public class BearerTokenExtractor {

    private static final String BASIC = "basic";
    private static final String BASIC_PREFIX = BASIC + " ";
    private static final String LOWERCASE_BASIC_PREFIX = BASIC_PREFIX.toLowerCase(Locale.ENGLISH);
    private static final int PREFIX_LENGTH = BASIC_PREFIX.length();
    private static final Charset charset = StandardCharsets.UTF_8;
    private final AuthzClient keycloakClient;
    private final RoutingContext context;

    public BearerTokenExtractor(RoutingContext context, final String authServerUrl, String realmName, String clientId, String clientSecret) {
        this.context = context;
        final HashMap<String, Object> credentials = new HashMap<>();
        credentials.put("secret", clientSecret);
        final Configuration keycloakConfiguration = new Configuration(authServerUrl, realmName, clientId, credentials, null);
        this.keycloakClient = AuthzClient.create(keycloakConfiguration);
    }

    protected String getBearerToken() {
        final Pair<String, String> credentials = extractCredentialsFromContext(this.context);
        if (credentials != null) {
            return authenticateRequest(credentials.getLeft(), credentials.getRight());
        } else {
            //XX Intended null return
            return null;
        }
    }

    public static Pair<String, String> extractCredentialsFromContext(RoutingContext context) {
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
                        return new ImmutablePair<>(userName, password);
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
