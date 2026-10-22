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
import io.apicurio.registry.examples.a2a.realworld.llm.AgentPrompts;
import io.apicurio.registry.examples.a2a.realworld.llm.LLMAgentServer;
import io.apicurio.registry.examples.a2a.realworld.llm.OllamaClient;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator.ContextualStep;
import io.apicurio.registry.examples.a2a.realworld.orchestrator.A2AOrchestrator.WorkflowResult;
import io.apicurio.registry.examples.a2a.realworld.web.WebUIServer;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A2A Context Chaining Demo - Multi-Agent Pipeline with Accumulated Context.
 *
 * This example demonstrates context chaining where each agent in a pipeline
 * receives the accumulated outputs from all previous agents, creating a
 * truly integrated multi-agent system.
 *
 * Context Chaining Flow:
 *   1. Sentiment Agent    → receives: {{original}}
 *   2. Analyzer Agent     → receives: {{original}} + {{sentiment}}
 *   3. Response Agent     → receives: {{original}} + {{sentiment}} + {{analysis}}
 *   4. Translation Agent  → receives: {{response}}
 *
 * Each agent builds upon previous work, enabling intelligent decision-making
 * that considers the full context of the conversation.
 *
 * Prerequisites:
 * - Docker Compose running (docker-compose up -d)
 * - Ollama with llama3.2 model ready
 */
public class RealA2ADemo {

