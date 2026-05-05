package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSchemaDereferencerTest {

    private ModelSchemaDereferencer dereferencer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        dereferencer = new ModelSchemaDereferencer();
    }

    private TypedContent create(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testDereferenceNoRefs() throws Exception {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "prompt": { "type": "string" }
                        }
                    }
                }
                """;

        TypedContent result = dereferencer.dereference(create(schema), Collections.emptyMap());

        JsonNode tree = mapper.readTree(result.getContent().content());
        assertEquals("test", tree.get("modelId").asText());
        assertTrue(tree.has("input"));
    }

    @Test
    void testDereferenceResolvesRef() throws Exception {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "config.json" }
                        }
                    }
                }
                """;

        String resolved = """
                {
                    "type": "object",
                    "properties": {
                        "setting1": { "type": "string" }
                    }
                }
                """;

        TypedContent result = dereferencer.dereference(
                create(schema),
                Map.of("config.json", create(resolved)));

        JsonNode tree = mapper.readTree(result.getContent().content());
        JsonNode config = tree.get("input").get("properties").get("config");
        assertFalse(config.has("$ref"), "The $ref should have been resolved");
        assertTrue(config.has("type"), "The resolved content should be inlined");
        assertEquals("object", config.get("type").asText());
    }

    @Test
    void testDereferencePreservesUnresolvedRefs() throws Exception {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "missing.json" }
                        }
                    }
                }
                """;

        TypedContent result = dereferencer.dereference(create(schema), Collections.emptyMap());

        JsonNode tree = mapper.readTree(result.getContent().content());
        assertEquals("missing.json", tree.get("input").get("properties").get("config").get("$ref").asText());
    }

    @Test
    void testRewriteReferences() throws Exception {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "config.json" }
                        }
                    }
                }
                """;

        TypedContent result = dereferencer.rewriteReferences(
                create(schema),
                Map.of("config.json", "https://registry.example.com/artifacts/config/versions/1"));

        JsonNode tree = mapper.readTree(result.getContent().content());
        String rewritten = tree.get("input").get("properties").get("config").get("$ref").asText();
        assertEquals("https://registry.example.com/artifacts/config/versions/1", rewritten);
    }

    @Test
    void testRewriteReferencesWithComponent() throws Exception {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "types.json#/defs/Config" }
                        }
                    }
                }
                """;

        TypedContent result = dereferencer.rewriteReferences(
                create(schema),
                Map.of("types.json", "https://registry.example.com/artifacts/types/versions/1"));

        JsonNode tree = mapper.readTree(result.getContent().content());
        String rewritten = tree.get("input").get("properties").get("config").get("$ref").asText();
        assertEquals("https://registry.example.com/artifacts/types/versions/1#/defs/Config", rewritten);
    }

    @Test
    void testRewritePreservesInternalRefs() throws Exception {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "#/definitions/Config" }
                        }
                    }
                }
                """;

        TypedContent result = dereferencer.rewriteReferences(create(schema), Collections.emptyMap());

        JsonNode tree = mapper.readTree(result.getContent().content());
        assertEquals("#/definitions/Config",
                tree.get("input").get("properties").get("config").get("$ref").asText());
    }

    @Test
    void testDereferenceInvalidContentDoesNotThrow() {
        TypedContent content = create("not valid json");
        TypedContent result = dereferencer.dereference(content, Collections.emptyMap());
        assertNotNull(result);
        assertNotNull(result.getContent().content());
    }
}
