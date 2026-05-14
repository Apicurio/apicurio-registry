package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateDereferencerTest {

    private PromptTemplateDereferencer dereferencer;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @BeforeEach
    void setUp() {
        dereferencer = new PromptTemplateDereferencer();
    }

    private TypedContent createJson(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testDereferenceNoRefs() throws Exception {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello {{name}}",
                    "variables": {
                        "name": { "type": "string" }
                    }
                }
                """;

        TypedContent result = dereferencer.dereference(createJson(template), Collections.emptyMap());

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        assertEquals("test", tree.get("templateId").asText());
        assertEquals("Hello {{name}}", tree.get("template").asText());
    }

    @Test
    void testDereferenceResolvesRefInVariables() throws Exception {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello {{config}}",
                    "variables": {
                        "config": { "$ref": "shared-vars.json" }
                    }
                }
                """;

        String resolved = """
                {
                    "type": "object",
                    "properties": {
                        "timeout": { "type": "integer" }
                    }
                }
                """;

        TypedContent result = dereferencer.dereference(
                createJson(template),
                Map.of("shared-vars.json", createJson(resolved)));

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        JsonNode config = tree.get("variables").get("config");
        assertFalse(config.has("$ref"), "The $ref should have been resolved");
        assertTrue(config.has("type"));
        assertEquals("object", config.get("type").asText());
    }

    @Test
    void testDereferenceResolvesRefInOutputSchema() throws Exception {
        String template = """
                {
                    "templateId": "test",
                    "template": "Analyze this",
                    "outputSchema": {
                        "type": "object",
                        "properties": {
                            "result": { "$ref": "result-types.json" }
                        }
                    }
                }
                """;

        String resolved = """
                {
                    "type": "string",
                    "description": "The analysis result"
                }
                """;

        TypedContent result = dereferencer.dereference(
                createJson(template),
                Map.of("result-types.json", createJson(resolved)));

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        JsonNode resultProp = tree.get("outputSchema").get("properties").get("result");
        assertFalse(resultProp.has("$ref"));
        assertEquals("string", resultProp.get("type").asText());
    }

    @Test
    void testDereferenceOutputsYaml() throws Exception {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello"
                }
                """;

        TypedContent result = dereferencer.dereference(createJson(template), Collections.emptyMap());
        assertEquals(ContentTypes.APPLICATION_YAML, result.getContentType());
    }

    @Test
    void testRewriteReferencesInVariables() throws Exception {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello {{config}}",
                    "variables": {
                        "config": { "$ref": "shared-vars.json" }
                    }
                }
                """;

        TypedContent result = dereferencer.rewriteReferences(
                createJson(template),
                Map.of("shared-vars.json", "https://registry.example.com/artifacts/shared/versions/1"));

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        String rewritten = tree.get("variables").get("config").get("$ref").asText();
        assertEquals("https://registry.example.com/artifacts/shared/versions/1", rewritten);
    }

    @Test
    void testRewriteReferencesInOutputSchema() throws Exception {
        String template = """
                {
                    "templateId": "test",
                    "template": "Analyze",
                    "outputSchema": {
                        "type": "object",
                        "properties": {
                            "result": { "$ref": "result.json" }
                        }
                    }
                }
                """;

        TypedContent result = dereferencer.rewriteReferences(
                createJson(template),
                Map.of("result.json", "https://registry.example.com/artifacts/result/versions/2"));

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        String rewritten = tree.get("outputSchema").get("properties").get("result").get("$ref").asText();
        assertEquals("https://registry.example.com/artifacts/result/versions/2", rewritten);
    }

    @Test
    void testDereferenceInvalidContentDoesNotThrow() {
        TypedContent content = createJson("not valid json");
        TypedContent result = dereferencer.dereference(content, Collections.emptyMap());
        assertNotNull(result);
        assertNotNull(result.getContent().content());
    }
}
