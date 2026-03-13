package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.AgentCardStructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardStructuredContentExtractorTest {

    private final AgentCardStructuredContentExtractor extractor = new AgentCardStructuredContentExtractor();

    @Test
    void testExtractSkills() {
        String agentCard = """
                {
                  "name": "Test Agent",
                  "skills": [
                    { "id": "schema-validation", "name": "Schema Validation" },
                    { "id": "data-transformation", "name": "Data Transformation" }
                  ]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("skill") && e.name().equals("schema-validation")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("skill") && e.name().equals("data-transformation")));
    }

    @Test
    void testExtractCapabilities() {
        String agentCard = """
                {
                  "name": "Test Agent",
                  "capabilities": {
                    "streaming": true,
                    "pushNotifications": false,
                    "stateTransitionHistory": true
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        // Only capabilities with value true should be extracted
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("capability") && e.name().equals("streaming")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("capability") && e.name().equals("stateTransitionHistory")));
        // pushNotifications is false, so it should NOT be extracted
        assertTrue(elements.stream()
                .noneMatch(e -> e.kind().equals("capability") && e.name().equals("pushNotifications")));
    }

    @Test
    void testExtractInputModes() {
        String agentCard = """
                {
                  "name": "Test Agent",
                  "defaultInputModes": ["text", "audio", "image"]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputmode") && e.name().equals("text")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputmode") && e.name().equals("audio")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputmode") && e.name().equals("image")));
    }

    @Test
    void testExtractOutputModes() {
        String agentCard = """
                {
                  "name": "Test Agent",
                  "defaultOutputModes": ["text", "video"]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputmode") && e.name().equals("text")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputmode") && e.name().equals("video")));
    }

    @Test
    void testComprehensiveExtraction() {
        String agentCard = """
                {
                  "name": "Comprehensive Agent",
                  "url": "https://agent.example.com",
                  "description": "A fully-featured agent",
                  "skills": [
                    { "id": "summarization", "name": "Text Summarization" },
                    { "id": "translation", "name": "Language Translation" }
                  ],
                  "capabilities": {
                    "streaming": true,
                    "pushNotifications": true
                  },
                  "defaultInputModes": ["text", "audio"],
                  "defaultOutputModes": ["text"]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        // Verify all element types are present
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("skill")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("capability")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("inputmode")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("outputmode")));

        // Verify specific values
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("skill") && e.name().equals("summarization")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("skill") && e.name().equals("translation")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("capability") && e.name().equals("streaming")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("capability") && e.name().equals("pushNotifications")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputmode") && e.name().equals("text")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("inputmode") && e.name().equals("audio")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("outputmode") && e.name().equals("text")));
    }

    @Test
    void testInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not json at all"));

        assertTrue(elements.isEmpty());
    }

    @Test
    void testEmptyAgentCard() {
        String agentCard = """
                {
                  "name": "Minimal Agent"
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        assertEquals(0, elements.size());
    }
}
