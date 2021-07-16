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
import org.keycloak.authorization.client.util.Http;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.Time;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;

import java.util.HashMap;
import java.util.Map;

public class Auth {

    private final AuthzClient keycloak;
    private static final String BEARER = "Bearer ";
    private final String clientId;
    private final String clientSecret;
    private AccessTokenResponse accessToken;


    public Auth(String serverUrl, String realm, String clientId, String clientSecret) {
        final HashMap<String, Object> credentials = new HashMap<>();
        credentials.put("secret", clientSecret);
        final Configuration configuration = new Configuration(serverUrl, realm, clientId, credentials, null);
        this.keycloak = AuthzClient.create(configuration);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String obtainAuthorizationValue() throws VerificationException {
        if (isAccessTokenRequired()) {
            this.accessToken = this.keycloak.obtainAccessToken();
        } else if (isTokenExpired(this.accessToken.getToken())) {
            this.accessToken = refreshToken(accessToken.getRefreshToken());
        }
        return BEARER + accessToken.getToken();
    }

    private boolean isAccessTokenRequired() throws VerificationException {
        return null == accessToken || accessToken.getRefreshToken() == null || isTokenExpired(accessToken.getRefreshToken());
    }

    private boolean isTokenExpired(String token) throws VerificationException {
        final AccessToken accessToken = TokenVerifier.create(token, AccessToken.class).getToken();
        return (accessToken.getExp() != null && accessToken.getExp() != 0L) && (long) Time.currentTime() > accessToken.getExp();
    }

    public AccessTokenResponse refreshToken(String refreshToken) {
        final String url = keycloak.getConfiguration().getAuthServerUrl() + "/realms/" + keycloak.getConfiguration().getRealm() + "/protocol/openid-connect/token";
        final Http http = new Http(keycloak.getConfiguration(), (params, headers) -> {
        });

        return http.<AccessTokenResponse>post(url)
                .authentication()
                .client()
                .form()
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken)
                .param("client_id", this.clientId)
                .param("client_secret", this.clientSecret)
                .response()
                .json(AccessTokenResponse.class)
                .execute();
    }
}
