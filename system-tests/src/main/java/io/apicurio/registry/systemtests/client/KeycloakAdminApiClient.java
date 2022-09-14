package io.apicurio.registry.systemtests.client;

import io.apicurio.registry.systemtests.framework.Constants;
import io.apicurio.registry.systemtests.framework.Environment;
import io.apicurio.registry.systemtests.framework.HttpClientUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONArray;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    public String getClientSecret(String clientId) {
        return getApiObjectAttribute("clients", "clientId", clientId, "secret");
    }

    public String getClientId(String clientId) {
        return getApiObjectAttribute("clients", "clientId", clientId, "id");
    }

    public String getClientScopeId(String scopeName) {
        return getApiObjectAttribute("client-scopes", "name", scopeName, "id");
    }

    public String getApiObjectAttribute(String objects, String objectId, String objectValue, String attributeKey) {
        URI clientsRequestUrl = HttpClientUtils.buildURI(
                "%s/admin/realms/%s/%s", host, Constants.SSO_REALM, objects
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
                if (obj.getJSONObject(i).getString(objectId).equals(objectValue)) {
                    return obj.getJSONObject(i).getString(attributeKey);
                }
            }
        }

        LOGGER.error("API client secret was not found.");

        return null;
    }

    public boolean createUserAttributesClientScope() throws IOException {
        // Log information about current action
        LOGGER.info("Creating org-admin client scope...");

        // Get request URI
        URI uri = HttpClientUtils.buildURI("%s/admin/realms/%s/client-scopes", host, Constants.SSO_REALM);

        // Prepare request content
        String content = Files.readString(
                Paths.get(Environment.TESTSUITE_PATH, "configs", "keycloak", "user-attributes-client-scope.json")
        );

        // Build request
        HttpRequest request = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                .header("Authorization", String.format("Bearer %s", token))
                // Set content type header
                .header("Content-Type", "application/json")
                // Set request type and content
                .POST(HttpRequest.BodyPublishers.ofString(content))
                .build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_CREATED) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        return true;
    }

    public boolean addDefaultClientScopeToClient(String clientId, String clientScopeId) {
        // Log information about current action
        LOGGER.info("Adding default client scope {} to client {}...", clientScopeId, clientId);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "%s/admin/realms/%s/clients/%s/default-client-scopes/%s",
                host, Constants.SSO_REALM, clientId, clientScopeId
        );

        // Build request
        HttpRequest request = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                .header("Authorization", String.format("Bearer %s", token))
                // Set content type header
                .header("Content-Type", "application/json")
                // Set request type and content
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_NO_CONTENT) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        return true;
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
