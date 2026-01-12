package io.apicurio.registry.examples.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Example demonstrating A2A (Agent-to-Agent) Agent Card usage with Apicurio Registry.
 * <p>
 * The A2A protocol enables AI agents to discover and communicate with each other.
 * Agent Cards are JSON metadata documents describing an AI agent's capabilities,
 * skills, authentication requirements, and endpoint URLs.
 * <p>
 * This example demonstrates:
 * <ul>
 *   <li>Registering Agent Cards in Apicurio Registry</li>
 *   <li>Retrieving and inspecting Agent Card content</li>
 *   <li>Creating new versions of Agent Cards</li>
 *   <li>Setting up compatibility rules for safe evolution</li>
 *   <li>Searching for agents by type</li>
 *   <li>Validation of Agent Card structure</li>
 * </ul>
 * <p>
 * Prerequisites:
 * <ul>
 *   <li>Apicurio Registry running on localhost:8080 (use docker-compose.yml)</li>
 * </ul>
 * <p>
 * Run with: mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.a2a.AgentCardExample
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol Specification</a>
 * @see <a href="https://github.com/google/A2A">Google A2A GitHub</a>
 */
public class AgentCardExample {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentCardExample.class);

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String GROUP_ID = "a2a-agents";
    private static final String ARTIFACT_TYPE = "AGENT_CARD";
    private static final String CONTENT_TYPE = "application/json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RegistryClient client;

    public AgentCardExample(RegistryClient client) {
        this.client = client;
    }

    public static void main(String[] args) {
        LOGGER.info("=".repeat(70));
        LOGGER.info("A2A Agent Card Example - Apicurio Registry");
        LOGGER.info("=".repeat(70));

        RegistryClient client = createClient();
        AgentCardExample example = new AgentCardExample(client);

        try {
            // Run all demonstrations
            example.demonstrateAgentCardCRUD();
            example.demonstrateCompatibilityRules();
            example.demonstrateAgentSearch();
            example.demonstrateMinimalAgentCard();

            LOGGER.info("=".repeat(70));
            LOGGER.info("All demonstrations completed successfully!");
            LOGGER.info("=".repeat(70));

        } catch (Exception e) {
            LOGGER.error("Example failed: {}", e.getMessage(), e);
        } finally {
            DefaultVertxInstance.close();
        }
    }

    /**
     * Demonstrates basic CRUD operations with Agent Cards.
     */
    public void demonstrateAgentCardCRUD() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(70));
        LOGGER.info("Demo 1: Agent Card CRUD Operations");
        LOGGER.info("-".repeat(70));

        String artifactId = "code-assistant-agent-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Load Agent Card from resource file
        String agentCardContent = loadResource("/agent-cards/code-assistant.json");
        LOGGER.info("Loaded Agent Card for: Code Assistant Agent");
        prettyPrintAgentCard(agentCardContent);

        // 2. Register the Agent Card
        LOGGER.info("Registering Agent Card in Apicurio Registry...");
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ARTIFACT_TYPE);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setVersion("1.0.0");
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContent(agentCardContent);
        createArtifact.getFirstVersion().getContent().setContentType(CONTENT_TYPE);

        VersionMetaData vmd = client.groups().byGroupId(GROUP_ID).artifacts()
                .post(createArtifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FAIL;
                }).getVersion();

        LOGGER.info("Successfully registered Agent Card:");
        LOGGER.info("  - Group ID: {}", GROUP_ID);
        LOGGER.info("  - Artifact ID: {}", artifactId);
        LOGGER.info("  - Version: {}", vmd.getVersion());
        LOGGER.info("  - Global ID: {}", vmd.getGlobalId());

        // 3. Retrieve the Agent Card
        LOGGER.info("Retrieving Agent Card from registry...");
        ArtifactMetaData metadata = client.groups().byGroupId(GROUP_ID)
                .artifacts().byArtifactId(artifactId).get();

        LOGGER.info("Retrieved artifact metadata:");
        LOGGER.info("  - Name: {}", metadata.getName());
        LOGGER.info("  - Artifact Type: {}", metadata.getArtifactType());
        LOGGER.info("  - Created: {}", metadata.getCreatedOn());

        // 4. Create a new version with enhanced capabilities
        LOGGER.info("Creating new version with enhanced capabilities...");
        String updatedContent = loadResource("/agent-cards/code-assistant-v2.json");

        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion("2.0.0");
        createVersion.setContent(new VersionContent());
        createVersion.getContent().setContent(updatedContent);
        createVersion.getContent().setContentType(CONTENT_TYPE);

        VersionMetaData v2 = client.groups().byGroupId(GROUP_ID)
                .artifacts().byArtifactId(artifactId).versions().post(createVersion);

        LOGGER.info("Created new version: {}", v2.getVersion());

        // 5. List all versions
        LOGGER.info("Listing all versions:");
        VersionSearchResults versions = client.groups().byGroupId(GROUP_ID)
                .artifacts().byArtifactId(artifactId).versions().get();

        versions.getVersions().forEach(v -> {
            LOGGER.info("  - Version: {} (Global ID: {})", v.getVersion(), v.getGlobalId());
        });

        LOGGER.info("Demo 1 completed successfully!");
    }

    /**
     * Demonstrates compatibility rules for Agent Card evolution.
     * <p>
     * A2A Agent Cards should evolve carefully to maintain backward compatibility.
     * Adding new skills is safe, but removing skills could break dependent agents.
     */
    public void demonstrateCompatibilityRules() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(70));
        LOGGER.info("Demo 2: Compatibility Rules for Agent Card Evolution");
        LOGGER.info("-".repeat(70));

        String artifactId = "data-analyst-agent-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. Create initial Agent Card
        String v1Content = loadResource("/agent-cards/data-analyst-v1.json");
        LOGGER.info("Creating Data Analyst Agent v1...");

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ARTIFACT_TYPE);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContent(v1Content);
        createArtifact.getFirstVersion().getContent().setContentType(CONTENT_TYPE);

        client.groups().byGroupId(GROUP_ID).artifacts()
                .post(createArtifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FAIL;
                });

        // 2. Enable BACKWARD compatibility rule
        LOGGER.info("Enabling BACKWARD compatibility rule...");
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");

        client.groups().byGroupId(GROUP_ID).artifacts()
                .byArtifactId(artifactId).rules().post(createRule);

        LOGGER.info("Compatibility rule enabled. Now testing compatible changes...");

        // 3. Add a new skill (backward compatible change)
        String v2Content = loadResource("/agent-cards/data-analyst-v2.json");
        LOGGER.info("Updating to v2 with added 'predictive-analytics' skill...");

        CreateVersion createVersion = new CreateVersion();
        createVersion.setContent(new VersionContent());
        createVersion.getContent().setContent(v2Content);
        createVersion.getContent().setContentType(CONTENT_TYPE);

        VersionMetaData v2 = client.groups().byGroupId(GROUP_ID)
                .artifacts().byArtifactId(artifactId).versions().post(createVersion);

        LOGGER.info("Successfully created v2: {} - Adding skills is backward compatible!", v2.getVersion());

        LOGGER.info("");
        LOGGER.info("Compatibility Rules Summary:");
        LOGGER.info("  [OK] Adding new skills - backward compatible");
        LOGGER.info("  [OK] Adding new capabilities - backward compatible");
        LOGGER.info("  [OK] Adding new input/output modes - backward compatible");
        LOGGER.info("  [!!] Removing skills - breaks backward compatibility");
        LOGGER.info("  [!!] Changing agent URL - breaks backward compatibility");
        LOGGER.info("  [!!] Removing authentication schemes - breaks backward compatibility");

        LOGGER.info("Demo 2 completed successfully!");
    }

    /**
     * Demonstrates searching for agents by type.
     */
    public void demonstrateAgentSearch() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(70));
        LOGGER.info("Demo 3: Searching for Agent Cards");
        LOGGER.info("-".repeat(70));

        // Register a few different agents
        String[] agentTypes = {"translator-agent", "summarizer-agent", "classifier-agent"};

        for (String agentType : agentTypes) {
            String artifactId = agentType + "-" + UUID.randomUUID().toString().substring(0, 8);
            ObjectNode agentCard = createSimpleAgentCard(agentType);

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId(artifactId);
            createArtifact.setArtifactType(ARTIFACT_TYPE);
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(MAPPER.writeValueAsString(agentCard));
            createArtifact.getFirstVersion().getContent().setContentType(CONTENT_TYPE);

            client.groups().byGroupId(GROUP_ID).artifacts()
                    .post(createArtifact, config -> {
                        config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    });

            LOGGER.info("Registered: {}", agentType);
        }

        // Search for all Agent Cards in the group
        LOGGER.info("");
        LOGGER.info("Searching for all AGENT_CARD artifacts in group '{}'...", GROUP_ID);

        ArtifactSearchResults results = client.groups().byGroupId(GROUP_ID).artifacts().get(config -> {
            // Filter by artifact type is not directly available, so we list all and filter
        });

        LOGGER.info("Found {} artifacts in group", results.getCount());

        results.getArtifacts().forEach(artifact -> {
            if (ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
                LOGGER.info("  Agent: {} ({})", artifact.getArtifactId(), artifact.getArtifactType());
            }
        });

        LOGGER.info("Demo 3 completed successfully!");
    }

    /**
     * Demonstrates creating a minimal Agent Card (only required fields).
     */
    public void demonstrateMinimalAgentCard() throws Exception {
        LOGGER.info("");
        LOGGER.info("-".repeat(70));
        LOGGER.info("Demo 4: Minimal Agent Card");
        LOGGER.info("-".repeat(70));

        String artifactId = "minimal-agent-" + UUID.randomUUID().toString().substring(0, 8);

        // A2A protocol only requires the 'name' field
        String minimalAgentCard = """
            {
                "name": "Minimal Example Agent"
            }
            """;

        LOGGER.info("Creating minimal Agent Card (only 'name' field)...");

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ARTIFACT_TYPE);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent().setContent(minimalAgentCard);
        createArtifact.getFirstVersion().getContent().setContentType(CONTENT_TYPE);

        VersionMetaData vmd = client.groups().byGroupId(GROUP_ID).artifacts()
                .post(createArtifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FAIL;
                }).getVersion();

        LOGGER.info("Successfully registered minimal Agent Card!");
        LOGGER.info("  - Artifact ID: {}", artifactId);
        LOGGER.info("  - Version: {}", vmd.getVersion());
        LOGGER.info("");
        LOGGER.info("The A2A protocol only requires the 'name' field.");
        LOGGER.info("All other fields (description, skills, capabilities, etc.) are optional.");

        LOGGER.info("Demo 4 completed successfully!");
    }

    // Helper methods

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

    private void prettyPrintAgentCard(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        LOGGER.info("Agent Card:");
        LOGGER.info("  Name: {}", node.path("name").asText());
        LOGGER.info("  Description: {}", node.path("description").asText());
        LOGGER.info("  Version: {}", node.path("version").asText());
        LOGGER.info("  URL: {}", node.path("url").asText());

        JsonNode skills = node.path("skills");
        if (skills.isArray()) {
            LOGGER.info("  Skills:");
            for (JsonNode skill : skills) {
                LOGGER.info("    - {} ({})", skill.path("name").asText(), skill.path("id").asText());
            }
        }

        JsonNode capabilities = node.path("capabilities");
        if (!capabilities.isMissingNode()) {
            LOGGER.info("  Capabilities:");
            LOGGER.info("    - Streaming: {}", capabilities.path("streaming").asBoolean(false));
            LOGGER.info("    - Push Notifications: {}", capabilities.path("pushNotifications").asBoolean(false));
        }
    }

    private ObjectNode createSimpleAgentCard(String agentType) {
        String name = agentType.replace("-", " ");
        name = name.substring(0, 1).toUpperCase() + name.substring(1);

        ObjectNode card = MAPPER.createObjectNode();
        card.put("name", name);
        card.put("description", "An AI agent for " + agentType.replace("-agent", "") + " tasks");
        card.put("version", "1.0.0");
        card.put("url", "https://example.com/" + agentType);

        ObjectNode capabilities = MAPPER.createObjectNode();
        capabilities.put("streaming", false);
        capabilities.put("pushNotifications", false);
        card.set("capabilities", capabilities);

        ArrayNode skills = MAPPER.createArrayNode();
        ObjectNode skill = MAPPER.createObjectNode();
        skill.put("id", agentType.replace("-agent", ""));
        skill.put("name", name.replace(" Agent", ""));
        skill.put("description", "Primary " + agentType.replace("-agent", "") + " capability");
        skills.add(skill);
        card.set("skills", skills);

        ArrayNode inputModes = MAPPER.createArrayNode();
        inputModes.add("text");
        card.set("defaultInputModes", inputModes);

        ArrayNode outputModes = MAPPER.createArrayNode();
        outputModes.add("text");
        card.set("defaultOutputModes", outputModes);

        return card;
    }
}
