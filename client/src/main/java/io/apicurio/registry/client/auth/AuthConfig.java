package io.apicurio.registry.client.auth;

import org.keycloak.admin.client.Config;

public class AuthConfig extends Config {
    private AuthProvider provider;

    public AuthProvider getProvider() {
        return provider;
    }

    public void setProvider(AuthProvider provider) {
        this.provider = provider;
    }

    public AuthConfig(String serverUrl, String realm, String username, String password, String clientId, String clientSecret, AuthProvider provider) {
        super(serverUrl, realm, username, password, clientId, clientSecret);
        this.provider = provider;
    }

    public AuthConfig(String serverUrl, String realm, String username, String password, String clientId, String clientSecret) {
        super(serverUrl, realm, username, password, clientId, clientSecret);
    }

    public AuthConfig(String serverUrl, String realm, String username, String password, String clientId, String clientSecret, String grantType) {
        super(serverUrl, realm, username, password, clientId, clientSecret, grantType);
    }

    public static class Builder {
        private String serverUrl;
        private String realm;
        private String username;
        private String password;
        private String clientId;
        private String clientSecret;
        private AuthConfig config;
        private AuthProvider provider;

        public Builder(AuthProvider provider) {
            this.provider = provider;
        }

        public Builder setRealm(String realm) {
            this.realm = realm;
            return this;
        }

        public Builder setUsername(String username) {
            this.username = username;
            return this;
        }

        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        public Builder setClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public AuthConfig Build(){
            return this.config = new AuthConfig(this.serverUrl, this.realm, this.username, this.password, this.clientId, this.clientSecret, this.provider);
        }
    }

}
