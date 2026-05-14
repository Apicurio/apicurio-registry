package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.openapi.content.extract.OpenApiStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiStructuredContentExtractorTest {

    private final OpenApiStructuredContentExtractor extractor = new OpenApiStructuredContentExtractor();

    @Test
    void testExtractSchemas() {
        String openApi = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Test API", "version": "1.0" },
                  "paths": {},
                  "components": {
                    "schemas": {
                      "Pet": { "type": "object" },
                      "Order": { "type": "object" }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApi));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("schema") && e.name().equals("Pet")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("schema") && e.name().equals("Order")));
    }

    @Test
    void testExtractPaths() {
        String openApi = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Test API", "version": "1.0" },
                  "paths": {
                    "/pets": {
                      "get": { "operationId": "listPets" }
                    },
                    "/pets/{id}": {
                      "get": { "operationId": "getPet" },
                      "delete": { "operationId": "deletePet" }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApi));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("path") && e.name().equals("/pets")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("path") && e.name().equals("/pets/{id}")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("operation") && e.name().equals("listPets")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("operation") && e.name().equals("getPet")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("operation") && e.name().equals("deletePet")));
    }

    @Test
    void testExtractTags() {
        String openApi = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Test API", "version": "1.0" },
                  "paths": {},
                  "tags": [
                    { "name": "pets" },
                    { "name": "orders" }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApi));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("tag") && e.name().equals("pets")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("tag") && e.name().equals("orders")));
    }

    @Test
    void testExtractSecuritySchemesAndParameters() {
        String openApi = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Test API", "version": "1.0" },
                  "paths": {},
                  "components": {
                    "parameters": {
                      "limitParam": { "name": "limit", "in": "query" }
                    },
                    "securitySchemes": {
                      "bearerAuth": { "type": "http", "scheme": "bearer" }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApi));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("parameter") && e.name().equals("limitParam")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("security_scheme") && e.name().equals("bearerAuth")));
    }

    @Test
    void testExtractServers() {
        String openApi = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Test API", "version": "1.0" },
                  "paths": {},
                  "servers": [
                    { "url": "https://api.example.com/v1" },
                    { "url": "https://staging.example.com/v1" }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApi));

        assertTrue(elements.stream().anyMatch(
                e -> e.kind().equals("server") && e.name().equals("https://api.example.com/v1")));
        assertTrue(elements.stream().anyMatch(
                e -> e.kind().equals("server") && e.name().equals("https://staging.example.com/v1")));
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not json at all"));

        assertTrue(elements.isEmpty());
    }

    @Test
    void testExtractFromEmptyOpenApi() {
        String openApi = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Empty", "version": "1.0" },
                  "paths": {}
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApi));

        // Should only have the empty paths object, no path elements
        assertEquals(0, elements.size());
    }

    @Test
    void testComprehensiveExtraction() {
        String openApi = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Pet Store", "version": "1.0" },
                  "servers": [{ "url": "https://petstore.example.com" }],
                  "tags": [{ "name": "pets" }],
                  "paths": {
                    "/pets": {
                      "get": { "operationId": "listPets" }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Pet": { "type": "object" }
                    },
                    "parameters": {
                      "pageSize": { "name": "pageSize", "in": "query" }
                    },
                    "securitySchemes": {
                      "apiKey": { "type": "apiKey" }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApi));

        // Verify all element types are present
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("schema")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("path")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("operation")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("tag")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("parameter")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("security_scheme")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("server")));
    }
}
