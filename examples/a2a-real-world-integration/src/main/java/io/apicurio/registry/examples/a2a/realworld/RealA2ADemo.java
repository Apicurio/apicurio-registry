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
package io.apicurio.registry.examples.a2a.realworld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.examples.a2a.realworld.agents.MockAgentServer;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator.WorkflowResult;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator.WorkflowStep;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Real A2A Demo - Demonstrates ACTUAL working A2A protocol integration.
 *
 * This example:
 * 1. Starts real HTTP servers (mock agents) that implement the A2A protocol
 * 2. Registers their agent cards in Apicurio Registry
 * 3. Uses the A2A Orchestrator to discover agents via registry endpoints
 * 4. Sends real A2A JSON-RPC tasks to agents and gets responses
 * 5. Demonstrates a multi-agent workflow with actual HTTP communication
 *
 * All communication uses the actual A2A protocol:
 * - /.well-known/agent.json for agent discovery
 * - /a2a endpoint for JSON-RPC task submission
 */
public class RealA2ADemo {

    private static final Logger LOGGER = Logger.getLogger(RealA2ADemo.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String REGISTRY_URL = System.getenv().getOrDefault(
            "REGISTRY_URL", "http://localhost:8080/apis/registry/v3");
    private static final String REGISTRY_BASE = REGISTRY_URL.replace("/apis/registry/v3", "");

    // Ports for our mock agents
    private static final int SENTIMENT_AGENT_PORT = 9001;
    private static final int SUMMARY_AGENT_PORT = 9002;
    private static final int TRANSLATE_AGENT_PORT = 9003;

    private static final List<MockAgentServer> runningAgents = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        LOGGER.info("================================================================================");
        LOGGER.info("Real A2A Protocol Demo - Actual Working Agent Integration");
        LOGGER.info("================================================================================");
        LOGGER.info("");
        LOGGER.info("This demo runs REAL HTTP servers implementing the A2A protocol.");
        LOGGER.info("All agent discovery and task execution uses actual HTTP communication.");
        LOGGER.info("");

        try {
            // Phase 1: Start mock agents (real HTTP servers)
            startMockAgents();

            // Phase 2: Verify agents are running and accessible
            verifyAgentsRunning();

            // Phase 3: Register agents in Apicurio Registry
            registerAgentsInRegistry();

            // Phase 4: Use orchestrator to discover agents
            discoverAgentsViaA2A();

            // Phase 5: Execute a real multi-agent workflow
            executeRealWorkflow();

            LOGGER.info("");
            LOGGER.info("================================================================================");
            LOGGER.info("Demo Complete - All A2A communication was REAL HTTP traffic!");
            LOGGER.info("================================================================================");
            LOGGER.info("");
            LOGGER.info("You can verify the agents are still running:");
            LOGGER.info("  curl http://localhost:" + SENTIMENT_AGENT_PORT + "/.well-known/agent.json");
            LOGGER.info("  curl http://localhost:" + SUMMARY_AGENT_PORT + "/.well-known/agent.json");
            LOGGER.info("  curl http://localhost:" + TRANSLATE_AGENT_PORT + "/.well-known/agent.json");
            LOGGER.info("");
            LOGGER.info("Registry UI: http://localhost:8888");
            LOGGER.info("");
            LOGGER.info("Press Ctrl+C to stop the agents...");

            // Keep running so agents can be tested manually
            Thread.sleep(Long.MAX_VALUE);

        } finally {
            stopMockAgents();
        }
    }

    // ==================================================================================
    // Phase 1: Start Mock Agents (Real HTTP Servers)
    // ==================================================================================

