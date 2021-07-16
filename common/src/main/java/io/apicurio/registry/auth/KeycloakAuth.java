/*
 * Copyright 2020 Red Hat
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

import org.keycloak.TokenVerifier;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.Time;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * @author carnalca@redhat.com
 */
public class KeycloakAuth extends ClientCredentialsAuth {

    private final AuthzClient keycloak;
    private AccessTokenResponse accessToken;

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuth.class);

    public KeycloakAuth(String serverUrl, String realm, String clientId, String clientSecret) {
        super(serverUrl, realm, clientId, clientSecret);
        final HashMap<String, Object> credentials = new HashMap<>();
        credentials.put("secret", clientSecret);
        final Configuration configuration = new Configuration(serverUrl, realm, clientId, credentials, null);
        this.keycloak = AuthzClient.create(configuration);
    }

    /**
     * @see io.apicurio.registry.auth.Auth#apply(java.util.Map)
     */
    @Override
    public void apply(Map<String, String> requestHeaders) {
        if (isAccessTokenRequired()) {
            this.accessToken = this.keycloak.obtainAccessToken();
        }
        requestHeaders.put("Authorization", BEARER + accessToken.getToken());
    }

    private boolean isAccessTokenRequired() {
        return null == accessToken || isTokenExpired(accessToken.getToken());
    }

    private boolean isTokenExpired(String token) {
        try {
            final AccessToken accessToken = TokenVerifier.create(token, AccessToken.class).getToken();
            return (accessToken.getExp() != null && accessToken.getExp() != 0L) && (long) Time.currentTime() > accessToken.getExp();
        } catch (VerificationException e) {
            log.info("Error verifying access token: ", e);
            throw new IllegalStateException(e);
        }
    }

    public static class Builder {
        private String serverUrl;
        private String realm;
        private String clientId;
        private String clientSecret;

        public Builder() {
        }

        public Builder withRealm(String realm) {
            this.realm = realm;
            return this;
        }

        public Builder withClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder withClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder withServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public KeycloakAuth build() {
            return new KeycloakAuth(this.serverUrl, this.realm, this.clientId, this.clientSecret);
        }
    }

}
