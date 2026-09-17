package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
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

    @Test
    void responsesValidateAgainstPinnedManifestAndUpstreamMetadataShape() throws Exception {
        String name = "io.github.shape" + UUID.randomUUID().toString().replace("-", "") + "/server";
        String base = "/mcp-registry/v0.1";
        String json = given().contentType(CT_JSON)
                .body(Map.of("name", name, "version", "1.0.0", "description", "Shape test"))
                .post(base + "/publish").then().statusCode(200).extract().asString();
        JSONObject response = new JSONObject(json);
        assertEquals(name, response.getJSONObject("server").getString("name"));
        try (InputStream stream = getClass().getResourceAsStream(
                "/io/apicurio/registry/rules/validity/mcp-server-2025-12-11.json")) {
            assertNotNull(stream);
            Schema manifest = SchemaLoader.builder().schemaJson(new JSONObject(new JSONTokener(stream)))
                    .draftV7Support().build().load().build();
            manifest.validate(response.getJSONObject("server"));
        }
        // Copied assertion fields from upstream ServerResponse official metadata: no extra id there.
        Schema metadata = SchemaLoader.load(new JSONObject("""
                {"type":"object","additionalProperties":false,"properties":{
                 "status":{"type":"string","enum":["active","deprecated","deleted"]},
                 "statusMessage":{"type":"string","maxLength":500},
                 "statusChangedAt":{"type":"string","format":"date-time"},
                 "publishedAt":{"type":"string","format":"date-time"},
                 "updatedAt":{"type":"string","format":"date-time"},
                 "isLatest":{"type":"boolean"}}}
                """));
        metadata.validate(response.getJSONObject("_meta").getJSONObject("io.modelcontextprotocol.registry/official"));
        JSONObject listed = new JSONObject(given().queryParam("search", name).get(base + "/servers")
                .then().statusCode(200).extract().asString());
        assertEquals(1, listed.getJSONArray("servers").length());
        assertEquals(name, listed.getJSONArray("servers").getJSONObject(0).getJSONObject("server").getString("name"));
        metadata.validate(listed.getJSONArray("servers").getJSONObject(0).getJSONObject("_meta")
                .getJSONObject("io.modelcontextprotocol.registry/official"));
    }
}
