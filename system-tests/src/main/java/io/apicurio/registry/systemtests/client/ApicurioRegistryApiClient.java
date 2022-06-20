package io.apicurio.registry.systemtests.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ApicurioRegistryApiClient {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private String host;
    private int port;
    private String token;

    public ApicurioRegistryApiClient(String host) {
        this.host = host;
        this.port = 80;
    }

    public ApicurioRegistryApiClient(String host, String token) {
        this.host = host;
        this.port = 80;
        this.token = token;
    }

    public ApicurioRegistryApiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public ApicurioRegistryApiClient(String host, int port, String token) {
        this.host = host;
        this.port = port;
        this.token = token;
    }

    public boolean createArtifact(
            String groupId, String id, ArtifactType type, String content
    ) throws URISyntaxException, IOException, InterruptedException {
        // Get request URI
        URI uri = new URI(String.format("http://%s:%d/apis/registry/v2/groups/%s/artifacts", host, port, groupId));

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                // Set request URI
                .uri(uri)
                // Set common request headers
                .header("Content-Type", "application/json")
                .header("X-Registry-ArtifactId", id)
                .header("X-Registry-ArtifactType", type.name())
                // Set request type and content
                .POST(HttpRequest.BodyPublishers.ofString(content));

        // Set header with token when provided
        if (token != null) {
            requestBuilder.header("Authorization", String.format("Bearer %s", token));
        }

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        return true;
    }

    public String readArtifactContent(
            String group, String id
    ) throws URISyntaxException, IOException, InterruptedException {
        // Get request URI
        URI uri = new URI(
                String.format("http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s", host, port, group, id)
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .GET();

        // Set header with token when provided
        if (token != null) {
            requestBuilder.header("Authorization", String.format("Bearer %s", token));
        }

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        return response.body();
    }

    public boolean deleteArtifact(
            String group, String id
    ) throws URISyntaxException, IOException, InterruptedException {
        // Get request URL
        URI uri = new URI(
                String.format("http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s", host, port, group, id)
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .DELETE();

        // Set header with token when provided
        if (token != null) {
            requestBuilder.header("Authorization", String.format("Bearer %s", token));
        }

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_NO_CONTENT) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        return true;
    }

    public ArtifactList listArtifacts() throws URISyntaxException, IOException, InterruptedException {
        // Get request URI
        URI uri = new URI(String.format("http://%s:%d/apis/registry/v2/search/artifacts", host, port));

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .GET();

        // Set header with token when provided
        if (token != null) {
            requestBuilder.header("Authorization", String.format("Bearer %s", token));
        }

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        return MAPPER.readValue(response.body(), ArtifactList.class);
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
