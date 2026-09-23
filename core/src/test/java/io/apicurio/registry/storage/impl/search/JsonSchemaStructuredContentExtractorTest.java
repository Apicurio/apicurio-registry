package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.json.content.extract.JsonSchemaStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaStructuredContentExtractorTest {

    private final JsonSchemaStructuredContentExtractor extractor = new JsonSchemaStructuredContentExtractor();

    @Test
    void testExtractId() {
        String jsonSchema = """
                {
                  "$id": "https://example.com/schemas/user",
                  "type": "object",
                  "properties": {}
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(jsonSchema));

        assertTrue(elements.stream().anyMatch(
                e -> e.kind().equals("id") && e.name().equals("https://example.com/schemas/user")));
    }

    @Test
    void testExtractProperties() {
        String jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "firstName": { "type": "string" },
                    "lastName": { "type": "string" },
                    "age": { "type": "integer" },
                    "email": { "type": "string", "format": "email" }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(jsonSchema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("property") && e.name().equals("firstName")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("property") && e.name().equals("lastName")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("property") && e.name().equals("age")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("property") && e.name().equals("email")));
    }

    @Test
    void testExtractDefinitions_Defs() {
        String jsonSchema = """
                {
                  "type": "object",
                  "properties": {},
                  "$defs": {
                    "Address": { "type": "object" },
                    "PhoneNumber": { "type": "string" }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(jsonSchema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("definition") && e.name().equals("Address")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("definition") && e.name().equals("PhoneNumber")));
    }

    @Test
    void testExtractDefinitions_OlderDraft() {
        String jsonSchema = """
                {
                  "type": "object",
                  "properties": {},
                  "definitions": {
                    "Color": { "type": "string", "enum": ["red", "green", "blue"] },
                    "Size": { "type": "integer", "minimum": 0 }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(jsonSchema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("definition") && e.name().equals("Color")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("definition") && e.name().equals("Size")));
    }

    @Test
    void testExtractRequired() {
        String jsonSchema = """
                {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "email": { "type": "string" },
                    "age": { "type": "integer" }
                  },
                  "required": ["name", "email"]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(jsonSchema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("required") && e.name().equals("name")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("required") && e.name().equals("email")));
    }

    @Test
    void testComprehensiveExtraction() {
        String jsonSchema = """
                {
                  "$id": "https://example.com/schemas/order",
                  "type": "object",
                  "properties": {
                    "orderId": { "type": "string" },
                    "customer": { "$ref": "#/$defs/Customer" },
                    "total": { "type": "number" }
                  },
                  "required": ["orderId", "total"],
                  "$defs": {
                    "Customer": {
                      "type": "object",
                      "properties": {
                        "name": { "type": "string" }
                      }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(jsonSchema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("id")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("property")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("definition")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("required")));
    }

    @Test
    void testExtractFromEmptySchema() {
        String jsonSchema = """
                {
                  "type": "object"
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(jsonSchema));

        assertEquals(0, elements.size());
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not valid json"));

        assertTrue(elements.isEmpty());
    }
}