    private static void startMockAgents() throws Exception {
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 1: Starting Mock Agents (Real HTTP Servers)");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        // Sentiment Analysis Agent
        MockAgentServer sentimentAgent = new MockAgentServer(
                SENTIMENT_AGENT_PORT,
                "Sentiment Analysis Agent",
                "Analyzes text sentiment and returns positive/negative/neutral classification with confidence score",
                new String[]{"sentiment-analysis", "emotion-detection"},
                (message) -> {
                    // Simple mock logic - in reality this would call an AI model
                    String lower = message.toLowerCase();
                    String sentiment;
                    double confidence;

                    if (lower.contains("happy") || lower.contains("great") || lower.contains("love")
                            || lower.contains("excellent") || lower.contains("amazing")) {
                        sentiment = "POSITIVE";
                        confidence = 0.85 + new Random().nextDouble() * 0.14;
                    } else if (lower.contains("sad") || lower.contains("angry") || lower.contains("hate")
                            || lower.contains("terrible") || lower.contains("frustrated")) {
                        sentiment = "NEGATIVE";
                        confidence = 0.80 + new Random().nextDouble() * 0.19;
                    } else {
                        sentiment = "NEUTRAL";
                        confidence = 0.60 + new Random().nextDouble() * 0.25;
                    }

                    return String.format(
                            "{\"sentiment\": \"%s\", \"confidence\": %.2f, \"analyzed_text_length\": %d}",
                            sentiment, confidence, message.length());
                });
        sentimentAgent.start();
        runningAgents.add(sentimentAgent);

        // Text Summarization Agent
        MockAgentServer summaryAgent = new MockAgentServer(
                SUMMARY_AGENT_PORT,
                "Text Summarization Agent",
                "Summarizes long text into concise summaries while preserving key information",
                new String[]{"text-summarization", "key-extraction"},
                (message) -> {
                    // Simple mock summarization
                    String[] sentences = message.split("[.!?]");
                    int targetSentences = Math.min(2, sentences.length);
                    StringBuilder summary = new StringBuilder();
                    for (int i = 0; i < targetSentences; i++) {
                        if (!sentences[i].trim().isEmpty()) {
                            summary.append(sentences[i].trim()).append(". ");
                        }
                    }
                    if (summary.length() == 0) {
                        summary.append(message.substring(0, Math.min(100, message.length())));
                    }
                    return String.format(
                            "{\"summary\": \"%s\", \"original_length\": %d, \"summary_length\": %d, \"compression_ratio\": %.2f}",
                            summary.toString().replace("\"", "'"),
                            message.length(),
                            summary.length(),
                            (double) summary.length() / message.length());
                });
        summaryAgent.start();
        runningAgents.add(summaryAgent);

        // Translation Agent
        MockAgentServer translateAgent = new MockAgentServer(
                TRANSLATE_AGENT_PORT,
                "Translation Agent",
                "Translates text between languages using advanced neural machine translation",
                new String[]{"translation", "language-detection"},
                (message) -> {
                    // Mock translation - just reverse words for demo
                    String[] words = message.split(" ");
                    StringBuilder translated = new StringBuilder();
                    for (int i = words.length - 1; i >= 0; i--) {
                        translated.append(words[i]);
                        if (i > 0) {
                            translated.append(" ");
                        }
                    }
                    return String.format(
                            "{\"translated_text\": \"%s\", \"source_language\": \"en\", " +
                                    "\"target_language\": \"demo\", \"word_count\": %d}",
                            translated.toString().replace("\"", "'"),
                            words.length);
                });
        translateAgent.start();
        runningAgents.add(translateAgent);

        LOGGER.info("");
        LOGGER.info("Started 3 mock agents on ports " + SENTIMENT_AGENT_PORT + ", " +
                SUMMARY_AGENT_PORT + ", " + TRANSLATE_AGENT_PORT);
    }

    // ==================================================================================
    // Phase 2: Verify Agents are Running
    // ==================================================================================

