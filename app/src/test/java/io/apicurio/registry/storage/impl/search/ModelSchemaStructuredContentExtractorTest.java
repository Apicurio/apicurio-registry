package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ModelSchemaStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSchemaStructuredContentExtractorTest {

    private final ModelSchemaStructuredContentExtractor extractor = new ModelSchemaStructuredContentExtractor();

    @Test
    void testExtractModelId() {
        String schema = """
                {
                    "modelId": "gpt-4o",
                    "input": { "type": "object" }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("modelid") && e.name().equals("gpt-4o")));
    }

    @Test
    void testExtractProvider() {
        String schema = """
                {
                    "modelId": "gpt-4o",
                    "provider": "openai",
                    "input": { "type": "object" }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("provider") && e.name().equals("openai")));
    }

    @Test
    void testExtractInputProperties() {
        String schema = """
                {
                    "modelId": "gpt-4o",
                    "input": {
                        "type": "object",
                        "properties": {
                            "prompt": { "type": "string" },
                            "temperature": { "type": "number" },
                            "maxTokens": { "type": "integer" }
                        }
                    }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputproperty") && e.name().equals("prompt")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputproperty") && e.name().equals("temperature")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputproperty") && e.name().equals("maxTokens")));
    }

    @Test
    void testExtractOutputProperties() {
        String schema = """
                {
                    "modelId": "gpt-4o",
                    "output": {
                        "type": "object",
                        "properties": {
                            "text": { "type": "string" },
                            "usage": { "type": "object" }
                        }
                    }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputproperty") && e.name().equals("text")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputproperty") && e.name().equals("usage")));
    }

    @Test
    void testComprehensiveExtraction() {
        String schema = """
                {
                    "modelId": "claude-sonnet-4-6",
                    "provider": "anthropic",
                    "input": {
                        "type": "object",
                        "properties": {
                            "messages": { "type": "array" },
                            "system": { "type": "string" }
                        }
                    },
                    "output": {
                        "type": "object",
                        "properties": {
                            "content": { "type": "array" },
                            "stop_reason": { "type": "string" }
                        }
                    }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("modelid")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("provider")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("inputproperty")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("outputproperty")));

        assertEquals(1, elements.stream().filter(e -> e.kind().equals("modelid")).count());
        assertEquals(1, elements.stream().filter(e -> e.kind().equals("provider")).count());
        assertEquals(2, elements.stream().filter(e -> e.kind().equals("inputproperty")).count());
        assertEquals(2, elements.stream().filter(e -> e.kind().equals("outputproperty")).count());
    }

    @Test
    void testNoPropertiesSection() {
        String schema = """
                {
                    "modelId": "test",
                    "input": { "type": "object" },
                    "output": { "type": "object" }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(schema));

        assertEquals(1, elements.size());
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("modelid") && e.name().equals("test")));
    }

    @Test
    void testInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not json"));

        assertTrue(elements.isEmpty());
    }

    @Test
    void testEmptyObject() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("{}"));

        assertTrue(elements.isEmpty());
    }
}
