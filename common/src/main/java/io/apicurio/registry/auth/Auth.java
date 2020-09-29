package io.apicurio.registry.auth;


public class Auth {

    private final AuthConfig config;

    public Auth(AuthConfig config) {
        this.config = config;
    }

    public AuthStrategy getAuthStrategy() {
        if (this.config.getProvider() == AuthProvider.KEYCLOAK) {
            return new KeycloakAuth(config);
        }
        return null;
    }


}
