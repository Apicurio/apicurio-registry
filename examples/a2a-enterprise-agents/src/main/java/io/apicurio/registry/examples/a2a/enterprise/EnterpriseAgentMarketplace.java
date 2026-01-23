package io.apicurio.registry.examples.a2a.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.SearchedArtifact;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise AI Agent Marketplace - Real-World A2A Example
 * <p>
 * This example demonstrates a practical enterprise scenario where multiple AI agents
 * from different vendors are registered in Apicurio Registry for discovery and orchestration.
 * <p>
 * The scenario simulates an enterprise that uses AI agents for:
 * <ul>
 *   <li><b>Customer Support</b>: Zendesk and Intercom AI agents for ticket management and live chat</li>
 *   <li><b>Finance</b>: Stripe Radar for fraud detection, Plaid for financial insights</li>
 *   <li><b>DevOps</b>: Datadog for observability, PagerDuty for incident management, GitHub for security</li>
 *   <li><b>Orchestration</b>: A meta-agent that discovers and coordinates other agents</li>
 * </ul>
 * <p>
 * Key demonstrations:
 * <ul>
 *   <li>Organizing agents by domain (groups)</li>
 *   <li>Agent discovery using A2A well-known endpoints</li>
 *   <li>Skill-based agent search for workflow composition</li>
 *   <li>Agent version evolution with compatibility rules</li>
 *   <li>Multi-agent orchestration patterns</li>
 * </ul>
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Apicurio Registry running on localhost:8080 (use docker-compose.yml)</li>
 * </ul>
 * <p>
 * Run with: mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.a2a.enterprise.EnterpriseAgentMarketplace
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol Specification</a>
 */
