package io.apicurio.registry.agents.content.extract;

import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.content.ContentHandle;

/**
 * Extracts structured elements from A2A Agent Card content for search indexing. Parses the Agent Card JSON
 * and extracts skills, capabilities, protocol bindings, security schemes, tags, and modes.
 */
public class AgentCardStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(AgentCardStructuredContentExtractor.class);

    private static final String PROTOCOL_BINDING = "protocolBinding";
    private static final String PROTOCOL_VERSION = "protocolVersion";

    private final ObjectMapper objectMapper = new JsonMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractSkills(root, elements);
            extractCapabilities(root, elements);
            extractInputModes(root, elements);
            extractOutputModes(root, elements);
            extractProtocolBindings(root, elements);
            extractSecuritySchemeTypes(root, elements);
            extractTags(root, elements);
            extractProtocolVersion(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from Agent Card: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void extractSkills(JsonNode root, List<StructuredElement> elements) {
        JsonNode skills = root.path("skills");
        if (!skills.isMissingNode() && skills.isArray()) {
            for (JsonNode skill : skills) {
                if (skill.has("id") && skill.get("id").isString()) {
                    elements.add(new StructuredElement("skill", skill.get("id").asString()));
                }
            }
        }
    }

    private void extractCapabilities(JsonNode root, List<StructuredElement> elements) {
        JsonNode capabilities = root.path("capabilities");
        if (!capabilities.isMissingNode() && capabilities.isObject()) {
            capabilities.properties().iterator().forEachRemaining(entry -> {
                if (entry.getValue().isBoolean() && entry.getValue().asBoolean()) {
                    elements.add(new StructuredElement("capability", entry.getKey()));
                }
            });
        }
    }

    private void extractInputModes(JsonNode root, List<StructuredElement> elements) {
        JsonNode inputModes = root.path("defaultInputModes");
        if (!inputModes.isMissingNode() && inputModes.isArray()) {
            for (JsonNode mode : inputModes) {
                if (mode.isString()) {
                    elements.add(new StructuredElement("inputmode", mode.asString()));
                }
            }
        }
    }

    private void extractOutputModes(JsonNode root, List<StructuredElement> elements) {
        JsonNode outputModes = root.path("defaultOutputModes");
        if (!outputModes.isMissingNode() && outputModes.isArray()) {
            for (JsonNode mode : outputModes) {
                if (mode.isString()) {
                    elements.add(new StructuredElement("outputmode", mode.asString()));
                }
            }
        }
    }

    private void extractProtocolBindings(JsonNode root, List<StructuredElement> elements) {
        JsonNode interfaces = root.path("supportedInterfaces");
        if (!interfaces.isMissingNode() && interfaces.isArray()) {
            for (JsonNode iface : interfaces) {
                addTextualField(iface, PROTOCOL_BINDING, "protocolbinding", elements);
                addTextualField(iface, PROTOCOL_VERSION, "protocolversion", elements);
                addTextualField(iface, "url", "url", elements);
            }
        }
    }

    private void addTextualField(JsonNode node, String field, String elementKind,
            List<StructuredElement> elements) {
        if (node.has(field) && node.get(field).isString()) {
            elements.add(new StructuredElement(elementKind, node.get(field).asString()));
        }
    }

    private void extractSecuritySchemeTypes(JsonNode root, List<StructuredElement> elements) {
        JsonNode schemes = root.path("securitySchemes");
        if (!schemes.isMissingNode() && schemes.isObject()) {
            schemes.propertyNames().iterator().forEachRemaining(name ->
                    elements.add(new StructuredElement("securityscheme", name)));
        }
    }

    private void extractProtocolVersion(JsonNode root, List<StructuredElement> elements) {
        JsonNode version = root.path(PROTOCOL_VERSION);
        if (!version.isMissingNode() && version.isString()) {
            elements.add(new StructuredElement("protocolversion", version.asString()));
        }
    }

    private void extractTags(JsonNode root, List<StructuredElement> elements) {
        JsonNode skills = root.path("skills");
        if (!skills.isMissingNode() && skills.isArray()) {
            Set<String> seen = new HashSet<>();
            for (JsonNode skill : skills) {
                extractSkillTags(skill, seen, elements);
            }
        }
    }

    private void extractSkillTags(JsonNode skill, Set<String> seen, List<StructuredElement> elements) {
        JsonNode tags = skill.path("tags");
        if (!tags.isMissingNode() && tags.isArray()) {
            for (JsonNode tag : tags) {
                if (tag.isString() && seen.add(tag.asString())) {
                    elements.add(new StructuredElement("tag", tag.asString()));
                }
            }
        }
    }
}
