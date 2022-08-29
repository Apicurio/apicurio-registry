package io.apicurio.registry.systemtests.client;

import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.HttpClientUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONArray;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class KeycloakAdminApiClient {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private String host;
    private int port;
    private String token;

    public KeycloakAdminApiClient(String host) {
        this.host = host;
        this.port = 80;
    }

    public KeycloakAdminApiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public KeycloakAdminApiClient(String host, String token) {
        this.host = host;
        this.token = token;
    }

    public KeycloakAdminApiClient(String host, int port, String token) {
        this.host = host;
        this.port = port;
        this.token = token;
    }

    public String getApiClientSecret() {
        return getApiClientSecret(Constants.SSO_TEST_CLIENT_API);
    }

    public String getApiClientSecret(String clientId) {
        URI clientsRequestUrl = HttpClientUtils.buildURI(
                "%s/admin/realms/%s/clients", host, Constants.SSO_REALM
        );

        HttpRequest clientsRequest = HttpClientUtils.newBuilder()
                .uri(clientsRequestUrl)
                .header("Authorization", String.format("Bearer %s", token))
                .GET()
                .build();

        HttpResponse<String> clientsResponse = HttpClientUtils.processRequest(clientsRequest);

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
}
