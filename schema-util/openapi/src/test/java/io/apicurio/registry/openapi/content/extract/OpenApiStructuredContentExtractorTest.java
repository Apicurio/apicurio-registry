package io.apicurio.registry.openapi.content.extract;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.content.extract.StructuredElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiStructuredContentExtractorTest {

    private final OpenApiStructuredContentExtractor extractor = new OpenApiStructuredContentExtractor();
    private final ApicurioDataModelsContentExtractor metaExtractor = new ApicurioDataModelsContentExtractor();

    @Test
    void testExtractFromJson() {
        String openApiJson = """
                {
                  "openapi": "3.0.2",
                  "info": { "title": "Pet Store API", "version": "1.0", "description": "Pet Store Description" },
                  "paths": {
                    "/pets": {
                      "get": { "operationId": "listPets" }
                    }
                  },
                  "components": {
                    "schemas": {
                      "Pet": { "type": "object" }
                    }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApiJson));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("schema") && e.name().equals("Pet")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("path") && e.name().equals("/pets")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("operation") && e.name().equals("listPets")));

        ExtractedMetaData meta = metaExtractor.extract(ContentHandle.create(openApiJson));
        assertNotNull(meta);
        assertEquals("Pet Store API", meta.getName());
        assertEquals("Pet Store Description", meta.getDescription());
    }

    @Test
    void testExtractFromYaml() {
        String openApiYaml = """
                openapi: 3.0.2
                info:
                  title: Pet Store API
                  version: '1.0'
                  description: Pet Store Description
                paths:
                  /pets:
                    get:
                      operationId: listPets
                components:
                  schemas:
                    Pet:
                      type: object
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(openApiYaml));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("schema") && e.name().equals("Pet")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("path") && e.name().equals("/pets")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("operation") && e.name().equals("listPets")));

        ExtractedMetaData meta = metaExtractor.extract(ContentHandle.create(openApiYaml));
        assertNotNull(meta);
        assertEquals("Pet Store API", meta.getName());
        assertEquals("Pet Store Description", meta.getDescription());
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not a valid openapi spec"));
        assertTrue(elements.isEmpty());
    }
}
