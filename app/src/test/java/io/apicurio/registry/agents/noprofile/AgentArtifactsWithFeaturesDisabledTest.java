package io.apicurio.registry.agents.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

/**
 * With every agent feature flag off (the default), agent artifacts are ordinary artifacts: they are
 * created, read, searched and referenced through the core API, and feature-owned labels survive
 * metadata updates. Only the feature endpoints are unavailable. Storage does not depend on the
 * flags, so this is also what a registry sees after the flags are switched off with existing data.
 */
@QuarkusTest
public class AgentArtifactsWithFeaturesDisabledTest extends AbstractResourceTestBase {

    private static final String AGENT_CARD = """
            {"name": "Disabled Features Agent", "description": "An agent", "version": "1.0.0",
             "supportedInterfaces": [{"url": "https://example.com/agent", "protocolBinding": "JSONRPC",
               "protocolVersion": "1.0"}],
             "capabilities": {}, "skills": [], "defaultInputModes": ["text"], "defaultOutputModes": ["text"]}
            """;
    private static final String MCP_TOOL = """
            {"name": "lookup", "inputSchema": {"type": "object", "properties": {"q": {"type": "string"}}}}
            """;
    private static final String MCP_SERVER = """
            {"name": "io.github.test/server", "description": "A server", "version": "1.0.0"}
            """;
    private static final String PROMPT_TEMPLATE = """
            {"templateId": "greeting", "name": "Greeting", "template": "Hello {{name}}"}
            """;
    private static final String MODEL_SCHEMA = """
            {"name": "Model", "input": {"type": "object"}, "output": {"type": "object"}}
            """;
    private static final String FEATURE_LABEL = "apicurio.mcp-registry.version-id";

    private String group;
    private String root;

    @BeforeEach
    void createArtifacts() throws Exception {
        group = TestUtils.generateGroupId();
        root = "http://localhost:" + ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        createArtifact(group, "tool", ArtifactType.MCP_TOOL, MCP_TOOL, ContentTypes.APPLICATION_JSON);
        ArtifactReference toolRef = new ArtifactReference();
        toolRef.setGroupId(group);
        toolRef.setArtifactId("tool");
        toolRef.setVersion("1");
        toolRef.setName("lookup");
        createArtifactWithReferences(group, "agent", ArtifactType.AGENT_CARD, AGENT_CARD,
                ContentTypes.APPLICATION_JSON, List.of(toolRef));
        createArtifact(group, "server", ArtifactType.MCP_SERVER, MCP_SERVER, ContentTypes.APPLICATION_JSON);
        createArtifact(group, "prompt", ArtifactType.PROMPT_TEMPLATE, PROMPT_TEMPLATE,
                ContentTypes.APPLICATION_JSON);
        createArtifact(group, "model", ArtifactType.MODEL_SCHEMA, MODEL_SCHEMA, ContentTypes.APPLICATION_JSON);
    }

    private String artifact(String artifactId) {
        return registryV3ApiUrl + "/groups/" + group + "/artifacts/" + artifactId;
    }

    private RequestSpecification atRoot() {
        return given().baseUri(root).basePath("");
    }

    @Test
    void artifactsAreReadableThroughTheCoreApi() {
        Map<String, String> types = Map.of("agent", ArtifactType.AGENT_CARD, "tool", ArtifactType.MCP_TOOL,
                "server", ArtifactType.MCP_SERVER, "prompt", ArtifactType.PROMPT_TEMPLATE,
                "model", ArtifactType.MODEL_SCHEMA);
        types.forEach((id, type) -> given().get(artifact(id)).then().statusCode(200)
                .body("artifactType", equalTo(type)));
        given().get(artifact("prompt") + "/versions/1/content").then().statusCode(200)
                .body("template", equalTo("Hello {{name}}"));
        given().get(artifact("agent") + "/versions/1/content").then().statusCode(200)
                .body("name", equalTo("Disabled Features Agent"));
    }

    @Test
    void artifactsAreSearchable() {
        given().queryParam("groupId", group).get(registryV3ApiUrl + "/search/artifacts").then().statusCode(200)
                // The model's embedded input and output schemas are registered as artifacts too
                .body("count", equalTo(7))
                .body("artifacts.artifactId", containsInAnyOrder("agent", "tool", "server", "prompt", "model",
                        "model-input-schema", "model-output-schema"));
        given().queryParam("groupId", group).queryParam("artifactType", ArtifactType.AGENT_CARD)
                .get(registryV3ApiUrl + "/search/artifacts").then().statusCode(200)
                .body("artifacts.artifactId", containsInAnyOrder("agent"));
    }

    @Test
    void referencesAreKept() {
        given().get(artifact("agent") + "/versions/1/references").then().statusCode(200)
                .body("artifactId", containsInAnyOrder("tool"))
                .body("name", containsInAnyOrder("lookup"));
        given().queryParam("refType", "INBOUND").get(artifact("tool") + "/versions/1/references").then()
                .statusCode(200).body("artifactId", containsInAnyOrder("agent"));
    }

    @Test
    void featureOwnedLabelsSurviveMetadataUpdates() {
        given().contentType(CT_JSON).body(Map.of("labels", Map.of(FEATURE_LABEL, "v-1", "team", "a")))
                .put(artifact("server")).then().statusCode(204);
        given().contentType(CT_JSON).body(Map.of("labels", Map.of("team", "b")))
                .put(artifact("server")).then().statusCode(204);
        given().get(artifact("server")).then().statusCode(200)
                .body("labels", equalTo(Map.of(FEATURE_LABEL, "v-1", "team", "b")));
    }

    @Test
    void featureEndpointsAreUnavailable() {
        atRoot().get("/.well-known/agents").then().statusCode(404);
        atRoot().get("/.well-known/agents/" + group + "/agent").then().statusCode(404);
        atRoot().get("/.well-known/mcp-tools").then().statusCode(404);
        atRoot().get("/.well-known/ai-catalog.json").then().statusCode(404);
        atRoot().get("/apis/mcp-registry/v0.1/servers").then().statusCode(404);
        given().contentType(CT_JSON).body(Map.of("url", "http://localhost:1/mcp"))
                .post(artifact("server") + "/mcp-import").then().statusCode(409);
    }
}
