package io.apicurio.tests.smokeTests.apicurio;

import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.apicurio.tests.utils.Constants.ACCEPTANCE;
import static io.apicurio.tests.utils.Constants.SMOKE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for A2A Agent Card artifact type. Tests CRUD operations, validation, and compatibility
 * rules.
 */
@Tag(SMOKE)
@QuarkusIntegrationTest
class AgentCardIT extends ApicurioRegistryBaseIT {

    private static final String AGENT_CARD_CONTENT = """
            {
                "name": "TestAgent",
                "description": "A test AI agent",
                "version": "1.0.0",
                "url": "https://example.com/agent",
                "capabilities": {
                    "streaming": true,
                    "pushNotifications": false
                },
                "skills": [
                    {
                        "id": "test-skill",
                        "name": "Test Skill",
                        "description": "A test skill"
                    }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String STREAMING_AGENT_CARD = """
            {
                "name": "StreamingAgent",
                "description": "An agent with streaming capabilities",
                "version": "2.0.0",
                "url": "https://example.com/streaming-agent",
                "capabilities": {
                    "streaming": true,
                    "pushNotifications": true
                },
                "skills": [
                    {
                        "id": "data-processing",
                        "name": "Data Processing"
                    },
                    {
                        "id": "real-time-analysis",
                        "name": "Real-time Analysis"
                    }
                ],
                "defaultInputModes": ["text", "image"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String MINIMAL_AGENT_CARD = """
            {
                "name": "MinimalAgent"
            }
            """;

    // ==================== CRUD TESTS ====================

    @Test
    @Tag(ACCEPTANCE)
    void testAgentCardCRUD() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create agent card
        CreateArtifactResponse response = createArtifact(groupId, artifactId, ArtifactType.AGENT_CARD,
                AGENT_CARD_CONTENT, ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);

        assertNotNull(response);
        assertNotNull(response.getArtifact());
        assertEquals(artifactId, response.getArtifact().getArtifactId());
        assertEquals(ArtifactType.AGENT_CARD, response.getArtifact().getArtifactType());

        // Read artifact
        retryOp((rc) -> {
            var artifact = rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
            assertNotNull(artifact);
            assertEquals(ArtifactType.AGENT_CARD, artifact.getArtifactType());
        });

        // Update (create new version)
        VersionMetaData vmd = createArtifactVersion(groupId, artifactId, STREAMING_AGENT_CARD,
                ContentTypes.APPLICATION_JSON, null);
        assertNotNull(vmd);
        assertEquals("2", vmd.getVersion());

        // Verify versions
        retryOp((rc) -> {
            List<String> versions = listArtifactVersions(rc, groupId, artifactId);
            assertEquals(2, versions.size());
        });
    }

    @Test
    @Tag(ACCEPTANCE)
    void testAgentCardMinimalStructure() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Create agent card with minimal structure (only name)
        CreateArtifactResponse response = createArtifact(groupId, artifactId, ArtifactType.AGENT_CARD,
                MINIMAL_AGENT_CARD, ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);

        assertNotNull(response);
        assertEquals(artifactId, response.getArtifact().getArtifactId());
    }

    @Test
    void testAgentCardFullStructure() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        String v1Content = resourceToString("artifactTypes/agentcard/agent_v1.json");
        String v2Content = resourceToString("artifactTypes/agentcard/agent_v2.json");

        // Create with full structure
        createArtifact(groupId, artifactId, ArtifactType.AGENT_CARD, v1Content,
                ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);

        // Update to v2
        VersionMetaData vmd = createArtifactVersion(groupId, artifactId, v2Content,
                ContentTypes.APPLICATION_JSON, null);
        assertNotNull(vmd);
    }

    // ==================== COMPATIBILITY TESTS ====================

    @Test
    void testAgentCardCompatibility_AddSkill() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Enable backward compatibility rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");

        // Create initial agent card
        createArtifact(groupId, artifactId, ArtifactType.AGENT_CARD, AGENT_CARD_CONTENT,
                ContentTypes.APPLICATION_JSON, IfArtifactExists.FAIL, null);

        // Set compatibility rule on artifact
        registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .post(createRule);

        // Wait for rule to be applied
        retryOp((rc) -> rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .byRuleType(createRule.getRuleType().name()).get());

        // Add a new skill (backward compatible change) - should succeed
        String agentWithNewSkill = """
                {
                    "name": "TestAgent",
                    "description": "Agent with new skill",
                    "version": "2.0.0",
                    "url": "https://example.com/agent",
                    "capabilities": {
                        "streaming": true,
                        "pushNotifications": false
                    },
                    "skills": [
                        {
                            "id": "test-skill",
                            "name": "Test Skill",
                            "description": "A test skill"
                        },
                        {
                            "id": "new-skill",
                            "name": "New Skill",
                            "description": "A newly added skill"
                        }
                    ],
                    "defaultInputModes": ["text"],
                    "defaultOutputModes": ["text"]
                }
                """;

        VersionMetaData vmd = createArtifactVersion(groupId, artifactId, agentWithNewSkill,
                ContentTypes.APPLICATION_JSON, null);
        assertNotNull(vmd);
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    void testAgentCardValidation_MissingName() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Enable full validation
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        registryClient.admin().rules().post(createRule);

        // Wait for rule to be applied
        retryOp((rc) -> rc.admin().rules().byRuleType(createRule.getRuleType().name()).get());

        // Try to create an agent card without required 'name' field
        String invalidAgentCard = """
                {
                    "description": "An agent without a name",
                    "version": "1.0.0"
                }
                """;

        retryAssertClientError("RuleViolationException", 409, (rc) -> {
            CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AGENT_CARD,
                    invalidAgentCard, ContentTypes.APPLICATION_JSON);
            rc.groups().byGroupId(groupId).artifacts().post(ca);
        }, errorCodeExtractor);
    }

    @Test
    void testAgentCardValidation_InvalidSkillsType() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = TestUtils.generateArtifactId();

        // Enable full validation
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        registryClient.admin().rules().post(createRule);

        // Wait for rule to be applied
        retryOp((rc) -> rc.admin().rules().byRuleType(createRule.getRuleType().name()).get());

        // Try to create an agent card with invalid skills type
        String invalidAgentCard = """
                {
                    "name": "InvalidAgent",
                    "skills": "not-an-array"
                }
                """;

        retryAssertClientError("RuleViolationException", 409, (rc) -> {
            CreateArtifact ca = TestUtils.clientCreateArtifact(artifactId, ArtifactType.AGENT_CARD,
                    invalidAgentCard, ContentTypes.APPLICATION_JSON);
            rc.groups().byGroupId(groupId).artifacts().post(ca);
        }, errorCodeExtractor);
    }

    @AfterEach
    void deleteRules() throws Exception {
        registryClient.admin().rules().delete();
        retryOp((rc) -> {
            List<RuleType> rules = rc.admin().rules().get();
            assertEquals(0, rules.size(), "All global rules not deleted");
        });
    }
}
