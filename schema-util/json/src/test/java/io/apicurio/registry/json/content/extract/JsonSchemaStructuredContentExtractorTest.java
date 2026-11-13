package io.apicurio.registry.json.content.extract;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaStructuredContentExtractorTest {

    private final JsonSchemaStructuredContentExtractor extractor = new JsonSchemaStructuredContentExtractor();

    @Test
    void extractsRootLevelPropertiesAndId() {
        String schema = """
                {
                  "$id": "https://example.com/person.json",
                  "properties": {
                    "name": { "type": "string" },
                    "age":  { "type": "integer" }
                  },
                  "required": ["name"]
                }
                """;
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elementExists(elements, "id", "https://example.com/person.json"));
        assertTrue(elementExists(elements, "property", "name"));
        assertTrue(elementExists(elements, "property", "age"));
        assertTrue(elementExists(elements, "required", "name"));
    }

    @Test
    void extractsPropertiesInsideAllOf() {
        String schema = """
                {
                  "allOf": [
                    {
                      "properties": {
                        "name": { "type": "string" }
                      },
                      "required": ["name"]
                    }
                  ]
                }
                """;
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elementExists(elements, "property", "name"),
                "property 'name' inside allOf must be extracted");
        assertTrue(elementExists(elements, "required", "name"),
                "required 'name' inside allOf must be extracted");
    }

    @Test
    void extractsPropertiesInsideAnyOf() {
        String schema = """
                {
                  "anyOf": [
                    { "properties": { "email": { "type": "string" } } },
                    { "properties": { "phone": { "type": "string" } } }
                  ]
                }
                """;
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elementExists(elements, "property", "email"),
                "property 'email' inside anyOf must be extracted");
        assertTrue(elementExists(elements, "property", "phone"),
                "property 'phone' inside anyOf must be extracted");
    }

    @Test
    void extractsPropertiesInsideOneOf() {
        String schema = """
                {
                  "oneOf": [
                    { "properties": { "cat": { "type": "string" } } },
                    { "properties": { "dog": { "type": "string" } } }
                  ]
                }
                """;
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elementExists(elements, "property", "cat"),
                "property 'cat' inside oneOf must be extracted");
        assertTrue(elementExists(elements, "property", "dog"),
                "property 'dog' inside oneOf must be extracted");
    }

    @Test
    void extractsDefinitionsInsideAllOf() {
        String schema = """
                {
                  "allOf": [
                    {
                      "$defs": {
                        "Address": { "type": "object" }
                      }
                    }
                  ]
                }
                """;
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elementExists(elements, "definition", "Address"),
                "definition 'Address' inside allOf.$defs must be extracted");
    }

    @Test
    void extractsRootLevelDefs() {
        String schema = """
                {
                  "$defs": {
                    "Street": { "type": "string" }
                  },
                  "definitions": {
                    "City": { "type": "string" }
                  }
                }
                """;
        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elementExists(elements, "definition", "Street"),
                "'Street' from $defs must be extracted");
        assertTrue(elementExists(elements, "definition", "City"),
                "'City' from definitions must be extracted");
    }

    @Test
    void returnsEmptyListForInvalidJson() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not-json"));
        assertEquals(0, elements.size());
    }

    @Test
    void returnsEmptyListForEmptyObject() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("{}"));
        assertEquals(0, elements.size());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private boolean elementExists(List<StructuredElement> elements, String kind, String name) {
        return elements.stream()
                .anyMatch(e -> kind.equals(e.kind()) && name.equals(e.name()));
    }
}
