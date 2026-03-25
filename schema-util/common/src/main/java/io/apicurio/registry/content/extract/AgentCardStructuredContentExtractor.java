package io.apicurio.registry.content.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;

/**
 * Extracts structured elements from A2A Agent Card content for search indexing. Parses the Agent Card JSON
 * and extracts skills, capabilities, input modes, and output modes as structured elements.
 */
public class AgentCardStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(AgentCardStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractSkills(root, elements);
            extractCapabilities(root, elements);
            extractInputModes(root, elements);
            extractOutputModes(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from Agent Card: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts skill IDs from the skills array.
     */
    private void extractSkills(JsonNode root, List<StructuredElement> elements) {
        JsonNode skills = root.path("skills");
        if (!skills.isMissingNode() && skills.isArray()) {
            for (JsonNode skill : skills) {
                if (skill.has("id") && skill.get("id").isTextual()) {
                    elements.add(new StructuredElement("skill", skill.get("id").asText()));
                }
            }
        }
    }

    /**
     * Extracts capability names where the capability value is true.
     */
    private void extractCapabilities(JsonNode root, List<StructuredElement> elements) {
        JsonNode capabilities = root.path("capabilities");
        if (!capabilities.isMissingNode() && capabilities.isObject()) {
            capabilities.fields().forEachRemaining(entry -> {
                if (entry.getValue().isBoolean() && entry.getValue().asBoolean()) {
                    elements.add(new StructuredElement("capability", entry.getKey()));
                }
            });
        }
    }

    /**
     * Extracts values from the defaultInputModes array.
     */
    private void extractInputModes(JsonNode root, List<StructuredElement> elements) {
        JsonNode inputModes = root.path("defaultInputModes");
        if (!inputModes.isMissingNode() && inputModes.isArray()) {
            for (JsonNode mode : inputModes) {
                if (mode.isTextual()) {
                    elements.add(new StructuredElement("inputmode", mode.asText()));
                }
            }
        }
    }

    /**
     * Extracts values from the defaultOutputModes array.
     */
    private void extractOutputModes(JsonNode root, List<StructuredElement> elements) {
        JsonNode outputModes = root.path("defaultOutputModes");
        if (!outputModes.isMissingNode() && outputModes.isArray()) {
            for (JsonNode mode : outputModes) {
                if (mode.isTextual()) {
                    elements.add(new StructuredElement("outputmode", mode.asText()));
                }
            }
        }
    }
}
