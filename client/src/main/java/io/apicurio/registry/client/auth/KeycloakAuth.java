package io.apicurio.registry.client.auth;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

public class KeycloakAuth implements AuthStrategy {

    public static final String BEARER = "Bearer ";
    private Keycloak keycloak;

    public KeycloakAuth(AuthConfig config) {
        this.keycloak = KeycloakBuilder.builder()
                .serverUrl(config.getServerUrl())
                .username(config.getUsername())
                .password(config.getPassword())
                .realm(config.getRealm())
                .clientId(config.getClientId())
                .build();
    }

    @Override
    public String getToken() {
        return BEARER+this.keycloak.tokenManager().getAccessToken().getToken();
    }
}
