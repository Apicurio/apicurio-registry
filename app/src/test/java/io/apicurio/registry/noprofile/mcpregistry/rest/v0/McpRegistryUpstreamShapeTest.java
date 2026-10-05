package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.noprofile.mcpregistry.rest.v0.McpRegistryRequests.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Validates real HTTP responses independently of the PR's generated response beans. */
@QuarkusTest
@TestProfile(McpRegistryExperimentalFeaturesProfile.class)
class McpRegistryUpstreamShapeTest extends AbstractResourceTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void responsesValidateAgainstPinnedManifestAndUpstreamMetadataShape() throws Exception {
        String name = "io.github.shape" + UUID.randomUUID().toString().replace("-", "") + "/server";
        String base = "/mcp-registry/v0.1";
        String json = given().contentType(CT_JSON)
                .body(Map.of("name", name, "version", "1.0.0", "description", "Shape test"))
                .post(base + "/publish").then().statusCode(200).extract().asString();
        JsonNode response = MAPPER.readTree(json);
        assertEquals(name, response.path("server").path("name").asText());
        try (InputStream stream = getClass().getResourceAsStream(
                "/io/apicurio/registry/agents/rules/validity/mcp-server-2025-12-11.json")) {
            assertNotNull(stream);
            assertValid(schema(MAPPER.readTree(stream)), response.get("server"));
        }
        // Copied assertion fields from upstream ServerResponse official metadata: no extra id there.
        JsonSchema metadata = schema(MAPPER.readTree("""
                {"type":"object","additionalProperties":false,"properties":{
                 "status":{"type":"string","enum":["active","deprecated","deleted"]},
                 "statusMessage":{"type":"string","maxLength":500},
                 "statusChangedAt":{"type":"string","format":"date-time"},
                 "publishedAt":{"type":"string","format":"date-time"},
                 "updatedAt":{"type":"string","format":"date-time"},
                 "isLatest":{"type":"boolean"}}}
                """));
        assertValid(metadata, response.path("_meta").get("io.modelcontextprotocol.registry/official"));
        JsonNode listed = MAPPER.readTree(given().queryParam("search", name).get(base + "/servers")
                .then().statusCode(200).extract().asString());
        assertEquals(1, listed.path("servers").size());
        JsonNode first = listed.path("servers").path(0);
        assertEquals(name, first.path("server").path("name").asText());
        assertValid(metadata, first.path("_meta").get("io.modelcontextprotocol.registry/official"));
    }

    private static JsonSchema schema(JsonNode schema) {
        return JsonSchemaFactory.getInstance(VersionFlag.V7).getSchema(schema);
    }

    private static void assertValid(JsonSchema schema, JsonNode document) {
        assertNotNull(document);
        var messages = schema.validate(document);
        assertEquals(0, messages.size(), () -> "Response doesn't match the schema: " + messages);
    }
}
