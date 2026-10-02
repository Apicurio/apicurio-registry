package io.apicurio.registry.utils.tests;

import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.id.ClientID;
import no.nav.security.mock.oauth2.token.OAuth2TokenCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token callback that attaches a {@code groups} claim to issued tokens based on the
 * requesting client_id, so Apicurio's role-based authorization (which reads roles from
 * the token's {@code groups} claim by default) has something to authorize against.
 *
 * <p>Mirrors the realm-role-to-client mapping baked into utils/tests/src/main/resources/
 * realm.json for the real Keycloak-based tests (admin-client -> sr-admin, developer-client
 * -> sr-developer, readonly-client -> sr-readonly).
 */
public class RoleClaimTokenCallback implements OAuth2TokenCallback {

    private final String issuerId;
    private final Map<String, List<String>> clientIdToRoles;
    private final long tokenExpirySeconds;

    public RoleClaimTokenCallback(String issuerId, Map<String, List<String>> clientIdToRoles,
            long tokenExpirySeconds) {
        this.issuerId = issuerId;
        this.clientIdToRoles = clientIdToRoles;
        this.tokenExpirySeconds = tokenExpirySeconds;
    }

    private String resolveClientId(TokenRequest tokenRequest) {
        // Case 1: client_id sent as a form parameter (public client / unauthenticated request)
        ClientID clientId = tokenRequest.getClientID();
        if (clientId != null) {
            return clientId.getValue();
        }
        // Case 2: client_id sent via client authentication (e.g. HTTP Basic auth for
        // client-credentials grant with client_secret_basic)
        ClientAuthentication clientAuth = tokenRequest.getClientAuthentication();
        if (clientAuth != null && clientAuth.getClientID() != null) {
            return clientAuth.getClientID().getValue();
        }
        return null;
    }

    @Override
    public String issuerId() {
        return issuerId;
    }

    @Override
    public String subject(TokenRequest tokenRequest) {
        String clientId = resolveClientId(tokenRequest);
        return clientId != null ? clientId : "unknown-client";
    }

    @Override
    public String typeHeader(TokenRequest tokenRequest) {
        return "JWT";
    }

    @Override
    public List<String> audience(TokenRequest tokenRequest) {
        return List.of();
    }

    @Override
    public Map<String, Object> addClaims(TokenRequest tokenRequest) {
        String clientId = resolveClientId(tokenRequest);
        Map<String, Object> claims = new HashMap<>();
        if (clientId != null && clientIdToRoles.containsKey(clientId)) {
            claims.put("groups", clientIdToRoles.get(clientId));
        }
        return claims;
    }

    @Override
    public long tokenExpiry() {
        return tokenExpirySeconds;
    }
}
