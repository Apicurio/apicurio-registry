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

package io.apicurio.registry.auth.config;


/**
 * @author carnalca@redhat.com
 */
public class ClientCredentialsConfig implements CredentialsConfig {

    private final String serverUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public ClientCredentialsConfig(String serverUrl, String realm, String clientId, String clientSecret) {
        this.serverUrl = serverUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
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

        public ClientCredentialsConfig build(){
            return new ClientCredentialsConfig(this.serverUrl, this.realm, this.clientId, this.clientSecret);
        }
    }

}