    private static final Logger LOGGER = Logger.getLogger(RealA2ADemo.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Configuration from environment or defaults
    private static final String REGISTRY_URL = System.getenv().getOrDefault(
            "REGISTRY_URL", "http://localhost:8080/apis/registry/v3");
    private static final String REGISTRY_BASE = REGISTRY_URL.replace("/apis/registry/v3", "");
    private static final String OLLAMA_URL = System.getenv().getOrDefault(
            "OLLAMA_URL", "http://localhost:11434");
    private static final String OLLAMA_MODEL = System.getenv().getOrDefault(
            "OLLAMA_MODEL", "llama3.2");

    // Ports for our LLM-powered agents
    private static final int SENTIMENT_AGENT_PORT = 9001;
    private static final int ANALYZER_AGENT_PORT = 9002;
    private static final int RESPONSE_AGENT_PORT = 9003;
    private static final int TRANSLATOR_AGENT_PORT = 9004;
    private static final int WEB_UI_PORT = 9000;

    private static final List<MockAgentServer> runningAgents = new ArrayList<>();
    private static OllamaClient ollamaClient;
    private static WebUIServer webUIServer;

    public static void main(String[] args) throws Exception {
        printBanner();

        try {
            // Phase 0: Connect to Ollama and wait for model
            waitForOllama();

            // Phase 1: Start LLM-powered agents
            startLLMAgents();

            // Phase 2: Verify agents are running
            verifyAgentsRunning();

            // Phase 3: Register MODEL_SCHEMA and PROMPT_TEMPLATE artifacts (LLM lifecycle)
            try {
                registerSchemasAndPrompts();
            } catch (Exception e) {
                LOGGER.warning("Schema/prompt registration failed (continuing without): " + e.getMessage());
            }

            // Phase 4: Register agents in Apicurio Registry (optional - may fail if registry not configured)
            try {
                registerAgentsInRegistry();
            } catch (Exception e) {
                LOGGER.warning("Registry registration failed (continuing without): " + e.getMessage());
            }

            // Phase 5: Discover agents via A2A (optional - may fail if registry not configured)
            try {
                discoverAgentsViaA2A();
            } catch (Exception e) {
                LOGGER.warning("Registry discovery failed (continuing without): " + e.getMessage());
            }

            // Phase 6: Start Web UI
            startWebUI();

            // Phase 7: Execute sample workflow
            executeIntelligentWorkflow();

            printCompletionBanner();

            // Keep running so agents can be tested manually
            Thread.sleep(Long.MAX_VALUE);

        } finally {
            stopAgents();
        }
    }

    private static void printBanner() {
        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("  A2A Context Chaining Demo");
        LOGGER.info("  Multi-Agent Pipeline with Accumulated Context");
        LOGGER.info("================================================================================");
        LOGGER.info("");
        LOGGER.info("Each agent receives outputs from ALL previous agents via {{variable}} templates:");
        LOGGER.info("");
        LOGGER.info("  Step 1: Sentiment    -> Input: {{original}}");
        LOGGER.info("  Step 2: Analyzer     -> Input: {{original}} + {{sentiment}}");
        LOGGER.info("  Step 3: Response     -> Input: {{original}} + {{sentiment}} + {{analysis}}");
        LOGGER.info("  Step 4: Translation  -> Input: {{response}}");
        LOGGER.info("");
    }

    // ==================================================================================
    // Phase 0: Connect to Ollama
    // ==================================================================================

    private static void waitForOllama() throws Exception {
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 0: Connecting to Ollama LLM");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        ollamaClient = new OllamaClient(OLLAMA_URL, OLLAMA_MODEL);

        try {
            ollamaClient.waitForReady(Duration.ofMinutes(5));
            LOGGER.info("");
            LOGGER.info("Ollama ready: " + OLLAMA_URL + " (model: " + OLLAMA_MODEL + ")");
        } catch (Exception e) {
            LOGGER.severe("");
            LOGGER.severe("ERROR: Could not connect to Ollama at " + OLLAMA_URL);
            LOGGER.severe("");
            LOGGER.severe("Please ensure Docker Compose is running:");
            LOGGER.severe("  cd examples/a2a-real-world-integration");
            LOGGER.severe("  docker-compose up -d");
            LOGGER.severe("  docker-compose logs -f ollama-init  # Wait for model download");
            LOGGER.severe("");
            throw e;
        }
    }

    // ==================================================================================
    // Phase 1: Start LLM-Powered Agents
    // ==================================================================================

    private static void startLLMAgents() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 1: Starting LLM-Powered Agents");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        // Sentiment Analysis Agent
        LLMAgentServer sentimentAgent = new LLMAgentServer(
                SENTIMENT_AGENT_PORT,
                "Sentiment Analysis Agent",
                AgentPrompts.SENTIMENT_DESCRIPTION,
                AgentPrompts.SENTIMENT_SKILLS,
                ollamaClient,
                AgentPrompts.SENTIMENT_SYSTEM_PROMPT);
        sentimentAgent.start();
        runningAgents.add(sentimentAgent);

        // Issue Analyzer Agent
        LLMAgentServer analyzerAgent = new LLMAgentServer(
                ANALYZER_AGENT_PORT,
                "Issue Analyzer Agent",
                AgentPrompts.ANALYZER_DESCRIPTION,
                AgentPrompts.ANALYZER_SKILLS,
                ollamaClient,
                AgentPrompts.ANALYZER_SYSTEM_PROMPT);
        analyzerAgent.start();
        runningAgents.add(analyzerAgent);

        // Response Generator Agent
        LLMAgentServer responseAgent = new LLMAgentServer(
                RESPONSE_AGENT_PORT,
                "Response Generator Agent",
                AgentPrompts.RESPONSE_GENERATOR_DESCRIPTION,
                AgentPrompts.RESPONSE_GENERATOR_SKILLS,
                ollamaClient,
                AgentPrompts.RESPONSE_GENERATOR_SYSTEM_PROMPT);
        responseAgent.start();
        runningAgents.add(responseAgent);

        // Translation Agent
        LLMAgentServer translatorAgent = new LLMAgentServer(
                TRANSLATOR_AGENT_PORT,
                "Translation Agent",
                AgentPrompts.TRANSLATOR_DESCRIPTION,
                AgentPrompts.TRANSLATOR_SKILLS,
                ollamaClient,
                AgentPrompts.TRANSLATOR_SYSTEM_PROMPT);
        translatorAgent.start();
        runningAgents.add(translatorAgent);

        LOGGER.info("");
        LOGGER.info("Started 4 LLM-powered agents:");
        LOGGER.info("  - Sentiment Analysis Agent on :" + SENTIMENT_AGENT_PORT);
        LOGGER.info("  - Issue Analyzer Agent on :" + ANALYZER_AGENT_PORT);
        LOGGER.info("  - Response Generator Agent on :" + RESPONSE_AGENT_PORT);
        LOGGER.info("  - Translation Agent on :" + TRANSLATOR_AGENT_PORT);
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

        int[] ports = {SENTIMENT_AGENT_PORT, ANALYZER_AGENT_PORT,
                RESPONSE_AGENT_PORT, TRANSLATOR_AGENT_PORT};

        for (int port : ports) {
            String url = "http://localhost:" + port + "/.well-known/agent.json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode agentCard = objectMapper.readTree(response.body());
                LOGGER.info("  [OK] " + agentCard.get("name").asText() +
                        " - " + agentCard.get("skills").size() + " skills");
            } else {
                LOGGER.warning("  [FAIL] Port " + port + ": HTTP " + response.statusCode());
            }
        }
    }

    // ==================================================================================
    // Phase 3: Register MODEL_SCHEMA and PROMPT_TEMPLATE Artifacts
    // ==================================================================================

    private static void registerSchemasAndPrompts() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 3: Registering MODEL_SCHEMA and PROMPT_TEMPLATE Artifacts");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create(REGISTRY_URL));

        // Create groups for schemas and prompts
        createGroupIfNotExists(client, "llm-agents.schemas",
                "Input/output schemas for LLM agents (MODEL_SCHEMA artifacts)");
        createGroupIfNotExists(client, "llm-agents.prompts",
                "System prompts for LLM agents (PROMPT_TEMPLATE artifacts)");

        // Register MODEL_SCHEMA artifacts
        LOGGER.info("Registering MODEL_SCHEMA artifacts...");
        String[] schemaFiles = {
                "sentiment-agent-output.json",
                "analyzer-agent-output.json",
                "response-generator-output.json",
                "translator-agent-output.json"
        };

        for (String file : schemaFiles) {
            String content = loadResource("schemas/" + file);
            String artifactId = file.replace(".json", "");
            registerArtifact(client, "llm-agents.schemas", artifactId,
                    "MODEL_SCHEMA", content, "application/json");
            LOGGER.info("  Registered schema: " + artifactId);
        }

        // Register PROMPT_TEMPLATE artifacts
        LOGGER.info("Registering PROMPT_TEMPLATE artifacts...");
        String[] promptFiles = {
                "sentiment-agent-prompt.yaml",
                "analyzer-agent-prompt.yaml",
                "response-generator-prompt.yaml",
                "translator-agent-prompt.yaml"
        };

        for (String file : promptFiles) {
            String content = loadResource("prompts/" + file);
            String artifactId = file.replace(".yaml", "");
            registerArtifact(client, "llm-agents.prompts", artifactId,
                    "PROMPT_TEMPLATE", content, "application/yaml");
            LOGGER.info("  Registered prompt: " + artifactId);
        }

        LOGGER.info("");
        LOGGER.info("LLM artifact registration complete:");
        LOGGER.info("  - " + schemaFiles.length + " MODEL_SCHEMA artifacts");
        LOGGER.info("  - " + promptFiles.length + " PROMPT_TEMPLATE artifacts");
    }

    private static void createGroupIfNotExists(RegistryClient client, String groupId, String description) {
        try {
            CreateGroup group = new CreateGroup();
            group.setGroupId(groupId);
            group.setDescription(description);
            client.groups().post(group);
            LOGGER.info("Created group: " + groupId);
        } catch (Exception e) {
            // Group may already exist
        }
    }

    private static void registerArtifact(RegistryClient client, String groupId, String artifactId,
            String artifactType, String content, String contentType) {
        CreateArtifact artifact = new CreateArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setArtifactType(artifactType);

        CreateVersion version = new CreateVersion();
        version.setVersion("1.0.0");
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(contentType);
        version.setContent(versionContent);
        artifact.setFirstVersion(version);

        client.groups().byGroupId(groupId).artifacts()
                .post(artifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                });
    }

    private static String loadResource(String path) throws Exception {
        try (InputStream is = RealA2ADemo.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    // ==================================================================================
    // Phase 4: Register Agents in Registry
    // ==================================================================================

    private static void registerAgentsInRegistry() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 4: Registering Agents in Apicurio Registry");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create(REGISTRY_URL));

        // Create group
        try {
            CreateGroup group = new CreateGroup();
            group.setGroupId("demo.llm-agents");
            group.setDescription("LLM-powered A2A agents using Ollama llama3.2");
            client.groups().post(group);
        } catch (Exception e) {
            // Group may exist
        }

        // Register each agent
        int[] ports = {SENTIMENT_AGENT_PORT, ANALYZER_AGENT_PORT,
                RESPONSE_AGENT_PORT, TRANSLATOR_AGENT_PORT};
        String[] artifactIds = {"sentiment-agent", "analyzer-agent",
                "response-agent", "translator-agent"};

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

            client.groups().byGroupId("demo.llm-agents").artifacts()
                    .post(artifact, config -> {
                        config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    });

            LOGGER.info("  Registered: " + agentCard.get("name").asText());
        }
    }

    // ==================================================================================
    // Phase 5: Discover Agents via A2A
    // ==================================================================================

    private static void discoverAgentsViaA2A() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 5: Discovering Agents via A2A Registry Endpoints");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        A2AOrchestrator orchestrator = new A2AOrchestrator(REGISTRY_BASE);

        LOGGER.info("Querying: " + REGISTRY_BASE + "/.well-known/agents");
        var agents = orchestrator.discoverAgents();

        LOGGER.info("Found " + agents.size() + " agents:");
        for (var agent : agents) {
            LOGGER.info("  - " + agent.name + (agent.url != null ? " @ " + agent.url : ""));
        }
    }

    // ==================================================================================
    // Phase 6: Start Web UI
    // ==================================================================================

    private static void startWebUI() throws Exception {
        LOGGER.info("");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Phase 5: Starting Web UI");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        webUIServer = new WebUIServer(
            WEB_UI_PORT,
            SENTIMENT_AGENT_PORT,
            ANALYZER_AGENT_PORT,
            RESPONSE_AGENT_PORT,
            TRANSLATOR_AGENT_PORT
        );
        webUIServer.start();

        LOGGER.info("");
        LOGGER.info("Web UI available at: http://localhost:" + WEB_UI_PORT);
        LOGGER.info("Submit customer complaints through the web interface!");
    }

    // ==================================================================================
    // Phase 7: Execute Intelligent Multi-Agent Workflow with Context Chaining
    // ==================================================================================

    private static void executeIntelligentWorkflow() throws Exception {
        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("Phase 7: Executing Sample Workflow (Demo)");
        LOGGER.info("================================================================================");
        LOGGER.info("");
        LOGGER.info("This workflow uses CONTEXT CHAINING - each agent receives outputs from");
        LOGGER.info("previous agents, creating a true integrated pipeline.");
        LOGGER.info("");

        // Realistic customer complaint message
        String customerMessage = """
            I've been waiting 3 weeks for my order #12345 and nobody responds to my emails!
            This is unacceptable. I've been a loyal customer for 5 years and I expected
            much better treatment. The tracking hasn't updated in 10 days and your phone
            support just puts me on hold forever. I want a full refund immediately or I'm
            disputing the charge with my bank and posting about this on social media.
            """.strip();

        LOGGER.info("INPUT MESSAGE:");
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info(customerMessage);
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("");

        // Define contextual steps with template variables and registry references.
        // Each step can reference previous outputs using {{variable}} syntax.
        // Enhanced with schema and prompt template references for LLM lifecycle management.
        List<ContextualStep> steps = List.of(
                // Step 1: Sentiment analysis (just original message)
                new ContextualStep(
                        "Analyze customer sentiment and emotions",
                        "http://localhost:" + SENTIMENT_AGENT_PORT,
                        "sentiment",
                        "{{original}}"
                )
                .withPromptTemplate("urn:apicurio:llm-agents.prompts/sentiment-agent-prompt")
                .withOutputSchema("urn:apicurio:llm-agents.schemas/sentiment-agent-output"),

                // Step 2: Issue analysis WITH sentiment context
                new ContextualStep(
                        "Extract issues and entities with sentiment context",
                        "http://localhost:" + ANALYZER_AGENT_PORT,
                        "analysis",
                        """
                        CUSTOMER MESSAGE:
                        {{original}}

                        SENTIMENT ANALYSIS (from previous agent):
                        {{sentiment}}

                        Based on the sentiment analysis above, perform issue extraction.
                        Consider the urgency and emotional state when prioritizing.
                        """
                )
                .withPromptTemplate("urn:apicurio:llm-agents.prompts/analyzer-agent-prompt")
                .withOutputSchema("urn:apicurio:llm-agents.schemas/analyzer-agent-output"),

                // Step 3: Response with full context
                new ContextualStep(
                        "Generate response using sentiment and analysis",
                        "http://localhost:" + RESPONSE_AGENT_PORT,
                        "response",
                        """
                        CUSTOMER MESSAGE:
                        {{original}}

                        SENTIMENT ANALYSIS:
                        {{sentiment}}

                        ISSUE ANALYSIS:
                        {{analysis}}

                        Generate an empathetic response that:
                        - Acknowledges the customer's emotional state
                        - Addresses all identified issues
                        - Matches the urgency level identified
                        """
                )
                .withPromptTemplate("urn:apicurio:llm-agents.prompts/response-generator-prompt")
                .withOutputSchema("urn:apicurio:llm-agents.schemas/response-generator-output"),

                // Step 4: Translate the ACTUAL response
                new ContextualStep(
                        "Translate response to Spanish",
                        "http://localhost:" + TRANSLATOR_AGENT_PORT,
                        "translation",
                        """
                        Translate the following customer service response to Spanish:

                        {{response}}
                        """
                )
                .withPromptTemplate("urn:apicurio:llm-agents.prompts/translator-agent-prompt")
                .withOutputSchema("urn:apicurio:llm-agents.schemas/translator-agent-output")
        );

        // Execute contextual workflow
        A2AOrchestrator orchestrator = new A2AOrchestrator(REGISTRY_BASE);
        List<WorkflowResult> results = orchestrator.executeContextualWorkflow(
                "Integrated Customer Complaint Resolution Pipeline",
                steps,
                customerMessage);

        // Display results showing the integrated flow
        displayIntegratedResults(results);
    }

    /**
     * Display results highlighting the integrated context flow.
     */
    private static void displayIntegratedResults(List<WorkflowResult> results) {
        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("INTEGRATED WORKFLOW RESULTS");
        LOGGER.info("================================================================================");
        LOGGER.info("");
        LOGGER.info("Each agent built upon the previous agent's output:");
        LOGGER.info("");

        for (int i = 0; i < results.size(); i++) {
            WorkflowResult result = results.get(i);
            LOGGER.info("--------------------------------------------------------------------------------");
            LOGGER.info("Step " + (i + 1) + ": " + result.stepName);
            LOGGER.info("  Status: " + (result.success ? "SUCCESS" : "FAILED"));
            LOGGER.info("  Duration: " + result.durationMs + "ms");
            LOGGER.info("");

            // Pretty print JSON response
            try {
                JsonNode json = objectMapper.readTree(result.response);
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(json);
                for (String line : prettyJson.split("\n")) {
                    LOGGER.info("  " + line);
                }
            } catch (Exception e) {
                // Not JSON, print as-is
                LOGGER.info("  Response: " + result.response);
            }
            LOGGER.info("");
        }

        // Summary statistics
        long totalDuration = results.stream().mapToLong(r -> r.durationMs).sum();
        long successCount = results.stream().filter(r -> r.success).count();

        LOGGER.info("================================================================================");
        LOGGER.info("PIPELINE STATISTICS:");
        LOGGER.info("  Total LLM Processing Time: " + totalDuration + "ms");
        LOGGER.info("  Success Rate: " + successCount + "/" + results.size());
        LOGGER.info("  Model: " + OLLAMA_MODEL + " (local)");
        LOGGER.info("  Agents Used: " + results.size());
        LOGGER.info("");
        LOGGER.info("CONTEXT CHAINING VERIFICATION:");
        LOGGER.info("  - Step 2 received sentiment from Step 1");
        LOGGER.info("  - Step 3 received sentiment + analysis from Steps 1-2");
        LOGGER.info("  - Step 4 translated the actual response from Step 3");
        LOGGER.info("================================================================================");
    }

    private static void printCompletionBanner() {
        LOGGER.info("");
        LOGGER.info("================================================================================");
        LOGGER.info("Context Chaining Demo Ready!");
        LOGGER.info("================================================================================");
        LOGGER.info("");
        LOGGER.info("WEB UI: http://localhost:" + WEB_UI_PORT);
        LOGGER.info("");
        LOGGER.info("Open the URL above in your browser to submit customer complaints.");
        LOGGER.info("Each complaint will be processed through the 4-agent context chaining pipeline.");
        LOGGER.info("");
        LOGGER.info("Pipeline flow:");
        LOGGER.info("  1. Sentiment Analysis  -> {{original}}");
        LOGGER.info("  2. Issue Analyzer      -> {{original}} + {{sentiment}}");
        LOGGER.info("  3. Response Generator  -> {{original}} + {{sentiment}} + {{analysis}}");
        LOGGER.info("  4. Translator          -> {{response}}");
        LOGGER.info("");
        LOGGER.info("Press Ctrl+C to stop.");
    }

    private static void stopAgents() {
        LOGGER.info("");
        LOGGER.info("Stopping services...");
        if (webUIServer != null) {
            webUIServer.stop();
        }
        for (MockAgentServer agent : runningAgents) {
            agent.stop();
        }
    }
}
