package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlContentCanonicalizerTest {

    private YamlContentCanonicalizer canonicalizer;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @BeforeEach
    void setUp() {
        canonicalizer = new YamlContentCanonicalizer();
    }

    private TypedContent createJson(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    private TypedContent createYaml(String yaml) {
        return TypedContent.create(ContentHandle.create(yaml), ContentTypes.APPLICATION_YAML);
    }

    @Test
    void testCanonicalizeSortsKeys() throws Exception {
        String input = """
                {
                    "zebra": "last",
                    "alpha": "first",
                    "middle": "between"
                }
                """;

        TypedContent result = canonicalizer.canonicalize(createJson(input), Collections.emptyMap());

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        Iterator<String> fields = tree.fieldNames();
        assertEquals("alpha", fields.next());
        assertEquals("middle", fields.next());
        assertEquals("zebra", fields.next());
    }

    @Test
    void testCanonicalizeOutputsYaml() {
        String input = """
                {
                    "templateId": "test",
                    "template": "Hello"
                }
                """;

        TypedContent result = canonicalizer.canonicalize(createJson(input), Collections.emptyMap());
        assertEquals(ContentTypes.APPLICATION_YAML, result.getContentType());
    }

    @Test
    void testCanonicalizeFromYamlInput() throws Exception {
        String input = """
                zebra: last
                alpha: first
                middle: between
                """;

        TypedContent result = canonicalizer.canonicalize(createYaml(input), Collections.emptyMap());

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        Iterator<String> fields = tree.fieldNames();
        assertEquals("alpha", fields.next());
        assertEquals("middle", fields.next());
        assertEquals("zebra", fields.next());
    }

    @Test
    void testCanonicalizeSortsNestedKeys() throws Exception {
        String input = """
                {
                    "outer": {
                        "z_field": "z",
                        "a_field": "a"
                    },
                    "alpha": "first"
                }
                """;

        TypedContent result = canonicalizer.canonicalize(createJson(input), Collections.emptyMap());

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        Iterator<String> outerFields = tree.fieldNames();
        assertEquals("alpha", outerFields.next());
        assertEquals("outer", outerFields.next());

        Iterator<String> innerFields = tree.get("outer").fieldNames();
        assertEquals("a_field", innerFields.next());
        assertEquals("z_field", innerFields.next());
    }

    @Test
    void testCanonicalizeProducesDeterministicOutput() {
        String input1 = """
                { "b": 2, "a": 1, "c": 3 }
                """;
        String input2 = """
                { "c": 3, "a": 1, "b": 2 }
                """;

        TypedContent result1 = canonicalizer.canonicalize(createJson(input1), Collections.emptyMap());
        TypedContent result2 = canonicalizer.canonicalize(createJson(input2), Collections.emptyMap());

        assertEquals(result1.getContent().content(), result2.getContent().content());
    }

    @Test
    void testCanonicalizeInvalidContentReturnsOriginal() {
        TypedContent content = createJson("not valid json");
        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());
        assertEquals(content.getContent().content(), result.getContent().content());
    }

    @Test
    void testCanonicalizePromptTemplate() throws Exception {
        String input = """
                {
                    "variables": {
                        "name": { "type": "string" }
                    },
                    "templateId": "greet",
                    "template": "Hello {{name}}"
                }
                """;

        TypedContent result = canonicalizer.canonicalize(createJson(input), Collections.emptyMap());

        JsonNode tree = yamlMapper.readTree(result.getContent().content());
        Iterator<String> fields = tree.fieldNames();
        assertEquals("template", fields.next());
        assertEquals("templateId", fields.next());
        assertEquals("variables", fields.next());
    }

    @Test
    void testCanonicalizeWithTextPromptTemplateContentType() throws Exception {
        String input = """
                templateId: greet
                template: "Hello {{name}}"
                variables:
                  name:
                    type: string
                """;

        TypedContent content = TypedContent.create(
                ContentHandle.create(input), "text/x-prompt-template");
        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        assertTrue(result.getContent().content().contains("templateId"));
        assertEquals(ContentTypes.APPLICATION_YAML, result.getContentType());
    }
}
