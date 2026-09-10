package io.apicurio.registry.a2a.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OpenApiAgentCardAssembler}. Plain JUnit - the assembler has no CDI
 * dependencies and does not touch storage.
 */
public class OpenApiAgentCardAssemblerTest {

    private static final String FULL_SKILLS_BLOCK = """
            "capabilities": {},
            "skills": [
              {
                "id": "get-weather",
                "name": "Get Weather",
                "description": "Retrieve the current weather for a city",
                "tags": ["weather"]
              }
            ],
            "defaultInputModes": ["text"],
            "defaultOutputModes": ["text"]
            """;

    private final OpenApiAgentCardAssembler assembler = new OpenApiAgentCardAssembler();

    @Test
    public void noExtension_returnsNull() throws IOException {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Weather API", "version": "1.0.0" },
                  "paths": {}
                }
                """;
        assertNull(assembler.assemble(json(openApi)));
    }

    @Test
    public void nullExtension_returnsNull() throws IOException {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Weather API", "version": "1.0.0", "x-agent-card": null },
                  "paths": {}
                }
                """;
        assertNull(assembler.assemble(json(openApi)));
    }

    @Test
    public void extensionNotAnObject_throwsWithContextPointingAtExtension() {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": { "title": "Weather API", "version": "1.0.0", "x-agent-card": "not an object" },
                  "paths": {}
                }
                """;
        RuleViolationException e = assertThrows(RuleViolationException.class,
                () -> assembler.assemble(json(openApi)));
        assertEquals(1, e.getCauses().size());
        assertEquals("/info/x-agent-card", e.getCauses().iterator().next().getContext());
    }

    @Test
    public void completeCard_assembledUnchanged() throws IOException {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "version": "1.0.0",
                    "x-agent-card": {
                      "name": "Weather Agent",
                      "description": "Provides weather forecasts",
                      "version": "2.0.0",
                      "supportedInterfaces": [
                        { "url": "https://weather-agent.example.com", "protocolBinding": "http+json",
                          "protocolVersion": "1.0" }
                      ],
                      %s
                    }
                  },
                  "servers": [ { "url": "https://weather.example.com" } ],
                  "paths": {}
                }
                """.formatted(FULL_SKILLS_BLOCK);
        String assembled = assembler.assemble(json(openApi));
        JsonNode node = MAPPER.readTree(assembled);
        // Explicit values in the extension are preserved, not overwritten by info.*/servers[].
        assertEquals("Weather Agent", node.get("name").asText());
        assertEquals("2.0.0", node.get("version").asText());
        assertEquals("https://weather-agent.example.com",
                node.get("supportedInterfaces").get(0).get("url").asText());
    }

    @Test
    public void missingNameDescriptionVersion_derivedFromInfo() throws IOException {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "description": "A weather service",
                    "version": "1.0.0",
                    "x-agent-card": { %s }
                  },
                  "servers": [ { "url": "https://weather.example.com" } ],
                  "paths": {}
                }
                """.formatted(FULL_SKILLS_BLOCK);
        String assembled = assembler.assemble(json(openApi));
        JsonNode node = MAPPER.readTree(assembled);
        assertEquals("Weather API", node.get("name").asText());
        assertEquals("A weather service", node.get("description").asText());
        assertEquals("1.0.0", node.get("version").asText());
    }

    @Test
    public void missingSupportedInterfaces_derivedFromServers() throws IOException {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "description": "A weather service",
                    "version": "1.0.0",
                    "x-agent-card": { %s }
                  },
                  "servers": [ { "url": "https://weather.example.com" }, { "url": "https://weather2.example.com" } ],
                  "paths": {}
                }
                """.formatted(FULL_SKILLS_BLOCK);
        String assembled = assembler.assemble(json(openApi));
        JsonNode node = MAPPER.readTree(assembled);
        JsonNode interfaces = node.get("supportedInterfaces");
        assertEquals(2, interfaces.size());
        assertEquals("https://weather.example.com", interfaces.get(0).get("url").asText());
        assertEquals("http+json", interfaces.get(0).get("protocolBinding").asText());
        assertEquals("1.0", interfaces.get(0).get("protocolVersion").asText());
        assertEquals("https://weather2.example.com", interfaces.get(1).get("url").asText());
    }

    @Test
    public void derivedInterfaces_useCardProtocolVersionWhenPresent() throws IOException {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "description": "A weather service",
                    "version": "1.0.0",
                    "x-agent-card": { "protocolVersion": "1.1", %s }
                  },
                  "servers": [ { "url": "https://weather.example.com" } ],
                  "paths": {}
                }
                """.formatted(FULL_SKILLS_BLOCK);
        String assembled = assembler.assemble(json(openApi));
        JsonNode node = MAPPER.readTree(assembled);
        assertEquals("1.1", node.get("supportedInterfaces").get(0).get("protocolVersion").asText());
    }

    @Test
    public void noSupportedInterfacesAndNoServers_failsValidation() {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "version": "1.0.0",
                    "x-agent-card": { %s }
                  },
                  "paths": {}
                }
                """.formatted(FULL_SKILLS_BLOCK);
        RuleViolationException e = assertThrows(RuleViolationException.class,
                () -> assembler.assemble(json(openApi)));
        assertTrue(e.getCauses().stream().anyMatch(v -> v.getContext().startsWith("/info/x-agent-card")));
    }

    @Test
    public void missingSkills_failsValidationWithRemappedContext() {
        String openApi = """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "version": "1.0.0",
                    "x-agent-card": { "capabilities": {}, "defaultInputModes": ["text"], "defaultOutputModes": ["text"] }
                  },
                  "servers": [ { "url": "https://weather.example.com" } ],
                  "paths": {}
                }
                """;
        RuleViolationException e = assertThrows(RuleViolationException.class,
                () -> assembler.assemble(json(openApi)));
        assertTrue(e.getCauses().stream().anyMatch(v -> v.getContext().startsWith("/info/x-agent-card")),
                "Expected at least one violation context prefixed with /info/x-agent-card, got: "
                        + e.getCauses());
    }

    @Test
    public void yamlOpenApi_extensionStillFound() throws IOException {
        String openApiYaml = """
                openapi: "3.0.0"
                info:
                  title: Weather API
                  version: "1.0.0"
                  x-agent-card:
                    name: Weather Agent
                    description: Provides weather forecasts
                    version: "2.0.0"
                    capabilities: {}
                    skills:
                      - id: get-weather
                        name: Get Weather
                        description: Retrieve the current weather for a city
                        tags: [weather]
                    defaultInputModes: [text]
                    defaultOutputModes: [text]
                servers:
                  - url: https://weather.example.com
                paths: {}
                """;
        TypedContent yamlContent = TypedContent.create(openApiYaml, ContentTypes.APPLICATION_YAML);
        String assembled = assembler.assemble(yamlContent);
        JsonNode node = MAPPER.readTree(assembled);
        assertEquals("Weather Agent", node.get("name").asText());
        assertEquals(1, node.get("supportedInterfaces").size());
    }

    private TypedContent json(String content) {
        return TypedContent.create(content, ContentTypes.APPLICATION_JSON);
    }
}
