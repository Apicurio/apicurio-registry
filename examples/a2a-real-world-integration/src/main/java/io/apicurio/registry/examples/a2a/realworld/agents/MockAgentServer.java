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
package io.apicurio.registry.examples.a2a.realworld.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * A simple HTTP server that implements the A2A (Agent-to-Agent) protocol.
 * This demonstrates a real working agent that can:
 * - Expose its agent card via /.well-known/agent.json
 * - Accept A2A tasks via JSON-RPC at /a2a endpoint
 * - Process tasks and return results
 */
public class MockAgentServer {

    private static final Logger LOGGER = Logger.getLogger(MockAgentServer.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    protected final int port;
    protected final String agentName;
    protected final String agentDescription;
    protected final String[] skills;
    protected Function<String, String> taskHandler;
    private HttpServer server;

    public MockAgentServer(int port, String agentName, String agentDescription,
            String[] skills, Function<String, String> taskHandler) {
        this.port = port;
        this.agentName = agentName;
        this.agentDescription = agentDescription;
        this.skills = skills;
        this.taskHandler = taskHandler;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // A2A Well-Known endpoint for agent discovery
        server.createContext("/.well-known/agent.json", new AgentCardHandler());

        // A2A Task endpoint (JSON-RPC)
        server.createContext("/a2a", new A2ATaskHandler());

        // Health check
        server.createContext("/health", exchange -> {
            String response = "{\"status\":\"healthy\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });

        server.start();
        LOGGER.info("Agent '" + agentName + "' started on port " + port);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("Agent '" + agentName + "' stopped");
        }
    }

    public String getUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Handler for /.well-known/agent.json - Returns the agent's A2A card
     */
    private class AgentCardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            ObjectNode agentCard = objectMapper.createObjectNode();
            agentCard.put("name", agentName);
            agentCard.put("description", agentDescription);
            agentCard.put("version", "1.0.0");
            agentCard.put("url", getUrl());

            ObjectNode provider = agentCard.putObject("provider");
            provider.put("organization", "A2A Demo");
            provider.put("url", getUrl());

            ObjectNode capabilities = agentCard.putObject("capabilities");
            capabilities.put("streaming", false);
            capabilities.put("pushNotifications", false);

            ArrayNode skillsArray = agentCard.putArray("skills");
            for (String skill : skills) {
                ObjectNode skillNode = skillsArray.addObject();
                skillNode.put("id", skill.toLowerCase().replace(" ", "-"));
                skillNode.put("name", skill);
                skillNode.put("description", "Skill: " + skill);
                skillNode.putArray("tags").add(skill.toLowerCase());
            }

            ArrayNode inputModes = agentCard.putArray("defaultInputModes");
            inputModes.add("text");
            ArrayNode outputModes = agentCard.putArray("defaultOutputModes");
            outputModes.add("text");

            ObjectNode auth = agentCard.putObject("authentication");
            auth.putArray("schemes").add("none");

            String response = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(agentCard);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /**
     * Handler for /a2a endpoint - Processes A2A JSON-RPC requests
     * Implements the A2A protocol task submission and retrieval
     */
    private class A2ATaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle CORS preflight
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            // Read request body
            String requestBody;
            try (InputStream is = exchange.getRequestBody()) {
                requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            LOGGER.info("[" + agentName + "] Received A2A request: " +
                    requestBody.substring(0, Math.min(200, requestBody.length())));

            // Parse JSON-RPC request
            JsonNode request = objectMapper.readTree(requestBody);
            String method = request.get("method").asText();
            String id = request.has("id") ? request.get("id").asText() : UUID.randomUUID().toString();

            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", id);

            if ("tasks/send".equals(method)) {
                // Extract the task message
                JsonNode params = request.get("params");
                String taskId = UUID.randomUUID().toString();

                // Extract user message from the A2A task
                String userMessage = "";
                if (params.has("message") && params.get("message").has("parts")) {
                    JsonNode parts = params.get("message").get("parts");
                    if (parts.isArray() && parts.size() > 0) {
                        JsonNode firstPart = parts.get(0);
                        if (firstPart.has("text")) {
                            userMessage = firstPart.get("text").asText();
                        }
                    }
                }

                // Process the task using the handler
                String result = taskHandler.apply(userMessage);

                LOGGER.info("[" + agentName + "] Processed task: " + userMessage.substring(0, Math.min(50, userMessage.length())) + "...");

                // Build A2A response
                ObjectNode resultNode = response.putObject("result");
                resultNode.put("id", taskId);
                resultNode.put("sessionId", params.has("sessionId") ?
                        params.get("sessionId").asText() : UUID.randomUUID().toString());

                ObjectNode status = resultNode.putObject("status");
                status.put("state", "completed");

                ArrayNode artifacts = resultNode.putArray("artifacts");
                ObjectNode artifact = artifacts.addObject();
                artifact.put("name", "response");
                artifact.put("index", 0);

                ArrayNode artifactParts = artifact.putArray("parts");
                ObjectNode textPart = artifactParts.addObject();
                textPart.put("type", "text");
                textPart.put("text", result);

            } else if ("tasks/get".equals(method)) {
                // Return task status
                ObjectNode resultNode = response.putObject("result");
                resultNode.put("id", request.get("params").get("id").asText());
                ObjectNode status = resultNode.putObject("status");
                status.put("state", "completed");

            } else {
                // Unknown method
                ObjectNode error = response.putObject("error");
                error.put("code", -32601);
                error.put("message", "Method not found: " + method);
            }

            String responseBody = objectMapper.writeValueAsString(response);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, responseBody.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
