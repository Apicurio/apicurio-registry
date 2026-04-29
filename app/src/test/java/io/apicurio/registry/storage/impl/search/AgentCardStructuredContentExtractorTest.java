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
                  "description": "Test",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://example.com", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [
                    { "id": "schema-validation", "name": "Schema Validation", "description": "Validate schemas", "tags": ["validation"] },
                    { "id": "data-transformation", "name": "Data Transformation", "description": "Transform data", "tags": ["data"] }
                  ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"]
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
                  "description": "Test",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://example.com", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {
                    "streaming": true,
                    "pushNotifications": false,
                    "stateTransitionHistory": true
                  },
                  "skills": [
                    { "id": "s1", "name": "Skill", "description": "A skill", "tags": ["test"] }
                  ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"]
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
                  "description": "Test",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://example.com", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [
                    { "id": "s1", "name": "Skill", "description": "A skill", "tags": ["test"] }
                  ],
                  "defaultInputModes": ["text", "audio", "image"],
                  "defaultOutputModes": ["text"]
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
                  "description": "Test",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://example.com", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [
                    { "id": "s1", "name": "Skill", "description": "A skill", "tags": ["test"] }
                  ],
                  "defaultInputModes": ["text"],
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
    void testExtractProtocolBindings() {
        String agentCard = """
                {
                  "name": "Test Agent",
                  "description": "Test",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://example.com/jsonrpc", "protocolBinding": "jsonrpc", "protocolVersion": "1.0" },
                    { "url": "https://example.com/rest", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [
                    { "id": "s1", "name": "Skill", "description": "A skill", "tags": ["test"] }
                  ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("protocolbinding") && e.name().equals("jsonrpc")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("protocolbinding") && e.name().equals("http+json")));
    }

    @Test
    void testExtractSecuritySchemeNames() {
        String agentCard = """
                {
                  "name": "Test Agent",
                  "description": "Test",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://example.com", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [
                    { "id": "s1", "name": "Skill", "description": "A skill", "tags": ["test"] }
                  ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"],
                  "securitySchemes": {
                    "bearer": { "type": "httpAuth", "scheme": "Bearer" },
                    "apikey": { "type": "apiKey", "name": "X-API-Key", "location": "header" }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("securityscheme") && e.name().equals("bearer")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("securityscheme") && e.name().equals("apikey")));
    }

    @Test
    void testExtractTags() {
        String agentCard = """
                {
                  "name": "Test Agent",
                  "description": "Test",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://example.com", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {},
                  "skills": [
                    { "id": "s1", "name": "Skill 1", "description": "A skill", "tags": ["analytics", "data"] },
                    { "id": "s2", "name": "Skill 2", "description": "Another skill", "tags": ["data", "reporting"] }
                  ],
                  "defaultInputModes": ["text"],
                  "defaultOutputModes": ["text"]
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("tag") && e.name().equals("analytics")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("tag") && e.name().equals("data")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("tag") && e.name().equals("reporting")));
        // "data" appears in both skills but should only be extracted once
        assertEquals(1, elements.stream()
                .filter(e -> e.kind().equals("tag") && e.name().equals("data")).count());
    }

    @Test
    void testComprehensiveExtraction() {
        String agentCard = """
                {
                  "name": "Comprehensive Agent",
                  "description": "A fully-featured agent",
                  "version": "1.0.0",
                  "supportedInterfaces": [
                    { "url": "https://agent.example.com", "protocolBinding": "http+json", "protocolVersion": "1.0" }
                  ],
                  "capabilities": {
                    "streaming": true,
                    "pushNotifications": true
                  },
                  "skills": [
                    { "id": "summarization", "name": "Text Summarization", "description": "Summarize text", "tags": ["nlp", "text"] },
                    { "id": "translation", "name": "Language Translation", "description": "Translate text", "tags": ["nlp", "translation"] }
                  ],
                  "defaultInputModes": ["text", "audio"],
                  "defaultOutputModes": ["text"],
                  "securitySchemes": {
                    "bearer": { "type": "httpAuth", "scheme": "Bearer" }
                  }
                }
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(agentCard));

        // Verify all element types are present
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("skill")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("capability")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("inputmode")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("outputmode")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("protocolbinding")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("securityscheme")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("tag")));

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
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("protocolbinding") && e.name().equals("http+json")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("securityscheme") && e.name().equals("bearer")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("tag") && e.name().equals("nlp")));
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
