package io.apicurio.registry.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;

/**
 * Extracts searchable labels from Agent Card content.
 * These labels are stored with the artifact and enable capability-based search.
 */
@ApplicationScoped
public class AgentCardLabelExtractor {

    public static final String LABEL_SKILL_PREFIX = "a2a.skill.";
    public static final String LABEL_CAPABILITY_PREFIX = "a2a.capability.";
    public static final String LABEL_INPUT_MODE_PREFIX = "a2a.inputMode.";
    public static final String LABEL_OUTPUT_MODE_PREFIX = "a2a.outputMode.";
    public static final String LABEL_AGENT_NAME = "a2a.name";
    public static final String LABEL_AGENT_URL = "a2a.url";

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extracts labels from agent card JSON content.
     *
     * @param content the agent card JSON content
     * @return a map of labels to be stored with the artifact
     */
    public Map<String, String> extractLabels(ContentHandle content) {
        Map<String, String> labels = new HashMap<>();

        try {
            JsonNode root = mapper.readTree(content.content());

            // Extract name
            if (root.has("name") && root.get("name").isTextual()) {
                labels.put(LABEL_AGENT_NAME, root.get("name").asText());
            }

            // Extract URL
            if (root.has("url") && root.get("url").isTextual()) {
                labels.put(LABEL_AGENT_URL, root.get("url").asText());
            }

            // Extract capabilities
            if (root.has("capabilities") && root.get("capabilities").isObject()) {
                JsonNode capabilities = root.get("capabilities");

                if (capabilities.has("streaming")) {
                    labels.put(LABEL_CAPABILITY_PREFIX + "streaming",
                            String.valueOf(capabilities.get("streaming").asBoolean(false)));
                }
                if (capabilities.has("pushNotifications")) {
                    labels.put(LABEL_CAPABILITY_PREFIX + "pushNotifications",
                            String.valueOf(capabilities.get("pushNotifications").asBoolean(false)));
                }
            }

            // Extract skills
            if (root.has("skills") && root.get("skills").isArray()) {
                for (JsonNode skill : root.get("skills")) {
                    if (skill.has("id") && skill.get("id").isTextual()) {
                        String skillId = skill.get("id").asText();
                        String skillName = skill.has("name") ? skill.get("name").asText() : skillId;
                        labels.put(LABEL_SKILL_PREFIX + skillId, skillName);
                    }
                }
            }

            // Extract default input modes
            if (root.has("defaultInputModes") && root.get("defaultInputModes").isArray()) {
                for (JsonNode mode : root.get("defaultInputModes")) {
                    if (mode.isTextual()) {
                        labels.put(LABEL_INPUT_MODE_PREFIX + mode.asText(), "true");
                    }
                }
            }

            // Extract default output modes
            if (root.has("defaultOutputModes") && root.get("defaultOutputModes").isArray()) {
                for (JsonNode mode : root.get("defaultOutputModes")) {
                    if (mode.isTextual()) {
                        labels.put(LABEL_OUTPUT_MODE_PREFIX + mode.asText(), "true");
                    }
                }
            }

        } catch (Exception e) {
            // If parsing fails, return empty labels - validation will catch invalid content
        }

        return labels;
    }
}
