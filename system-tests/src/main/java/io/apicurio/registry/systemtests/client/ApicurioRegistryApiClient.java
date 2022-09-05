package io.apicurio.registry.systemtests.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.systemtests.framework.Base64Utils;
import io.apicurio.registry.systemtests.framework.CompatibilityLevel;
import io.apicurio.registry.systemtests.framework.HttpClientUtils;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import io.apicurio.registry.systemtests.framework.RuleType;
import io.apicurio.registry.systemtests.framework.ValidityLevel;
import io.apicurio.registry.systemtests.time.TimeoutBudget;
import org.apache.hc.core5.http.HttpStatus;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    public boolean enableGlobalValidityRule() {
        return enableGlobalValidityRule(HttpStatus.SC_NO_CONTENT);
    }

    public boolean enableGlobalValidityRule(int httpStatus) {
        return enableGlobalRule(RuleType.VALIDITY, httpStatus);
    }

    public boolean enableGlobalCompatibilityRule() {
        return enableGlobalCompatibilityRule(HttpStatus.SC_NO_CONTENT);
    }

    public boolean enableGlobalCompatibilityRule(int httpStatus) {
        return enableGlobalRule(RuleType.COMPATIBILITY, httpStatus);
    }

    public boolean enableGlobalRule(RuleType ruleType) {
        return enableGlobalRule(ruleType, HttpStatus.SC_NO_CONTENT);
    }

    public boolean enableGlobalRule(RuleType ruleType, int httpStatus) {
        // Log information about current action
        LOGGER.info("Enabling global {} rule...", ruleType);

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/admin/rules", host, port);

        // Prepare request content
        String content = String.format(
                "{\"type\":\"%s\", \"config\":\"%s\"}",
                ruleType,
                ruleType == RuleType.VALIDITY ? ValidityLevel.FULL : CompatibilityLevel.BACKWARD
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set content type header
                .header("Content-Type", "application/json")
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

    public boolean updateGlobalValidityRule(ValidityLevel validityLevel) {
        return updateGlobalValidityRule(validityLevel, HttpStatus.SC_OK);
    }

    public boolean updateGlobalValidityRule(ValidityLevel validityLevel, int httpStatus) {
        return updateGlobalRule(RuleType.VALIDITY, validityLevel.name(), httpStatus);
    }

    public boolean updateGlobalCompatibilityRule(CompatibilityLevel compatibilityLevel) {
        return updateGlobalCompatibilityRule(compatibilityLevel, HttpStatus.SC_OK);
    }

    public boolean updateGlobalCompatibilityRule(CompatibilityLevel compatibilityLevel, int httpStatus) {
        return updateGlobalRule(RuleType.COMPATIBILITY, compatibilityLevel.name(), httpStatus);
    }

    public boolean updateGlobalRule(RuleType ruleType, String ruleLevel, int httpStatus) {
        // Log information about current action
        LOGGER.info("Updating global {} rule to {}...", ruleType, ruleLevel);

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/admin/rules/%s", host, port, ruleType);

        // Prepare request content
        String content = String.format("{\"type\":\"%s\", \"config\":\"%s\"}", ruleType, ruleLevel);

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set content type header
                .header("Content-Type", "application/json")
                // Set request type and content
                .PUT(HttpRequest.BodyPublishers.ofString(content));

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

    public ValidityLevel getGlobalValidityRule() {
        return getGlobalValidityRule(HttpStatus.SC_OK);
    }

    public ValidityLevel getGlobalValidityRule(int httpStatus) {
        String level = getGlobalRule(RuleType.VALIDITY, httpStatus);

        return level == null ? null : ValidityLevel.valueOf(level);
    }

    public CompatibilityLevel getGlobalCompatibilityRule() {
        return getGlobalCompatibilityRule(HttpStatus.SC_OK);
    }

    public CompatibilityLevel getGlobalCompatibilityRule(int httpStatus) {
        String level = getGlobalRule(RuleType.COMPATIBILITY, httpStatus);

        return level == null ? null : CompatibilityLevel.valueOf(level);
    }

    public String getGlobalRule(RuleType ruleType, int httpStatus) {
        // Log information about current action
        LOGGER.info("Getting global {} rule...", ruleType);

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/admin/rules/%s", host, port, ruleType);

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

        if (httpStatus == HttpStatus.SC_OK) {
            return (new JSONObject(response.body())).getString("config");
        } else {
            return null;
        }
    }

    public List<String> listGlobalRules() {
        return listGlobalRules(HttpStatus.SC_OK);
    }

    public List<String> listGlobalRules(int httpStatus) {
        // Log information about current action
        LOGGER.info("Listing global rules...");

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/admin/rules", host, port);

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

        if (httpStatus == HttpStatus.SC_OK) {
            return (new JSONArray(response.body()))
                    .toList()
                    .stream()
                    .map(object -> Objects.toString(object, null))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    public boolean isGlobalValidityRuleEnabled() {
        return isGlobalRuleEnabled(RuleType.VALIDITY);
    }

    public boolean isGlobalCompatibilityRuleEnabled() {
        return isGlobalRuleEnabled(RuleType.COMPATIBILITY);
    }

    public boolean isGlobalRuleEnabled(RuleType ruleType) {
        return listGlobalRules().contains(ruleType.name());
    }

    public boolean toggleGlobalValidityRule() {
        return toggleGlobalRule(RuleType.VALIDITY);
    }

    public boolean toggleGlobalCompatibilityRule() {
        return toggleGlobalRule(RuleType.COMPATIBILITY);
    }

    public boolean toggleGlobalRule(RuleType ruleType) {
        if (isGlobalRuleEnabled(ruleType)) {
            return disableGlobalRule(ruleType);
        } else {
            return enableGlobalRule(ruleType);
        }
    }

    public boolean disableGlobalValidityRule() {
        return disableGlobalValidityRule(HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableGlobalValidityRule(int httpStatus) {
        return disableGlobalRule(RuleType.VALIDITY, httpStatus);
    }

    public boolean disableGlobalCompatibilityRule() {
        return  disableGlobalCompatibilityRule(HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableGlobalCompatibilityRule(int httpStatus) {
        return  disableGlobalRule(RuleType.COMPATIBILITY, httpStatus);
    }

    public boolean disableGlobalRule(RuleType ruleType) {
        return disableGlobalRule(ruleType, HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableGlobalRule(RuleType ruleType, int httpStatus) {
        // Log information about current action
        LOGGER.info("Disabling global {} rule...", ruleType);

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/admin/rules/%s", host, port, ruleType);

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

    public boolean disableGlobalRules() {
        return disableGlobalRules(HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableGlobalRules(int httpStatus) {
        // Log information about current action
        LOGGER.info("Disabling all global rules...");

        // Get request URI
        URI uri = HttpClientUtils.buildURI("http://%s:%d/apis/registry/v2/admin/rules", host, port);

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

    public boolean enableArtifactValidityRule(String groupId, String id) {
        return enableArtifactValidityRule(groupId, id, HttpStatus.SC_NO_CONTENT);
    }

    public boolean enableArtifactValidityRule(String groupId, String id, int httpStatus) {
        return enableArtifactRule(groupId, id, RuleType.VALIDITY, httpStatus);
    }

    public boolean enableArtifactCompatibilityRule(String groupId, String id) {
        return enableArtifactCompatibilityRule(groupId, id, HttpStatus.SC_NO_CONTENT);
    }

    public boolean enableArtifactCompatibilityRule(String groupId, String id, int httpStatus) {
        return enableArtifactRule(groupId, id, RuleType.COMPATIBILITY, httpStatus);
    }

    public boolean enableArtifactRule(String groupId, String id, RuleType ruleType) {
        return enableArtifactRule(groupId, id, ruleType, HttpStatus.SC_NO_CONTENT);
    }

    public boolean enableArtifactRule(String groupId, String id, RuleType ruleType, int httpStatus) {
        // Log information about current action
        LOGGER.info("Enabling artifact {} rule of {}/{}...", ruleType, groupId, id);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s/rules", host, port, groupId, id
        );

        // Prepare request content
        String content = String.format(
                "{\"type\":\"%s\", \"config\":\"%s\"}",
                ruleType,
                ruleType == RuleType.VALIDITY ? ValidityLevel.FULL : CompatibilityLevel.BACKWARD
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set content type header
                .header("Content-Type", "application/json")
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

    public boolean updateArtifactValidityRule(String groupId, String id, ValidityLevel validityLevel) {
        return updateArtifactValidityRule(groupId, id, validityLevel, HttpStatus.SC_OK);
    }

    public boolean updateArtifactValidityRule(String groupId, String id, ValidityLevel validityLevel, int httpStatus) {
        return updateArtifactRule(groupId, id, RuleType.VALIDITY, validityLevel.name(), httpStatus);
    }

    public boolean updateArtifactCompatibilityRule(String groupId, String id, CompatibilityLevel compatibilityLevel) {
        return updateArtifactCompatibilityRule(groupId, id, compatibilityLevel, HttpStatus.SC_OK);
    }

    public boolean updateArtifactCompatibilityRule(
            String groupId,
            String id,
            CompatibilityLevel compatibilityLevel,
            int httpStatus
    ) {
        return updateArtifactRule(groupId, id, RuleType.COMPATIBILITY, compatibilityLevel.name(), httpStatus);
    }

    public boolean updateArtifactRule(String groupId, String id, RuleType ruleType, String ruleLevel, int httpStatus) {
        // Log information about current action
        LOGGER.info("Updating artifact {} rule of {}/{} to {}...", ruleType, groupId, id, ruleLevel);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s/rules/%s", host, port, groupId, id, ruleType
        );

        // Prepare request content
        String content = String.format("{\"type\":\"%s\", \"config\":\"%s\"}", ruleType, ruleLevel);

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set content type header
                .header("Content-Type", "application/json")
                // Set request type and content
                .PUT(HttpRequest.BodyPublishers.ofString(content));

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

    public ValidityLevel getArtifactValidityRule(String groupId, String id) {
        return getArtifactValidityRule(groupId, id, HttpStatus.SC_OK);
    }

    public ValidityLevel getArtifactValidityRule(String groupId, String id, int httpStatus) {
        String level = getArtifactRule(groupId, id, RuleType.VALIDITY, httpStatus);

        return level == null ? null : ValidityLevel.valueOf(level);
    }

    public CompatibilityLevel getArtifactCompatibilityRule(String groupId, String id) {
        return getArtifactCompatibilityRule(groupId, id, HttpStatus.SC_OK);
    }

    public CompatibilityLevel getArtifactCompatibilityRule(String groupId, String id, int httpStatus) {
        String level = getArtifactRule(groupId, id, RuleType.COMPATIBILITY, httpStatus);

        return level == null ? null : CompatibilityLevel.valueOf(level);
    }

    public String getArtifactRule(String groupId, String id, RuleType ruleType, int httpStatus) {
        // Log information about current action
        LOGGER.info("Getting artifact {} rule of {}/{}...", ruleType, groupId, id);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s/rules/%s", host, port, groupId, id, ruleType
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

        if (httpStatus == HttpStatus.SC_OK) {
            return (new JSONObject(response.body())).getString("config");
        } else {
            return null;
        }
    }

    public List<String> listArtifactRules(String groupId, String id) {
        return listArtifactRules(groupId, id, HttpStatus.SC_OK);
    }

    public List<String> listArtifactRules(String groupId, String id, int httpStatus) {
        // Log information about current action
        LOGGER.info("Listing artifact rules of {}/{}...", groupId, id);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s/rules", host, port, groupId, id
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

        if (httpStatus == HttpStatus.SC_OK) {
            return (new JSONArray(response.body()))
                    .toList()
                    .stream()
                    .map(object -> Objects.toString(object, null))
                    .collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    public boolean isArtifactValidityRuleEnabled(String groupId, String id) {
        return isArtifactRuleEnabled(groupId, id, RuleType.VALIDITY);
    }

    public boolean isArtifactCompatibilityRuleEnabled(String groupId, String id) {
        return isArtifactRuleEnabled(groupId, id, RuleType.COMPATIBILITY);
    }

    public boolean isArtifactRuleEnabled(String groupId, String id, RuleType ruleType) {
        return listArtifactRules(groupId, id).contains(ruleType.name());
    }

    public boolean toggleArtifactValidityRule(String groupId, String id) {
        return toggleArtifactRule(groupId, id, RuleType.VALIDITY);
    }

    public boolean toggleArtifactCompatibilityRule(String groupId, String id) {
        return toggleArtifactRule(groupId, id, RuleType.COMPATIBILITY);
    }

    public boolean toggleArtifactRule(String groupId, String id, RuleType ruleType) {
        if (isArtifactRuleEnabled(groupId, id, ruleType)) {
            return disableArtifactRule(groupId, id, ruleType);
        } else {
            return enableArtifactRule(groupId, id, ruleType);
        }
    }

    public boolean disableArtifactValidityRule(String groupId, String id) {
        return disableArtifactValidityRule(groupId, id, HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableArtifactValidityRule(String groupId, String id, int httpStatus) {
        return disableArtifactRule(groupId, id, RuleType.VALIDITY, httpStatus);
    }

    public boolean disableArtifactCompatibilityRule(String groupId, String id) {
        return  disableArtifactCompatibilityRule(groupId, id, HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableArtifactCompatibilityRule(String groupId, String id, int httpStatus) {
        return  disableArtifactRule(groupId, id, RuleType.COMPATIBILITY, httpStatus);
    }

    public boolean disableArtifactRule(String groupId, String id, RuleType ruleType) {
        return disableArtifactRule(groupId, id, ruleType, HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableArtifactRule(String groupId, String id, RuleType ruleType, int httpStatus) {
        // Log information about current action
        LOGGER.info("Disabling artifact {} rule of {}/{}...", ruleType, groupId, id);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s/rules/%s", host, port, groupId, id, ruleType
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

    public boolean disableArtifactRules(String groupId, String id) {
        return disableArtifactRules(groupId, id, HttpStatus.SC_NO_CONTENT);
    }

    public boolean disableArtifactRules(String groupId, String id, int httpStatus) {
        // Log information about current action
        LOGGER.info("Disabling all artifact rules of {}/{}...", groupId, id);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s/rules", host, port, groupId, id
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
        return readArtifactContent(groupId, id, HttpStatus.SC_OK);
    }

    public String readArtifactContent(String groupId, String id, int httpStatus) {
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

        LOGGER.info("Expected status code: {}.", httpStatus);

        // Check response status code
        if (response.statusCode() != httpStatus) {
            LOGGER.error("Response: code={}, body={}", response.statusCode(), response.body());

            return null;
        }

        LOGGER.info("Response: code={}, body={}", response.statusCode(), response.body());

        if (httpStatus == HttpStatus.SC_OK) {
            return response.body();
        } else {
            return null;
        }
    }

    public boolean updateArtifact(String groupId, String id, String newContent) {
        return updateArtifact(groupId, id, newContent, HttpStatus.SC_OK);
    }

    public boolean updateArtifact(String groupId, String id, String newContent, int httpStatus) {
        // Log information about current action
        LOGGER.info("Updating artifact: groupId={}, id={}.", groupId, id);

        // Get request URL
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts/%s", host, port, groupId, id
        );

        // Get request builder
        HttpRequest.Builder requestBuilder = HttpClientUtils.newBuilder()
                // Set request URI
                .uri(uri)
                // Set request type and data
                .PUT(HttpRequest.BodyPublishers.ofString(newContent));

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

    public ArtifactList listGroupArtifacts(String groupId) {
        return listGroupArtifacts(groupId, HttpStatus.SC_OK);
    }

    public ArtifactList listGroupArtifacts(String groupId, int httpStatus) {
        // Log information about current action
        LOGGER.info("Listing artifacts in group {}.", groupId);

        // Get request URI
        URI uri = HttpClientUtils.buildURI(
                "http://%s:%d/apis/registry/v2/groups/%s/artifacts",
                host,
                port,
                groupId
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
        return checkUnauthorized(HttpStatus.SC_UNAUTHORIZED);
    }

    public boolean checkUnauthorized(int httpStatus) {
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

        LOGGER.info("Expected status code: {}.", httpStatus);

        // Check response status code
        if (response.statusCode() != httpStatus) {
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
