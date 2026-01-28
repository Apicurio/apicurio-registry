/*
 * Copyright 2025 Red Hat Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.examples.a2a.realworld.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * HTTP client for Ollama API with JSON mode support.
 *
 * Features:
 * - Synchronous generate endpoint
 * - Structured JSON output mode
 * - Connection health checks
 * - Configurable timeouts
 */
public class OllamaClient {

    private static final Logger LOGGER = Logger.getLogger(OllamaClient.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    public OllamaClient(String baseUrl, String model) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Generate a response using the Ollama API with JSON mode.
     *
     * @param prompt The user prompt to send
     * @param systemPrompt The system prompt that defines agent behavior
     * @return The generated response text (JSON format)
     */
    public String generate(String prompt, String systemPrompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("prompt", prompt);
        requestBody.put("system", systemPrompt);
        requestBody.put("stream", false);
        requestBody.put("format", "json");

        // Set reasonable generation parameters
        ObjectNode options = requestBody.putObject("options");
        options.put("temperature", 0.7);
        options.put("num_predict", 2048);

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama error: HTTP " + response.statusCode() +
                    " - " + response.body());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        return responseJson.get("response").asText();
    }

    /**
     * Check if Ollama is available.
     *
     * @return true if Ollama server is responding
     */
    public boolean isServerAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the configured model is available.
     *
     * @return true if the model is loaded and ready
     */
    public boolean isModelAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode tags = objectMapper.readTree(response.body());
                JsonNode models = tags.get("models");
                if (models != null && models.isArray()) {
                    for (JsonNode m : models) {
                        String modelName = m.get("name").asText();
                        if (modelName.startsWith(model) || modelName.equals(model + ":latest")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Wait for the Ollama server and model to be available.
     *
     * @param timeout Maximum time to wait
     * @throws RuntimeException if timeout is exceeded
     */
    public void waitForReady(Duration timeout) throws Exception {
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout.toMillis();

        LOGGER.info("Waiting for Ollama server at " + baseUrl + "...");

        // First wait for server
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isServerAvailable()) {
                LOGGER.info("Ollama server is available");
                break;
            }
            Thread.sleep(2000);
        }

        if (!isServerAvailable()) {
            throw new RuntimeException("Timeout waiting for Ollama server at " + baseUrl);
        }

        // Then wait for model
        LOGGER.info("Waiting for model '" + model + "' to be ready...");

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isModelAvailable()) {
                LOGGER.info("Model '" + model + "' is ready!");
                return;
            }
            LOGGER.info("Model not ready yet, waiting... (this may take a few minutes on first run)");
            Thread.sleep(5000);
        }

        throw new RuntimeException("Timeout waiting for model: " + model +
                ". Make sure to run 'docker-compose up -d' and wait for the model to download.");
    }

    /**
     * Get the configured model name.
     */
    public String getModel() {
        return model;
    }

    /**
     * Get the Ollama base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }
}
