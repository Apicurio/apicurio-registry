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

package io.apicurio.multitenant.client;

import org.keycloak.TokenVerifier;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.Time;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;

import java.util.HashMap;

public class Auth {

    private final AuthzClient keycloak;
    private static final String BEARER = "Bearer ";
    private AccessTokenResponse accessToken;


    public Auth(String serverUrl, String realm, String clientId, String clientSecret) {
        final HashMap<String, Object> credentials = new HashMap<>();
        credentials.put("secret", clientSecret);
        final Configuration configuration = new Configuration(serverUrl, realm, clientId, credentials, null);
        this.keycloak = AuthzClient.create(configuration);
    }

    public String obtainAuthorizationValue() throws VerificationException {
        if (isAccessTokenRequired()) {
            this.accessToken = this.keycloak.obtainAccessToken();
        }
        return BEARER + accessToken.getToken();
    }

    private boolean isAccessTokenRequired() throws VerificationException {
        return null == accessToken || isTokenExpired(this.accessToken.getToken());
    }

    private boolean isTokenExpired(String token) throws VerificationException {
        final AccessToken accessToken = TokenVerifier.create(token, AccessToken.class).getToken();
        return (accessToken.getExp() != null && accessToken.getExp() != 0L) && (long) Time.currentTime() > accessToken.getExp();
    }
}
