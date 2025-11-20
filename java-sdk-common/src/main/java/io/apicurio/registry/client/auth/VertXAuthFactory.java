package io.apicurio.registry.client.auth;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.web.client.OAuth2WebClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * A factory class to make it easier to create Vertx web clients with authentication enabled. Supports OIDC
 * and BASIC auth.
 */
public class VertXAuthFactory {

    public static WebClient buildOIDCWebClient(Vertx vertx, String tokenUrl, String clientId,
            String clientSecret) {
        return buildOIDCWebClient(vertx, tokenUrl, clientId, clientSecret, null);
    }

    public static WebClient buildOIDCWebClient(Vertx vertx, String tokenUrl, String clientId,
            String clientSecret, String scope) {
        return buildOIDCWebClient(vertx, null, tokenUrl, clientId, clientSecret, scope);
    }

    public static WebClient buildOIDCWebClient(Vertx vertx, WebClientOptions options, String tokenUrl,
            String clientId, String clientSecret, String scope) {
        if (options == null) {
            options = new WebClientOptions();
        }
        WebClient webClient = WebClient.create(vertx, options);

        OAuth2Auth oAuth2Options = OAuth2Auth.create(vertx, new OAuth2Options()
                .setFlow(OAuth2FlowType.CLIENT)
                .setHttpClientOptions(options)
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setTokenPath(tokenUrl));

        Oauth2Credentials oauth2Credentials = new Oauth2Credentials();
        if (scope != null) {
            oauth2Credentials.addScope(scope);
        }
        OAuth2WebClient oauth2WebClient = OAuth2WebClient.create(webClient, oAuth2Options);
        oauth2WebClient.withCredentials(oauth2Credentials);

        return oauth2WebClient;
    }

    public static WebClient buildSimpleAuthWebClient(Vertx vertx, String username, String password) {
        return buildSimpleAuthWebClient(vertx, null, username, password);
    }

    public static WebClient buildSimpleAuthWebClient(Vertx vertx, WebClientOptions options, String username,
            String password) {
        WebClient webClient = options != null ? WebClient.create(vertx, options) : WebClient.create(vertx);
        String usernameAndPassword = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return WebClientSession.create(webClient).addHeader("Authorization",
                "Basic " + usernameAndPassword);
    }

}
