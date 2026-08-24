package io.apicurio.registry.noprofile.rest.aicatalog;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Tests for the AI Catalog (ai-catalog.io) well-known endpoint.
 */
@QuarkusTest
@TestProfile(AiCatalogEnabledProfile.class)
public class WellKnownAiCatalogTest extends AbstractResourceTestBase {

    private String serverRootUrl;

    @BeforeEach
    public void setUpWellKnown() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        serverRootUrl = "http://localhost:" + port;
    }

    private RequestSpecification givenAtRoot() {
        return RestAssured.given().baseUri(serverRootUrl);
    }

    private static final String AGENT_CARD_CONTENT = """
            {
                "name": "CatalogAgent",
                "description": "An agent listed in the AI catalog",
                "version": "1.2.3",
                "supportedInterfaces": [
                    { "url": "https://example.com/agent", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                ],
                "capabilities": {
                    "streaming": true
                },
                "skills": [
                    {
                        "id": "catalog-skill",
                        "name": "Catalog Skill",
                        "description": "A skill used for catalog tests",
                        "tags": ["catalog"]
                    }
                ],
                "defaultInputModes": ["text"],
                "defaultOutputModes": ["text"]
            }
            """;

    private static final String MCP_TOOL_CONTENT = """
            {
                "name": "catalog_lookup",
                "title": "Catalog Lookup",
                "description": "Look up entries in the product catalog",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "query": { "type": "string" }
                    },
                    "required": ["query"]
                }
            }
            """;

    @Test
    public void testGetAiCatalog() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String agentId = "aicat-agent-" + TestUtils.generateArtifactId().replace("-", "");
        String toolId = "aicat-tool-" + TestUtils.generateArtifactId().replace("-", "");

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);
        createMcpTool(groupId, toolId, MCP_TOOL_CONTENT);

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .get("/.well-known/ai-catalog.json")
                .then()
                .statusCode(200)
                .body("specVersion", equalTo("1.0"))
                .body("host.displayName", equalTo("Apicurio Registry"))
                .body("host.identifier", startsWith("localhost:"))
                .body("entries.identifier", hasItem(startsWith("urn:air:localhost:")))
                .body("entries.find { it.url.endsWith('" + groupId + "/" + agentId + "') }.type",
                        equalTo("application/a2a-agent-card+json"))
                .body("entries.find { it.url.endsWith('" + groupId + "/" + toolId + "') }.type",
                        equalTo("application/mcp-server-card+json"));
    }

    @Test
    public void testGetAiCatalogEntryDetails() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String agentId = "aicat-detail-" + TestUtils.generateArtifactId().replace("-", "");

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);

        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .get("/.well-known/ai-catalog.json")
                .then()
                .statusCode(200)
                // displayName comes from the card's own "name" when the artifact has none
                .body("entries.find { it.url.endsWith('" + groupId + "/" + agentId + "') }.displayName",
                        equalTo("CatalogAgent"))
                // version comes from the card's own "version"
                .body("entries.find { it.url.endsWith('" + groupId + "/" + agentId + "') }.version",
                        equalTo("1.2.3"))
                // capabilities are the agent card skill ids
                .body("entries.find { it.url.endsWith('" + groupId + "/" + agentId + "') }.capabilities",
                        hasItem("catalog-skill"))
                // identifier groups the entry by publisher domain, group and artifact id
                .body("entries.find { it.url.endsWith('" + groupId + "/" + agentId + "') }.identifier",
                        startsWith("urn:air:localhost:"))
                .body("entries.find { it.url.endsWith('" + groupId + "/" + agentId + "') }.identifier",
                        containsString(":" + groupId + ":" + agentId));
    }

    @Test
    public void testGetAiCatalogViaV3WellKnown() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String agentId = "aicat-v3-" + TestUtils.generateArtifactId().replace("-", "");

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);

        // The same endpoint must also be exposed under the v3 well-known base URL.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .get("/apis/registry/v3/well-known/ai-catalog.json")
                .then()
                .statusCode(200)
                .body("specVersion", equalTo("1.0"))
                .body("host.displayName", equalTo("Apicurio Registry"))
                .body("entries.url", hasItem(endsWith(groupId + "/" + agentId)));
    }

    @Test
    public void testGetAiCatalogEmptyRegistryHasNoEntries() {
        // With no artifacts matching a fresh random publisher-agnostic state this just
        // verifies the document structure is always well-formed.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .get("/.well-known/ai-catalog.json")
                .then()
                .statusCode(200)
                .body("specVersion", equalTo("1.0"))
                .body("host", notNullValue())
                .body("entries", notNullValue());
    }

    @Test
    public void testGetAiCatalogEntryCountMatchesCreated() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String unique = TestUtils.generateArtifactId().replace("-", "");
        String agentId = "aicat-count-a-" + unique;
        String toolId = "aicat-count-t-" + unique;

        createAgentCard(groupId, agentId, AGENT_CARD_CONTENT);
        createMcpTool(groupId, toolId, MCP_TOOL_CONTENT);

        // Exactly one agent and one tool with these URLs must be present.
        givenAtRoot()
                .when()
                .contentType(ContentType.JSON)
                .get("/.well-known/ai-catalog.json")
                .then()
                .statusCode(200)
                .body("entries.findAll { it.url.endsWith('" + groupId + "/" + agentId + "') }",
                        hasSize(1))
                .body("entries.findAll { it.url.endsWith('" + groupId + "/" + toolId + "') }",
                        hasSize(1));
    }

    private void createAgentCard(String groupId, String artifactId, String content) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.AGENT_CARD);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private void createMcpTool(String groupId, String artifactId, String content) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.MCP_TOOL);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }
}