public class EnterpriseAgentMarketplace {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnterpriseAgentMarketplace.class);

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String REGISTRY_BASE_URL = "http://localhost:8080";
    private static final String ARTIFACT_TYPE = "AGENT_CARD";
    private static final String CONTENT_TYPE = "application/json";

    // Domain-based group organization
    private static final String GROUP_CUSTOMER_SUPPORT = "enterprise.agents.customer-support";
    private static final String GROUP_FINANCE = "enterprise.agents.finance";
    private static final String GROUP_DEVOPS = "enterprise.agents.devops";
    private static final String GROUP_ORCHESTRATION = "enterprise.agents.orchestration";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegistryClient client;
    private final HttpClient httpClient;

    public EnterpriseAgentMarketplace(RegistryClient client) {
        this.client = client;
        this.httpClient = HttpClient.newHttpClient();
    }

    public static void main(String[] args) {
        LOGGER.info("=".repeat(80));
        LOGGER.info("Enterprise AI Agent Marketplace - Real-World A2A Example");
        LOGGER.info("=".repeat(80));
        LOGGER.info("");
        LOGGER.info("This example demonstrates a production-like enterprise scenario with");
        LOGGER.info("AI agents from Zendesk, Intercom, Stripe, Plaid, Datadog, PagerDuty, and GitHub.");
        LOGGER.info("");

        RegistryClient client = createClient();
        EnterpriseAgentMarketplace marketplace = new EnterpriseAgentMarketplace(client);

        try {
            // Phase 1: Register all enterprise agents organized by domain
            marketplace.registerEnterpriseAgents();

            // Phase 2: Demonstrate A2A discovery endpoints
            marketplace.demonstrateA2ADiscovery();

            // Phase 3: Demonstrate skill-based agent search for orchestration
            marketplace.demonstrateSkillBasedSearch();

            // Phase 4: Demonstrate agent evolution with compatibility rules
            marketplace.demonstrateAgentEvolution();

            // Phase 5: Demonstrate multi-agent orchestration scenario
            marketplace.demonstrateOrchestrationScenario();

            LOGGER.info("");
            LOGGER.info("=".repeat(80));
            LOGGER.info("All demonstrations completed successfully!");
            LOGGER.info("=".repeat(80));
            LOGGER.info("");
            LOGGER.info("Explore the registered agents:");
            LOGGER.info("  - UI: http://localhost:8888");
            LOGGER.info("  - A2A Discovery: http://localhost:8080/.well-known/agents");
            LOGGER.info("  - Registry Agent Card: http://localhost:8080/.well-known/agent.json");

        } catch (Exception e) {
            LOGGER.error("Example failed: {}", e.getMessage(), e);
        } finally {
            DefaultVertxInstance.close();
        }
    }

    /**
     * Phase 1: Register enterprise agents organized by business domain.
     * <p>
     * Uses groups to organize agents:
     * - Customer Support agents in enterprise.agents.customer-support
     * - Finance agents in enterprise.agents.finance
     * - DevOps agents in enterprise.agents.devops
     * - Orchestration agents in enterprise.agents.orchestration
     */
    public void registerEnterpriseAgents() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(80));
        LOGGER.info("Phase 1: Registering Enterprise AI Agents by Domain");
        LOGGER.info("-".repeat(80));

        // Customer Support Domain
        LOGGER.info("");
        LOGGER.info("[Customer Support Domain]");
        registerAgent(GROUP_CUSTOMER_SUPPORT, "zendesk-support-agent",
                "/agent-cards/customer-support/zendesk-ai-agent.json", "1.0.0");
        registerAgent(GROUP_CUSTOMER_SUPPORT, "intercom-conversation-agent",
                "/agent-cards/customer-support/intercom-conversation-agent.json", "1.0.0");

        // Finance Domain
        LOGGER.info("");
        LOGGER.info("[Finance Domain]");
        registerAgent(GROUP_FINANCE, "stripe-radar-agent",
                "/agent-cards/finance/stripe-fraud-detection-agent.json", "1.0.0");
        registerAgent(GROUP_FINANCE, "plaid-insights-agent",
                "/agent-cards/finance/plaid-financial-insights-agent.json", "1.0.0");

        // DevOps Domain
        LOGGER.info("");
        LOGGER.info("[DevOps Domain]");
        registerAgent(GROUP_DEVOPS, "datadog-observability-agent",
                "/agent-cards/devops/datadog-observability-agent.json", "1.0.0");
        registerAgent(GROUP_DEVOPS, "pagerduty-incident-agent",
                "/agent-cards/devops/pagerduty-incident-agent.json", "1.0.0");
        registerAgent(GROUP_DEVOPS, "github-security-agent",
                "/agent-cards/devops/github-copilot-security-agent.json", "1.0.0");

        // Orchestration Domain
        LOGGER.info("");
        LOGGER.info("[Orchestration Domain]");
        registerAgent(GROUP_ORCHESTRATION, "enterprise-orchestrator",
                "/agent-cards/orchestrator-agent.json", "1.0.0");

        LOGGER.info("");
        LOGGER.info("Successfully registered 8 enterprise agents across 4 domains");
    }

    /**
     * Phase 2: Demonstrate A2A protocol discovery endpoints.
     * <p>
     * The A2A protocol defines standard endpoints for agent discovery:
     * - /.well-known/agent.json - The registry's own agent card
     * - /.well-known/agents - Search for registered agents
     * - /.well-known/agents/{groupId}/{artifactId} - Get specific agent
     */
    public void demonstrateA2ADiscovery() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(80));
        LOGGER.info("Phase 2: A2A Protocol Discovery Endpoints");
        LOGGER.info("-".repeat(80));

        // 1. Get the Registry's own Agent Card
        LOGGER.info("");
        LOGGER.info("[Registry Self-Description]");
        LOGGER.info("GET /.well-known/agent.json - Registry exposes itself as an A2A agent");

        String registryAgentCard = fetchWellKnownEndpoint("/.well-known/agent.json");
        if (registryAgentCard != null) {
            JsonNode card = MAPPER.readTree(registryAgentCard);
            LOGGER.info("  Registry Agent: {}", card.path("name").asText());
            LOGGER.info("  Version: {}", card.path("version").asText());
            LOGGER.info("  Skills:");
            card.path("skills").forEach(skill ->
                    LOGGER.info("    - {} ({})", skill.path("name").asText(), skill.path("id").asText())
            );
        }

        // 2. Discover all registered agents
        LOGGER.info("");
        LOGGER.info("[Agent Discovery]");
        LOGGER.info("GET /.well-known/agents - Search all registered agents");

        String agentsResponse = fetchWellKnownEndpoint("/.well-known/agents?limit=10");
        if (agentsResponse != null) {
            JsonNode results = MAPPER.readTree(agentsResponse);
            LOGGER.info("  Found {} registered agents", results.path("count").asInt());
            results.path("agents").forEach(agent -> {
                LOGGER.info("    - {} v{} ({})",
                        agent.path("name").asText(),
                        agent.path("version").asText(),
                        agent.path("groupId").asText());
            });
        }

        // 3. Get a specific agent via well-known endpoint
        LOGGER.info("");
        LOGGER.info("[Specific Agent Retrieval]");
        LOGGER.info("GET /.well-known/agents/{groupId}/{artifactId}");

        String specificAgent = fetchWellKnownEndpoint(
                "/.well-known/agents/" + GROUP_FINANCE + "/stripe-radar-agent");
        if (specificAgent != null) {
            JsonNode agent = MAPPER.readTree(specificAgent);
            LOGGER.info("  Retrieved: {} v{}", agent.path("name").asText(), agent.path("version").asText());
            LOGGER.info("  Provider: {}", agent.path("provider").path("organization").asText());
            LOGGER.info("  Capabilities: streaming={}, pushNotifications={}",
                    agent.path("capabilities").path("streaming").asBoolean(),
                    agent.path("capabilities").path("pushNotifications").asBoolean());
        }

        LOGGER.info("");
        LOGGER.info("A2A discovery endpoints enable standardized agent discovery across platforms");
    }

    /**
     * Phase 3: Demonstrate skill-based agent search for workflow composition.
     * <p>
     * Real-world orchestrators need to find agents by capability:
     * - "Find agents that can detect fraud"
     * - "Which agents handle incident response?"
     * - "Find streaming-capable agents for real-time use cases"
     */
    public void demonstrateSkillBasedSearch() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(80));
        LOGGER.info("Phase 3: Skill-Based Agent Search for Orchestration");
        LOGGER.info("-".repeat(80));

        // Search by skill: Find agents with fraud-related capabilities
        LOGGER.info("");
        LOGGER.info("[Use Case: Find agents for fraud investigation workflow]");

        // Use the registry API to search by labels
        ArtifactSearchResults allAgents = client.search().artifacts().get(config -> {
            config.queryParameters.artifactType = ARTIFACT_TYPE;
        });

        // Analyze agents for fraud-related skills
        List<AgentSkillMatch> fraudAgents = new ArrayList<>();
        for (SearchedArtifact artifact : allAgents.getArtifacts()) {
            InputStream content = client.groups().byGroupId(artifact.getGroupId())
                    .artifacts().byArtifactId(artifact.getArtifactId())
                    .versions().byVersionExpression("branch=latest").content().get();
            String json = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode card = MAPPER.readTree(json);

            for (JsonNode skill : card.path("skills")) {
                String skillId = skill.path("id").asText();
                String skillName = skill.path("name").asText();
                String description = skill.path("description").asText().toLowerCase();

                if (skillId.contains("fraud") || description.contains("fraud") ||
                        skillId.contains("risk") || description.contains("risk-scoring")) {
                    fraudAgents.add(new AgentSkillMatch(
                            card.path("name").asText(),
                            artifact.getGroupId(),
                            skillId,
                            skillName
                    ));
                }
            }
        }

        LOGGER.info("  Found {} agents with fraud/risk-related skills:", fraudAgents.size());
        for (AgentSkillMatch match : fraudAgents) {
            LOGGER.info("    - {} ({}) -> skill: {}",
                    match.agentName, match.groupId, match.skillName);
        }

        // Search by capability: Find streaming-capable agents
        LOGGER.info("");
        LOGGER.info("[Use Case: Find real-time capable agents (streaming)]");

        List<String> streamingAgents = new ArrayList<>();
        for (SearchedArtifact artifact : allAgents.getArtifacts()) {
            InputStream content = client.groups().byGroupId(artifact.getGroupId())
                    .artifacts().byArtifactId(artifact.getArtifactId())
                    .versions().byVersionExpression("branch=latest").content().get();
            String json = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode card = MAPPER.readTree(json);

            if (card.path("capabilities").path("streaming").asBoolean(false)) {
                streamingAgents.add(card.path("name").asText());
            }
        }

        LOGGER.info("  Streaming-capable agents: {}", streamingAgents);

        // Search by domain: DevOps agents for incident handling
        LOGGER.info("");
        LOGGER.info("[Use Case: Find DevOps agents for incident workflow]");

        ArtifactSearchResults devopsAgents = client.groups().byGroupId(GROUP_DEVOPS).artifacts().get();
        LOGGER.info("  DevOps domain has {} agents:", devopsAgents.getCount());
        for (SearchedArtifact artifact : devopsAgents.getArtifacts()) {
            InputStream content = client.groups().byGroupId(artifact.getGroupId())
                    .artifacts().byArtifactId(artifact.getArtifactId())
                    .versions().byVersionExpression("branch=latest").content().get();
            String json = new String(content.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode card = MAPPER.readTree(json);

            List<String> skills = new ArrayList<>();
            card.path("skills").forEach(s -> skills.add(s.path("id").asText()));
            LOGGER.info("    - {} (skills: {})", card.path("name").asText(), skills);
        }

        LOGGER.info("");
        LOGGER.info("Skill-based search enables intelligent agent selection for workflow composition");
    }

    /**
     * Phase 4: Demonstrate safe agent evolution with compatibility rules.
     * <p>
     * When agents evolve (new skills, capabilities), we need to ensure
     * backward compatibility so existing orchestrations don't break.
     */
    public void demonstrateAgentEvolution() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(80));
        LOGGER.info("Phase 4: Agent Evolution with Compatibility Governance");
        LOGGER.info("-".repeat(80));

        String artifactId = "datadog-observability-agent";

        // Enable backward compatibility rule
        LOGGER.info("");
        LOGGER.info("[Enabling Compatibility Rule]");
        LOGGER.info("Setting BACKWARD compatibility for Datadog agent to ensure safe evolution");

        try {
            CreateRule createRule = new CreateRule();
            createRule.setRuleType(RuleType.COMPATIBILITY);
            createRule.setConfig("BACKWARD");
            client.groups().byGroupId(GROUP_DEVOPS).artifacts()
                    .byArtifactId(artifactId).rules().post(createRule);
            LOGGER.info("  Compatibility rule enabled successfully");
        } catch (Exception e) {
            LOGGER.info("  Rule already exists or agent not found: {}", e.getMessage());
        }

        // Evolve agent with new skills (backward compatible change)
        LOGGER.info("");
        LOGGER.info("[Evolving Agent: Adding New Skills]");
        LOGGER.info("Upgrading Datadog agent v3.5.0 -> v4.0.0 with new AIOps skills");

        String v2Content = loadResource("/agent-cards/devops/datadog-observability-agent-v2.json");
        JsonNode v2Card = MAPPER.readTree(v2Content);

        LOGGER.info("  New version: {}", v2Card.path("version").asText());
        LOGGER.info("  New skills being added:");
        v2Card.path("skills").forEach(skill -> {
            String name = skill.path("name").asText();
            String desc = skill.path("description").asText();
            if (desc.startsWith("NEW:")) {
                LOGGER.info("    + {} - {}", skill.path("id").asText(), name);
            }
        });

        try {
            CreateVersion createVersion = new CreateVersion();
            createVersion.setVersion("4.0.0");
            createVersion.setContent(new VersionContent());
            createVersion.getContent().setContent(v2Content);
            createVersion.getContent().setContentType(CONTENT_TYPE);

            VersionMetaData v2 = client.groups().byGroupId(GROUP_DEVOPS)
                    .artifacts().byArtifactId(artifactId).versions().post(createVersion);

            LOGGER.info("  Successfully created version: {}", v2.getVersion());
        } catch (Exception e) {
            LOGGER.info("  Version creation: {}", e.getMessage());
        }

        // List all versions
        LOGGER.info("");
        LOGGER.info("[Version History]");
        VersionSearchResults versions = client.groups().byGroupId(GROUP_DEVOPS)
                .artifacts().byArtifactId(artifactId).versions().get();

        LOGGER.info("  Datadog agent versions:");
        versions.getVersions().forEach(v ->
                LOGGER.info("    - v{} (created: {})", v.getVersion(), v.getCreatedOn()));

        LOGGER.info("");
        LOGGER.info("Compatibility Rules Summary:");
        LOGGER.info("  [SAFE] Adding new skills - existing workflows continue to work");
        LOGGER.info("  [SAFE] Adding new capabilities - enhances functionality");
        LOGGER.info("  [BREAKING] Removing skills - would break dependent orchestrations");
        LOGGER.info("  [BREAKING] Changing authentication - would break integrations");
    }

    /**
     * Phase 5: Demonstrate a realistic multi-agent orchestration scenario.
     * <p>
     * Scenario: A suspicious transaction triggers a complex investigation workflow
     * that spans multiple domains (Finance, Support, DevOps).
     */
    public void demonstrateOrchestrationScenario() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(80));
        LOGGER.info("Phase 5: Multi-Agent Orchestration Scenario");
        LOGGER.info("-".repeat(80));

        LOGGER.info("");
        LOGGER.info("SCENARIO: Suspicious Transaction Investigation");
        LOGGER.info("A high-value transaction from a new device triggers automated investigation");
        LOGGER.info("");

        // Step 1: Orchestrator discovers available agents
        LOGGER.info("[Step 1] Orchestrator queries A2A registry for capable agents");
        Map<String, List<String>> workflowAgents = new HashMap<>();

        // Finance agents for fraud analysis
        workflowAgents.put("Fraud Analysis", List.of(
                "Stripe Radar AI Agent -> transaction-risk-scoring",
                "Plaid Financial Insights Agent -> spending-analysis"
        ));

        // Support agents for customer context
        workflowAgents.put("Customer Context", List.of(
                "Zendesk AI Support Agent -> sentiment-analysis",
                "Intercom Conversation AI -> intent-recognition"
        ));

        // DevOps agents for system correlation
        workflowAgents.put("System Correlation", List.of(
                "Datadog AI Observability Agent -> anomaly-detection",
                "GitHub Advanced Security Agent -> secret-detection"
        ));

        for (Map.Entry<String, List<String>> phase : workflowAgents.entrySet()) {
            LOGGER.info("  {} phase:", phase.getKey());
            phase.getValue().forEach(agent -> LOGGER.info("    - {}", agent));
        }

        // Step 2: Execute workflow
        LOGGER.info("");
        LOGGER.info("[Step 2] Orchestrator executes investigation workflow");
        LOGGER.info("");
        LOGGER.info("  1. Stripe Radar: Risk score = 0.87 (HIGH)");
        LOGGER.info("     - New device fingerprint detected");
        LOGGER.info("     - Transaction amount 3x higher than usual");
        LOGGER.info("     - IP geolocation mismatch with account history");
        LOGGER.info("");
        LOGGER.info("  2. Plaid Insights: Spending pattern analysis");
        LOGGER.info("     - No similar transactions in 12-month history");
        LOGGER.info("     - First transaction to this merchant category");
        LOGGER.info("     - Account cash flow supports transaction");
        LOGGER.info("");
        LOGGER.info("  3. Zendesk Support: Customer context check");
        LOGGER.info("     - 2 recent support tickets about account access");
        LOGGER.info("     - Sentiment: FRUSTRATED in last interaction");
        LOGGER.info("     - No travel notification on file");
        LOGGER.info("");
        LOGGER.info("  4. Datadog: System correlation");
        LOGGER.info("     - No anomalies in authentication service");
        LOGGER.info("     - Login from new IP 2 hours before transaction");

        // Step 3: Aggregated decision
        LOGGER.info("");
        LOGGER.info("[Step 3] Orchestrator aggregates findings and decides");
        LOGGER.info("");
        LOGGER.info("  DECISION: HOLD TRANSACTION FOR REVIEW");
        LOGGER.info("  Confidence: 94%");
        LOGGER.info("  Rationale:");
        LOGGER.info("    - High risk score from fraud detection");
        LOGGER.info("    - Unusual spending pattern");
        LOGGER.info("    - Recent account access concerns in support history");
        LOGGER.info("    - New device + new IP combination");
        LOGGER.info("");
        LOGGER.info("  ACTIONS TAKEN:");
        LOGGER.info("    - Transaction held pending verification");
        LOGGER.info("    - SMS verification sent to customer");
        LOGGER.info("    - Support ticket auto-created for follow-up");
        LOGGER.info("    - Incident logged in PagerDuty for fraud team");

        LOGGER.info("");
        LOGGER.info("This scenario demonstrates how A2A enables complex multi-agent");
        LOGGER.info("orchestration across domains using standardized discovery and skills.");
    }

    // Helper methods

    private void registerAgent(String groupId, String artifactId, String resourcePath, String version) throws Exception {
        String content = loadResource(resourcePath);
        JsonNode card = MAPPER.readTree(content);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ARTIFACT_TYPE);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setVersion(version);
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContent(content);
        createArtifact.getFirstVersion().getContent().setContentType(CONTENT_TYPE);

        try {
            client.groups().byGroupId(groupId).artifacts()
                    .post(createArtifact, config -> {
                        config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    });

            LOGGER.info("  Registered: {} v{} ({})",
                    card.path("name").asText(),
                    card.path("version").asText(),
                    card.path("provider").path("organization").asText());
        } catch (Exception e) {
            LOGGER.info("  Skipped (exists): {}", card.path("name").asText());
        }
    }

    private String fetchWellKnownEndpoint(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REGISTRY_BASE_URL + path))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                LOGGER.warn("  Failed to fetch {}: HTTP {}", path, response.statusCode());
                return null;
            }
        } catch (Exception e) {
            LOGGER.warn("  Error fetching {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static RegistryClient createClient() {
        String registryUrl = System.getenv().getOrDefault("REGISTRY_URL", REGISTRY_URL);
        LOGGER.info("Connecting to Apicurio Registry at: {}", registryUrl);

        String tokenEndpoint = System.getenv("AUTH_TOKEN_ENDPOINT");
        if (tokenEndpoint != null) {
            String authClient = System.getenv("AUTH_CLIENT_ID");
            String authSecret = System.getenv("AUTH_CLIENT_SECRET");
            return RegistryClientFactory.create(RegistryClientOptions.create(registryUrl)
                    .oauth2(tokenEndpoint, authClient, authSecret));
        }

        return RegistryClientFactory.create(RegistryClientOptions.create(registryUrl));
    }

    private String loadResource(String path) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Helper class to represent a skill match during search
     */
    private static class AgentSkillMatch {
        final String agentName;
        final String groupId;
        final String skillId;
        final String skillName;

        AgentSkillMatch(String agentName, String groupId, String skillId, String skillName) {
            this.agentName = agentName;
            this.groupId = groupId;
            this.skillId = skillId;
            this.skillName = skillName;
        }
    }
}
