package io.apicurio.registry.storage.impl.search;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.PromptTemplateStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateStructuredContentExtractorTest {

    private final PromptTemplateStructuredContentExtractor extractor =
            new PromptTemplateStructuredContentExtractor();

    @Test
    void testExtractTemplateId() {
        String template = """
                {
                    "templateId": "code-review",
                    "template": "Review this code"
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(template));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("templateid") && e.name().equals("code-review")));
    }

    @Test
    void testExtractVariableNames() {
        String template = """
                {
                    "templateId": "greet",
                    "template": "Hello {{name}}, welcome to {{place}}",
                    "variables": {
                        "name": { "type": "string", "required": true },
                        "place": { "type": "string", "required": false },
                        "style": { "type": "string", "enum": ["formal", "casual"] }
                    }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(template));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("variable") && e.name().equals("name")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("variable") && e.name().equals("place")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("variable") && e.name().equals("style")));
        assertEquals(3, elements.stream().filter(e -> e.kind().equals("variable")).count());
    }

    @Test
    void testExtractOutputSchemaProperties() {
        String template = """
                {
                    "templateId": "analyze",
                    "template": "Analyze {{input}}",
                    "variables": {
                        "input": { "type": "string" }
                    },
                    "outputSchema": {
                        "type": "object",
                        "properties": {
                            "summary": { "type": "string" },
                            "confidence": { "type": "number" },
                            "categories": { "type": "array" }
                        }
                    }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(template));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputproperty") && e.name().equals("summary")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputproperty") && e.name().equals("confidence")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputproperty") && e.name().equals("categories")));
    }

    @Test
    void testComprehensiveExtraction() {
        String template = """
                {
                    "templateId": "code-review",
                    "template": "Review {{code}} in {{language}}",
                    "variables": {
                        "code": { "type": "string" },
                        "language": { "type": "string" }
                    },
                    "outputSchema": {
                        "type": "object",
                        "properties": {
                            "issues": { "type": "array" },
                            "score": { "type": "number" }
                        }
                    }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(template));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("templateid")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("variable")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("outputproperty")));

        assertEquals(1, elements.stream().filter(e -> e.kind().equals("templateid")).count());
        assertEquals(2, elements.stream().filter(e -> e.kind().equals("variable")).count());
        assertEquals(2, elements.stream().filter(e -> e.kind().equals("outputproperty")).count());
    }

    @Test
    void testNoVariablesOrOutputSchema() {
        String template = """
                {
                    "templateId": "simple",
                    "template": "Just a static prompt"
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(template));

        assertEquals(1, elements.size());
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("templateid") && e.name().equals("simple")));
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
