package io.apicurio.registry.systemtests.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.systemtests.framework.Base64Utils;
import io.apicurio.registry.systemtests.framework.HttpClientUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import org.apache.hc.core5.http.HttpStatus;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApicurioRegistryApiClient {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private String host;
    private int port;
    private String token;
    private AuthMethod authMethod;
    private String username;
    private String password;

    public ApicurioRegistryApiClient(String host) {
        this.host = host;
        this.port = 80;
    }

    public ApicurioRegistryApiClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    private void setAuthenticationHeader(HttpRequest.Builder requestBuilder) {
        // Set header with authentication when provided
        if (authMethod == AuthMethod.TOKEN) {
            requestBuilder.header("Authorization", String.format("Bearer %s", token));
        } else if (authMethod == AuthMethod.BASIC) {
            requestBuilder.header(
                    "Authorization",
                    String.format("Basic %s", Base64Utils.encode(username + ":" + password))
            );
        } else {
            LOGGER.info("No authentication header needed.");
        }
    }

    public boolean isServiceAvailable() {
        // Log information about current action
        LOGGER.info("Checking if API is available...");

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis", host, port);

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .GET();

        // Set header with authentication when provided
        setAuthenticationHeader(requestBuilder);

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        // Check response status code
        if (response.statusCode() == HttpStatus.SC_SERVICE_UNAVAILABLE) {
            LOGGER.warn("Response: code={}", response.statusCode());

            return false;
        }

        LOGGER.info("Response: code={}", response.statusCode());

        return true;
    }

    public boolean waitServiceAvailable() {
        TimeoutBudget timeout = TimeoutBudget.ofDuration(Duration.ofMinutes(3));

        LOGGER.info("Waiting for API to be ready...");

        while (!timeout.timeoutExpired()) {
            if (isServiceAvailable()) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        if (!isServiceAvailable()) {
            LOGGER.error("API failed readiness check.");

            return false;
        }

        LOGGER.info("API is ready.");

        return true;
    }

    public boolean createArtifact(String groupId, String id, ArtifactType type, String content) {
        return createArtifact(groupId, id, type, content, HttpStatus.SC_OK);
    }

    public boolean createArtifact(String groupId, String id, ArtifactType type, String content, int httpStatus) {
        // Log information about current action
        LOGGER.info("Creating artifact: groupId={}, id={}, type={}.", groupId, id, type);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts", host, port, groupId
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set common request headers
                .header("Content-Type", "application/json")
                .header("X-Registry-ArtifactId", id)
                .header("X-Registry-ArtifactType", type.name())
                // Set request type and content
                .POST(HttpRequest.BodyPublishers.ofString(content));

        // Set header with authentication when provided
        setAuthenticationHeader(requestBuilder);

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        LOGGER.info("Expected status code: {}.", httpStatus);

        // Check response status code
        if (response.statusCode() != httpStatus) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        return true;
    }

    public String readArtifactContent(String groupId, String id) {
        // Log information about current action
        LOGGER.info("Reading artifact content: groupId={}, id={}.", groupId, id);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s", host, port, groupId, id
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .GET();

        // Set header with authentication when provided
        setAuthenticationHeader(requestBuilder);

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        LOGGER.info("Expected status code: {}.", HttpStatus.SC_OK);

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_OK) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        return response.body();
    }

    public boolean deleteArtifact(String group, String id) {
        return deleteArtifact(group, id, HttpStatus.SC_NO_CONTENT);
    }

    public boolean deleteArtifact(String groupId, String id, int httpStatus) {
        // Log information about current action
        LOGGER.info("Deleting artifact: groupId={}, id={}.", groupId, id);

        // Get request URL
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s", host, port, groupId, id
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .DELETE();

        // Set header with authentication when provided
        setAuthenticationHeader(requestBuilder);

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        LOGGER.info("Expected status code: {}.", httpStatus);

        // Check response status code
        if (response.statusCode() != httpStatus) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        return true;
    }

    public ArtifactList listArtifacts() {
        return listArtifacts(1000);
    }

    public ArtifactList listArtifacts(int limit) {
        return listArtifacts(limit, HttpStatus.SC_OK);
    }

    public ArtifactList listArtifacts(int limit, int httpStatus) {
        // Log information about current action
        LOGGER.info("Listing all artifacts.");

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/search/artifacts?limit=%d",
                host,
                port,
                limit
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .GET();

        // Set header with authentication when provided
        setAuthenticationHeader(requestBuilder);

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        LOGGER.info("Expected status code: {}.", httpStatus);

        // Check response status code
        if (response.statusCode() != httpStatus) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        try {
            if (httpStatus == HttpStatus.SC_OK) {
                return MAPPER.readValue(response.body(), ArtifactList.class);
            } else {
                return new ArtifactList();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean checkUnauthorized() {
        // Log information about current action
        LOGGER.info("Trying unauthorized access...");

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/search/artifacts", host, port);

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .GET();

        // Set header with authentication when provided
        setAuthenticationHeader(requestBuilder);

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        LOGGER.info("Expected status code: {}.", HttpStatus.SC_UNAUTHORIZED);

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_UNAUTHORIZED) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return false;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        return true;
    }

    public boolean checkUnauthorizedFake() {
        // Log information about current action
        LOGGER.info("Trying fake unauthorized access...");

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/search/artifacts", host, port);

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type
                .GET();

        // Set header with fake token
        requestBuilder.header("Authorization", "Bearer thisShouldNotWork");

        // Build request
        HttpRequest request = requestBuilder.build();

        // Process request
        HttpResponse<String> response = HttpClientUtils.processRequest(request);

        LOGGER.info("Expected status code: {}.", HttpStatus.SC_UNAUTHORIZED);

        // Check response status code
        if (response.statusCode() != HttpStatus.SC_UNAUTHORIZED) {
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

    public AuthMethod getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(AuthMethod authMethod) {
        this.authMethod = authMethod;
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
