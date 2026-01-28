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
package io.apicurio.registry.examples.a2a.realworld.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A2A Orchestrator - Discovers agents and coordinates multi-agent workflows.
 *
 * This orchestrator:
 * 1. Discovers agents from the A2A registry (/.well-known/agents)
 * 2. Fetches agent cards to understand their capabilities
 * 3. Sends real A2A JSON-RPC tasks to agents
 * 4. Aggregates results from multiple agents
 */
public class A2AOrchestrator {

    private static final Logger LOGGER = Logger.getLogger(A2AOrchestrator.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String registryBaseUrl;

    public A2AOrchestrator(String registryBaseUrl) {
        this.registryBaseUrl = registryBaseUrl;
    }

    /**
     * Discover all agents from the A2A registry
     */
    public List<AgentInfo> discoverAgents() throws Exception {
        String url = registryBaseUrl + "/.well-known/agents?limit=50";
        LOGGER.info("[Orchestrator] Discovering agents from: " + url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to discover agents: HTTP " + response.statusCode());
        }

        JsonNode body = objectMapper.readTree(response.body());
        List<AgentInfo> agents = new ArrayList<>();

        JsonNode agentsArray = body.get("agents");
        if (agentsArray != null && agentsArray.isArray()) {
            for (JsonNode agentNode : agentsArray) {
                AgentInfo agent = new AgentInfo();
                agent.groupId = agentNode.get("groupId").asText();
                agent.artifactId = agentNode.get("artifactId").asText();
                agent.name = agentNode.get("name").asText();
                agent.description = agentNode.has("description") ?
                        agentNode.get("description").asText() : "";

                // Try to get the full agent card to get the URL
                try {
                    agent.agentCard = fetchAgentCard(agent.groupId, agent.artifactId);
                    if (agent.agentCard.has("url")) {
                        agent.url = agent.agentCard.get("url").asText();
                    }
                } catch (Exception e) {
                    LOGGER.warning("Could not fetch agent card for " + agent.name);
                }

                agents.add(agent);
            }
        }

        LOGGER.info("[Orchestrator] Discovered " + agents.size() + " agents");
        return agents;
    }

    /**
     * Fetch the full agent card from the registry
     */
    public JsonNode fetchAgentCard(String groupId, String artifactId) throws Exception {
        String url = registryBaseUrl + "/.well-known/agents/" + groupId + "/" + artifactId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch agent card: HTTP " + response.statusCode());
        }

        return objectMapper.readTree(response.body());
    }

    /**
     * Fetch agent card directly from the agent's well-known endpoint
     */
    public JsonNode fetchAgentCardDirect(String agentUrl) throws Exception {
        String url = agentUrl + "/.well-known/agent.json";
        LOGGER.info("[Orchestrator] Fetching agent card from: " + url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to fetch agent card: HTTP " + response.statusCode());
        }

        JsonNode card = objectMapper.readTree(response.body());
        LOGGER.info("[Orchestrator] Agent: " + card.get("name").asText() +
                " - Skills: " + card.get("skills").size());
        return card;
    }

    /**
     * Send an A2A task to an agent and get the result
     */
    public String sendTask(String agentUrl, String message) throws Exception {
        String url = agentUrl + "/a2a";
        LOGGER.info("[Orchestrator] Sending task to: " + url);

        // Build A2A JSON-RPC request
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", UUID.randomUUID().toString());
        request.put("method", "tasks/send");

        ObjectNode params = request.putObject("params");
        params.put("id", UUID.randomUUID().toString());
        params.put("sessionId", UUID.randomUUID().toString());

        ObjectNode messageNode = params.putObject("message");
        messageNode.put("role", "user");

        ArrayNode parts = messageNode.putArray("parts");
        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", message);

        String requestBody = objectMapper.writeValueAsString(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        LOGGER.info("[Orchestrator] Response status: " + response.statusCode());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Task failed: HTTP " + response.statusCode() +
                    " - " + response.body());
        }

        // Parse A2A JSON-RPC response
        JsonNode responseNode = objectMapper.readTree(response.body());

        if (responseNode.has("error")) {
            throw new RuntimeException("A2A error: " + responseNode.get("error").toString());
        }

        // Extract the text result from artifacts
        JsonNode result = responseNode.get("result");
        if (result != null && result.has("artifacts")) {
            JsonNode artifacts = result.get("artifacts");
            if (artifacts.isArray() && artifacts.size() > 0) {
                JsonNode firstArtifact = artifacts.get(0);
                if (firstArtifact.has("parts")) {
                    JsonNode artifactParts = firstArtifact.get("parts");
                    if (artifactParts.isArray() && artifactParts.size() > 0) {
                        JsonNode firstPart = artifactParts.get(0);
                        if (firstPart.has("text")) {
                            return firstPart.get("text").asText();
                        }
                    }
                }
            }
        }

        return "No response";
    }

    /**
     * Execute a multi-agent workflow
     */
    public List<WorkflowResult> executeWorkflow(String workflowName, List<WorkflowStep> steps)
            throws Exception {
        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("Executing Workflow: " + workflowName);
        LOGGER.info("================================================================================");

        List<WorkflowResult> results = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            LOGGER.info("");
            LOGGER.info("[Step " + (i + 1) + "/" + steps.size() + "] " + step.description);
            LOGGER.info("  Agent: " + step.agentUrl);
            LOGGER.info("  Task: " + step.task.substring(0, Math.min(80, step.task.length())) + "...");

            try {
                long startTime = System.currentTimeMillis();
                String response = sendTask(step.agentUrl, step.task);
                long duration = System.currentTimeMillis() - startTime;

                WorkflowResult result = new WorkflowResult();
                result.stepName = step.description;
                result.agentUrl = step.agentUrl;
                result.response = response;
                result.durationMs = duration;
                result.success = true;
                results.add(result);

                LOGGER.info("  Result: " + response.substring(0, Math.min(100, response.length())) + "...");
                LOGGER.info("  Duration: " + duration + "ms");

            } catch (Exception e) {
                LOGGER.warning("  FAILED: " + e.getMessage());
                WorkflowResult result = new WorkflowResult();
                result.stepName = step.description;
                result.agentUrl = step.agentUrl;
                result.response = "ERROR: " + e.getMessage();
                result.success = false;
                results.add(result);
            }
        }

        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("Workflow Complete: " + results.stream().filter(r -> r.success).count() +
                "/" + results.size() + " steps succeeded");
        LOGGER.info("================================================================================");

        return results;
    }

    // Helper classes
    public static class AgentInfo {
        public String groupId;
        public String artifactId;
        public String name;
        public String description;
        public String url;
        public JsonNode agentCard;

        @Override
        public String toString() {
            return name + " (" + groupId + "/" + artifactId + ") @ " + url;
        }
    }

    public static class WorkflowStep {
        public String description;
        public String agentUrl;
        public String task;

        public WorkflowStep(String description, String agentUrl, String task) {
            this.description = description;
            this.agentUrl = agentUrl;
            this.task = task;
        }
    }

    public static class WorkflowResult {
        public String stepName;
        public String agentUrl;
        public String response;
        public long durationMs;
        public boolean success;
    }
}