    private static void verifyAgentsRunning() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 2: Verifying Agents via A2A Discovery Endpoints");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        int[] ports = {SENTIMENT_AGENT_PORT, SUMMARY_AGENT_PORT, TRANSLATE_AGENT_PORT};
        for (int port : ports) {
            String url = "http://localhost:" + port + "/.well-known/agent.json";
            LOGGER.info("GET " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode agentCard = objectMapper.readTree(response.body());
                LOGGER.info("  ✓ " + agentCard.get("name").asText() +
                        " - " + agentCard.get("skills").size() + " skills");
            } else {
                LOGGER.warning("  ✗ Failed: HTTP " + response.statusCode());
            }
        }
    }

    // ==================================================================================
    // Phase 3: Register Agents in Registry
    // ==================================================================================

    private static void registerAgentsInRegistry() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 3: Registering Agents in Apicurio Registry");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create(REGISTRY_URL));

        // Create group
        try {
            CreateGroup group = new CreateGroup();
            group.setGroupId("demo.agents");
            group.setDescription("Demo A2A agents for real-world integration");
            client.groups().post(group);
        } catch (Exception e) {
            // Group may exist
        }

        // Register each agent by fetching their actual agent card
        int[] ports = {SENTIMENT_AGENT_PORT, SUMMARY_AGENT_PORT, TRANSLATE_AGENT_PORT};
        String[] artifactIds = {"sentiment-agent", "summary-agent", "translate-agent"};

        for (int i = 0; i < ports.length; i++) {
            String url = "http://localhost:" + ports[i] + "/.well-known/agent.json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String agentCardJson = response.body();
            JsonNode agentCard = objectMapper.readTree(agentCardJson);

            CreateArtifact artifact = new CreateArtifact();
            artifact.setArtifactId(artifactIds[i]);
            artifact.setArtifactType("AGENT_CARD");
            artifact.setName(agentCard.get("name").asText());
            artifact.setDescription(agentCard.get("description").asText());

            CreateVersion version = new CreateVersion();
            version.setVersion("1.0.0");
            VersionContent content = new VersionContent();
            content.setContent(agentCardJson);
            content.setContentType("application/json");
            version.setContent(content);
            artifact.setFirstVersion(version);

            client.groups().byGroupId("demo.agents").artifacts()
                    .post(artifact, config -> {
                        config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    });

            LOGGER.info("  Registered: " + agentCard.get("name").asText() +
                    " @ http://localhost:" + ports[i]);
        }
    }

    // ==================================================================================
    // Phase 4: Discover Agents via A2A
    // ==================================================================================

    private static void discoverAgentsViaA2A() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 4: Discovering Agents via A2A Registry Endpoints");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        A2AOrchestrator orchestrator = new A2AOrchestrator(REGISTRY_BASE);

        // Discover all agents from registry
        LOGGER.info("[Using /.well-known/agents endpoint]");
        var agents = orchestrator.discoverAgents();

        for (var agent : agents) {
            LOGGER.info("  Found: " + agent.name);
            if (agent.url != null) {
                LOGGER.info("    URL: " + agent.url);
            }
        }

        // Test direct agent discovery
        LOGGER.info("");
        LOGGER.info("[Using direct agent /.well-known/agent.json endpoints]");
        int[] ports = {SENTIMENT_AGENT_PORT, SUMMARY_AGENT_PORT, TRANSLATE_AGENT_PORT};
        for (int port : ports) {
            JsonNode card = orchestrator.fetchAgentCardDirect("http://localhost:" + port);
            LOGGER.info("  Direct discovery: " + card.get("name").asText());
        }
    }

    // ==================================================================================
    // Phase 5: Execute Real Multi-Agent Workflow
    // ==================================================================================

    private static void executeRealWorkflow() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 5: Executing Real Multi-Agent Workflow");
        LOGGER.info("--------------------------------------------------------------------------------");

        A2AOrchestrator orchestrator = new A2AOrchestrator(REGISTRY_BASE);

        // Create a workflow that chains multiple agents
        List<WorkflowStep> steps = new ArrayList<>();

        String customerMessage = "I am so frustrated with your service! The product arrived broken " +
                "and customer support has been unhelpful. I've been a loyal customer for 5 years " +
                "and I expected much better treatment. This is completely unacceptable and I want " +
                "a full refund immediately!";

        // Step 1: Analyze sentiment
        steps.add(new WorkflowStep(
                "Analyze customer sentiment",
                "http://localhost:" + SENTIMENT_AGENT_PORT,
                customerMessage));

        // Step 2: Summarize the complaint
        steps.add(new WorkflowStep(
                "Summarize the customer complaint",
                "http://localhost:" + SUMMARY_AGENT_PORT,
                customerMessage));

        // Step 3: Translate for demo
        steps.add(new WorkflowStep(
                "Process text through translation agent",
                "http://localhost:" + TRANSLATE_AGENT_PORT,
                "Please process this refund request urgently"));

        // Execute the workflow
        List<WorkflowResult> results = orchestrator.executeWorkflow(
                "Customer Complaint Processing Pipeline",
                steps);

        // Display aggregated results
        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("AGGREGATED WORKFLOW RESULTS");
        LOGGER.info("================================================================================");
        LOGGER.info("");
        LOGGER.info("Original Customer Message:");
        LOGGER.info("  \"" + customerMessage.substring(0, 80) + "...\"");
        LOGGER.info("");

        for (WorkflowResult result : results) {
            LOGGER.info("Step: " + result.stepName);
            LOGGER.info("  Status: " + (result.success ? "SUCCESS" : "FAILED"));
            LOGGER.info("  Response: " + result.response);
            LOGGER.info("  Duration: " + result.durationMs + "ms");
            LOGGER.info("");
        }

        // Calculate total
        long totalDuration = results.stream().mapToLong(r -> r.durationMs).sum();
        long successCount = results.stream().filter(r -> r.success).count();

        LOGGER.info("Total Duration: " + totalDuration + "ms");
        LOGGER.info("Success Rate: " + successCount + "/" + results.size());
    }

    private static void stopMockAgents() {
        LOGGER.info("");
        LOGGER.info("Stopping mock agents...");
        for (MockAgentServer agent : runningAgents) {
            agent.stop();
        }
    }
}
