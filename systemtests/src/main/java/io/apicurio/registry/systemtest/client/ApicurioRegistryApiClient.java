package io.apicurio.registry.systemtest.client;

import io.apicurio.registry.systemtest.framework.LoggerUtils;
import org.json.JSONArray;
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
    private String port;

    public ApicurioRegistryApiClient(String host, String port) {
        this.host = host;
        this.port = port;
    }

    public boolean createArtifact(String artifactGroup, String artifactId, ArtifactType artifactType, String artifactData) throws URISyntaxException, IOException, InterruptedException {
        // Create request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                // Get URL
                .uri(new URI("http://" + host + ":" + port + "/apis/registry/v2/groups/" + artifactGroup + "/artifacts"))
                // Set artifact type
                .header("Content-Type", "application/json")
                // Set request header
                .header("X-Registry-ArtifactId", artifactId)
                .header("X-Registry-ArtifactType", artifactType.name())
                // Get body
                .POST(HttpRequest.BodyPublishers.ofString(artifactData))
                .build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Check status code
        if(response.statusCode() != 200) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            // Return failure
            return false;
        }

        // Return success
        return true;
    }

    public String readArtifact(String artifactGroup, String artifactId) throws URISyntaxException, IOException, InterruptedException {
        // Create request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                // Get URL
                .uri(new URI("http://" + host + ":" + port + "/apis/registry/v2/groups/" + artifactGroup + "/artifacts/" + artifactId))
                .GET()
                .build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Check status code
        if(response.statusCode() != 200) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            // Return failure
            return null;
        }

        // Read and return artifact data
        return response.body();
    }

    public boolean deleteArtifact(String artifactGroup, String artifactId) throws URISyntaxException, IOException, InterruptedException {
        // Create request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                // Get URL
                .uri(new URI("http://" + host + ":" + port + "/apis/registry/v2/groups/" + artifactGroup + "/artifacts/" + artifactId))
                .DELETE()
                .build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Check status code
        if(response.statusCode() != 204) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            // Return failure
            return false;
        }

        // Return success
        return true;
    }

    public List<String> listArtifacts() throws URISyntaxException, IOException, InterruptedException {
        // Create request
        HttpRequest httpRequest = HttpRequest.newBuilder()
                // Get URL
                .uri(new URI("http://" + host + ":" + port + "/apis/registry/v2/search/artifacts"))
                .GET()
                .build();

        // Process request
        HttpResponse<String> response = HttpClient.newHttpClient().send(httpRequest, HttpResponse.BodyHandlers.ofString());

        // Check status code
        if(response.statusCode() != 200) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            // Return failure
            return null;
        }

        // Get response with artifact list
        JSONObject jsonObject = new JSONObject(response.body());

        // Initialize artifact list to return
        List<String> artifactList = new ArrayList<>();

        // Iterate over artifact list items in response
        for (Object o : jsonObject.getJSONArray("artifacts")) {
            // Get artifact
            JSONObject artifact = (JSONObject) o;

            // Add artifact group and ID from response to artifact list to return
            artifactList.add(artifact.get("groupId").toString() + "/" + artifact.get("id") + ", " + artifact.get("type"));
        }

        // Return artifact list
        return artifactList;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    /** Get default instances */

    public static String getDefaultAvroArtifact() {
        return new JSONObject()
                .put("type", "record")
                .put("name", "price")
                .put("namespace", "com.example")
                .put("fields", new JSONArray() {{
                    put(new JSONObject() {{
                        put("name", "symbol");
                        put("type", "string");
                    }});
                    put(new JSONObject() {{
                        put("name", "price");
                        put("type", "string");
                    }});
                }})
                .toString();
    }
}
