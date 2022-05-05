package io.apicurio.registry.systemtest.client;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class ApicurioRegistryApiClient {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private String host;
    private int port;

    public ApicurioRegistryApiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean createArtifact(
            String group, String id, ArtifactType type, String content
    ) throws URISyntaxException, IOException, InterruptedException {
        // Get request URI
        URI uri = new URI(String.format("http://%s:%d/apis/registry/v2/groups/%s/artifacts", host, port, group));

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
        if (response.statusCode() != 200) {
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
        if (response.statusCode() != 200) {
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
        if (response.statusCode() != 204) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        return true;
    }

    public List<String> listArtifacts() throws URISyntaxException, IOException, InterruptedException {
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
        if (response.statusCode() != 200) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        // Get JSON response with artifact list
        JSONObject jsonArtifactList = new JSONObject(response.body());

        // Initialize artifact list to return
        List<String> artifactListToReturn = new ArrayList<>();

        // Iterate over artifact list items in response
        for (Object o : jsonArtifactList.getJSONArray("artifacts")) {
            // Get artifact
            JSONObject artifact = (JSONObject) o;

            // Add artifact group, ID and type from response to artifact list to return
            artifactListToReturn.add(String.format(
                    "%s/%s (%s)", artifact.get("groupId").toString(), artifact.get("id"), artifact.get("type")
            ));
        }

        return artifactListToReturn;
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
