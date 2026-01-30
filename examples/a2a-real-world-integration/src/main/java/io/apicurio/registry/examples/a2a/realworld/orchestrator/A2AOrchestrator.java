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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A2A Orchestrator with Context Chaining - Coordinates multi-agent workflows
 * where each agent receives accumulated context from previous agents.
 *
 * Key features:
 * 1. Context chaining via {{variable}} template substitution
 * 2. Each agent's output is stored and passed to subsequent agents
 * 3. Sends real A2A JSON-RPC tasks to agents
 * 4. Optional registry discovery via /.well-known/agents
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
     * Render a prompt template using the registry's render endpoint.
     * This calls: POST /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/render
     *
     * @param groupId The group ID of the prompt template artifact
     * @param artifactId The artifact ID of the prompt template
     * @param version The version expression (e.g., "1.0.0" or "latest")
     * @param variables Map of variable names to values for template substitution
     * @return The rendered prompt string
     */
    public String renderPromptTemplate(String groupId, String artifactId, String version,
                                        Map<String, Object> variables) throws Exception {
        String url = registryBaseUrl + "/apis/registry/v3/groups/" + groupId +
                "/artifacts/" + artifactId + "/versions/" + version + "/render";
        LOGGER.info("[Orchestrator] Rendering template via registry: " + url);

        // Build RenderPromptRequest body
        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode variablesNode = requestBody.putObject("variables");
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                variablesNode.put(entry.getKey(), (String) value);
            } else if (value instanceof Integer) {
                variablesNode.put(entry.getKey(), (Integer) value);
            } else if (value instanceof Long) {
                variablesNode.put(entry.getKey(), (Long) value);
            } else if (value instanceof Double) {
                variablesNode.put(entry.getKey(), (Double) value);
            } else if (value instanceof Boolean) {
                variablesNode.put(entry.getKey(), (Boolean) value);
            } else {
                variablesNode.put(entry.getKey(), value != null ? value.toString() : null);
            }
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to render template: HTTP " + response.statusCode() +
                    " - " + response.body());
        }

        JsonNode responseNode = objectMapper.readTree(response.body());

        // Check for validation errors
        if (responseNode.has("validationErrors")) {
            JsonNode errors = responseNode.get("validationErrors");
            if (errors.isArray() && errors.size() > 0) {
                LOGGER.warning("[Orchestrator] Template validation warnings: " + errors.toString());
            }
        }

        // Return the rendered prompt
        if (responseNode.has("rendered")) {
            return responseNode.get("rendered").asText();
        }

        throw new RuntimeException("Render response missing 'rendered' field: " + response.body());
    }

    /**
     * Parse a URN reference into groupId, artifactId, and optional version.
     * Format: urn:apicurio:groupId/artifactId or urn:apicurio:groupId/artifactId@version
     *
     * @param urn The URN reference string
     * @return String array with [groupId, artifactId, version] (version may be "latest")
     */
    public String[] parsePromptTemplateRef(String urn) {
        if (urn == null || !urn.startsWith("urn:apicurio:")) {
            throw new IllegalArgumentException("Invalid URN format. Expected: urn:apicurio:groupId/artifactId[@version]");
        }

        String path = urn.substring("urn:apicurio:".length());
        String version = "latest";

        // Check for version suffix
        if (path.contains("@")) {
            int atIndex = path.lastIndexOf('@');
            version = path.substring(atIndex + 1);
            path = path.substring(0, atIndex);
        }

        // Split into groupId and artifactId
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex == -1) {
            throw new IllegalArgumentException("Invalid URN format. Expected: urn:apicurio:groupId/artifactId[@version]");
        }

        String groupId = path.substring(0, slashIndex);
        String artifactId = path.substring(slashIndex + 1);

        return new String[]{groupId, artifactId, version};
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

    // ==================================================================================
    // Helper Classes
    // ==================================================================================
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

    public static class WorkflowResult {
        public String stepName;
        public String agentUrl;
        public String response;
        public long durationMs;
        public boolean success;
    }

    /**
     * Workflow context that accumulates outputs from each agent step.
     * Enables context chaining where each agent can access previous agents' outputs.
     * Supports both local template substitution and registry-based rendering.
     */
    public static class WorkflowContext {
        public String originalMessage;
        public Map<String, String> agentOutputs = new LinkedHashMap<>();

        /**
         * Build a prompt by substituting template variables with context values.
         * This is the local fallback method when no registry template reference is provided.
         *
         * @param template Template with {{variable}} placeholders
         * @return Template with placeholders replaced by actual values
         */
        public String buildPrompt(String template) {
            String result = template.replace("{{original}}", originalMessage);
            for (Map.Entry<String, String> entry : agentOutputs.entrySet()) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return result;
        }

        /**
         * Build the variables map for registry-based template rendering.
         * Converts the context into a map suitable for the render endpoint.
         *
         * @return Map of variable names to values including original message and all agent outputs
         */
        public Map<String, Object> buildVariablesMap() {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("original", originalMessage);
            variables.putAll(agentOutputs);
            return variables;
        }
    }

    /**
     * A workflow step that uses templates with context variables.
     * Each step can reference outputs from previous steps using {{variable}} syntax.
     * Enhanced with schema and prompt template references for registry integration.
     */
    public static class ContextualStep {
        public String description;
        public String agentUrl;
        public String outputKey;      // Key to store result in context
        public String taskTemplate;   // Template with {{variable}} placeholders

        // Registry integration fields (Phase 6)
        public String promptTemplateRef;   // URN reference: urn:apicurio:group/artifact
        public String outputSchemaRef;     // URN reference: urn:apicurio:group/artifact
        public boolean validateOutput = true;

        public ContextualStep(String description, String agentUrl,
                              String outputKey, String taskTemplate) {
            this.description = description;
            this.agentUrl = agentUrl;
            this.outputKey = outputKey;
            this.taskTemplate = taskTemplate;
        }

        /**
         * Set the prompt template reference from the registry.
         * @param ref URN reference like "urn:apicurio:llm-agents.prompts/sentiment-agent-prompt"
         * @return this step for method chaining
         */
        public ContextualStep withPromptTemplate(String ref) {
            this.promptTemplateRef = ref;
            return this;
        }

        /**
         * Set the output schema reference for validation.
         * @param ref URN reference like "urn:apicurio:llm-agents.schemas/sentiment-agent-output"
         * @return this step for method chaining
         */
        public ContextualStep withOutputSchema(String ref) {
            this.outputSchemaRef = ref;
            return this;
        }

        /**
         * Enable or disable output validation against the schema.
         * @param validate whether to validate output (default true)
         * @return this step for method chaining
         */
        public ContextualStep withValidation(boolean validate) {
            this.validateOutput = validate;
            return this;
        }
    }

    /**
     * Execute a context-aware workflow where each agent receives accumulated context.
     *
     * This method implements context chaining - each agent's output is stored and
     * made available to subsequent agents via template variables.
     *
     * @param workflowName Name of the workflow for logging
     * @param steps List of contextual steps to execute
     * @param originalMessage The original message to process
     * @return List of workflow results from each step
     */
    public List<WorkflowResult> executeContextualWorkflow(
            String workflowName,
            List<ContextualStep> steps,
            String originalMessage) throws Exception {

        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("Executing Contextual Workflow: " + workflowName);
        LOGGER.info("================================================================================");

        WorkflowContext context = new WorkflowContext();
        context.originalMessage = originalMessage;
        List<WorkflowResult> results = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            ContextualStep step = steps.get(i);

            // Build prompt: use registry render endpoint if promptTemplateRef is set, otherwise use local substitution
            String contextualTask;
            boolean usedRegistryRender = false;

            if (step.promptTemplateRef != null && !step.promptTemplateRef.isEmpty()) {
                // Use registry's render endpoint for server-side template rendering with validation
                try {
                    String[] parsed = parsePromptTemplateRef(step.promptTemplateRef);
                    String groupId = parsed[0];
                    String artifactId = parsed[1];
                    String version = parsed[2];

                    Map<String, Object> variables = context.buildVariablesMap();
                    contextualTask = renderPromptTemplate(groupId, artifactId, version, variables);
                    usedRegistryRender = true;
                    LOGGER.info("[Step " + (i + 1) + "/" + steps.size() + "] " + step.description);
                    LOGGER.info("  Using registry render endpoint: " + step.promptTemplateRef);
                } catch (Exception e) {
                    LOGGER.warning("  Failed to render via registry, falling back to local: " + e.getMessage());
                    contextualTask = context.buildPrompt(step.taskTemplate);
                }
            } else {
                // Use local template substitution as fallback
                contextualTask = context.buildPrompt(step.taskTemplate);
            }

            if (!usedRegistryRender) {
                LOGGER.info("");
                LOGGER.info("[Step " + (i + 1) + "/" + steps.size() + "] " + step.description);
            }
            LOGGER.info("  Agent: " + step.agentUrl);
            LOGGER.info("  Context keys available: " + context.agentOutputs.keySet());
            LOGGER.info("  Task preview: " + contextualTask.substring(0, Math.min(100, contextualTask.length())) + "...");

            try {
                long startTime = System.currentTimeMillis();
                String response = sendTask(step.agentUrl, contextualTask);
                long duration = System.currentTimeMillis() - startTime;

                // Store result in context for subsequent agents
                context.agentOutputs.put(step.outputKey, response);

                WorkflowResult result = new WorkflowResult();
                result.stepName = step.description;
                result.agentUrl = step.agentUrl;
                result.response = response;
                result.durationMs = duration;
                result.success = true;
                results.add(result);

                LOGGER.info("  Stored output as: " + step.outputKey);
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

                // Store error in context so subsequent steps can see it failed
                context.agentOutputs.put(step.outputKey, "ERROR: " + e.getMessage());
            }
        }

        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("Contextual Workflow Complete: " + results.stream().filter(r -> r.success).count() +
                "/" + results.size() + " steps succeeded");
        LOGGER.info("================================================================================");

        return results;
    }
}
