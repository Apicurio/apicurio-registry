package io.apicurio.registry.client.auth;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.web.client.OAuth2WebClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientSession;

import java.util.Base64;

public class VertXAuthFactory {

    public final static Vertx defaultVertx = Vertx.vertx();

    public static WebClient buildOIDCWebClient(String tokenUrl, String clientId, String clientSecret) {
        return buildOIDCWebClient(defaultVertx, tokenUrl, clientId, clientSecret, null);
    }

    public static WebClient buildOIDCWebClient(Vertx vertx, String tokenUrl, String clientId, String clientSecret) {
        return buildOIDCWebClient(tokenUrl, clientId, clientSecret, null);
    }

    public static WebClient buildOIDCWebClient(String tokenUrl, String clientId, String clientSecret, String scope) {
        return buildOIDCWebClient(defaultVertx, tokenUrl, clientId, clientSecret, scope);
    }

    public static WebClient buildOIDCWebClient(Vertx vertx, String tokenUrl, String clientId, String clientSecret, String scope) {
        OAuth2Options options =
                new OAuth2Options()
                        .setFlow(OAuth2FlowType.CLIENT)
                        .setClientId(clientId)
                        .setTokenPath(tokenUrl)
                        .setClientSecret(clientSecret);
        OAuth2Auth oAuth2Auth = OAuth2Auth.create(VertXAuthFactory.defaultVertx, options);
        Oauth2Credentials oauth2Credentials = new Oauth2Credentials();
        if (scope != null) {
            oauth2Credentials.addScope(scope);
        }

        OAuth2WebClient oAuth2WebClient =
                OAuth2WebClient.create(WebClient.create(vertx), oAuth2Auth)
                        .withCredentials(oauth2Credentials);

        return oAuth2WebClient;
    }

    public static WebClient buildSimpleAuthWebClient(String username, String password) {
        return buildSimpleAuthWebClient(defaultVertx, username, password);
    }

    public static WebClient buildSimpleAuthWebClient(Vertx vertx, String username, String password) {
        String usernameAndPassword = Base64.getEncoder().encodeToString("user:pw".getBytes());

        // TODO: ask Carles if there is a more "idiomatic way" to do this
        return WebClientSession
                .create(WebClient.create(vertx))
                .addHeader("Authorization", "Basic " + usernameAndPassword);
    }

}
