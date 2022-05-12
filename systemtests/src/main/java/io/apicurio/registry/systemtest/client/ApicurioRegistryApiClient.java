package io.apicurio.registry.systemtest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.systemtest.framework.LoggerUtils;
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

    public ApicurioRegistryApiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean createArtifact(
            String groupId, String id, ArtifactType type, String content
    ) throws URISyntaxException, IOException, InterruptedException {
        // Get request URI
        URI uri = new URI(String.format("http://%s:%d/apis/registry/v2/groups/%s/artifacts", host, port, groupId));

        // Create request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .header("X-Registry-ArtifactId", id)
                .header("X-Registry-ArtifactType", type.name())
                .POST(HttpRequest.BodyPublishers.ofString(content))
                .build();

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

        // Create request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());

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

        // Create request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .DELETE()
                .build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());

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

        // Create request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());

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
}
