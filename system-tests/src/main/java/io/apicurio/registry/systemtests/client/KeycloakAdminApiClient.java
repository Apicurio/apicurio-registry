package io.apicurio.registry.systemtests.client;

import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.HttpClientUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONArray;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class KeycloakAdminApiClient {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private String host;
    private int port;
    private String token;
    private String username;
    private String password;

    public KeycloakAdminApiClient(String host) {
        this.host = host;
        this.port = 80;
    }

    public KeycloakAdminApiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getApiClientSecret() throws IOException, InterruptedException {
        return getApiClientSecret(Constants.SSO_TEST_CLIENT_API);
    }

    public String getApiClientSecret(String clientId) throws IOException, InterruptedException {
        URI clientsRequestUrl = HttpClientUtils.buildURI(
                "%s/admin/realms/%s/clients", host, Constants.SSO_REALM
        );

        HttpRequest clientsRequest = HttpClientUtils.newBuilder()
                .uri(clientsRequestUrl)
                .header("Authorization", String.format("Bearer %s", token))
                .GET()
                .build();

        // Process request
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(HttpClientUtils.getInsecureContext())
                .build();

        HttpResponse<String> clientsResponse = httpClient.send(clientsRequest, HttpResponse.BodyHandlers.ofString());

        // Check response status code
        if (clientsResponse.statusCode() != HttpStatus.SC_OK) {
            LOGGER.error("Response: code={}, body={}", clientsResponse.statusCode(), clientsResponse.body());

            return null;
        } else {
            JSONArray obj = new JSONArray(clientsResponse.body());

            for (int i = 0; i < obj.length(); i++) {
                if (obj.getJSONObject(i).getString("clientId").equals(clientId)) {
                    return obj.getJSONObject(i).getString("secret");
                }
            }
        }

        LOGGER.error("API client secret was not found.");
        return null;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
