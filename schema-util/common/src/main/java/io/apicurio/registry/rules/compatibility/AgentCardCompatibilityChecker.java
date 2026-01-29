package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility checker for A2A Agent Card artifacts.
 *
 * Compatibility rules for Agent Cards:
 * - Adding new skills: Always compatible
 * - Removing skills: Backward incompatible
 * - Adding capabilities: Always compatible
 * - Removing/disabling capabilities: Backward incompatible
 * - Changing URL: Incompatible (both directions)
 * - Adding authentication schemes: Always compatible
 * - Removing authentication schemes: Backward incompatible
 * - Adding input/output modes: Always compatible
 * - Removing input/output modes: Backward incompatible
 */
public class AgentCardCompatibilityChecker extends AbstractCompatibilityChecker<AgentCardCompatibilityDifference> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected Set<AgentCardCompatibilityDifference> isBackwardsCompatibleWith(String existing, String proposed,
            Map<String, TypedContent> resolvedReferences) {
        Set<AgentCardCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = mapper.readTree(existing);
            JsonNode proposedNode = mapper.readTree(proposed);

            // Check URL changes (breaking in both directions)
            checkUrlCompatibility(existingNode, proposedNode, differences);

            // Check removed skills
            checkSkillRemovals(existingNode, proposedNode, differences);

            // Check removed or disabled capabilities
            checkCapabilityRemovals(existingNode, proposedNode, differences);

            // Check removed authentication schemes
            checkAuthenticationRemovals(existingNode, proposedNode, differences);

            // Check removed input modes
            checkModeRemovals(existingNode, proposedNode, "defaultInputModes", "input mode", differences);

            // Check removed output modes
            checkModeRemovals(existingNode, proposedNode, "defaultOutputModes", "output mode", differences);

        } catch (Exception e) {
            differences.add(new AgentCardCompatibilityDifference(
                    AgentCardCompatibilityDifference.Type.PARSE_ERROR,
                    "Failed to parse Agent Card: " + e.getMessage()));
        }

        return differences;
    }

    private void checkUrlCompatibility(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        String existingUrl = getTextValue(existing, "url");
        String proposedUrl = getTextValue(proposed, "url");

        if (existingUrl != null && proposedUrl != null && !existingUrl.equals(proposedUrl)) {
            differences.add(new AgentCardCompatibilityDifference(
                    AgentCardCompatibilityDifference.Type.URL_CHANGED,
                    "Agent URL changed from '" + existingUrl + "' to '" + proposedUrl + "'"));
        }
    }

    private void checkSkillRemovals(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        Set<String> existingSkills = extractSkillIds(existing);
        Set<String> proposedSkills = extractSkillIds(proposed);

        for (String skillId : existingSkills) {
            if (!proposedSkills.contains(skillId)) {
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.SKILL_REMOVED,
                        "Skill '" + skillId + "' was removed"));
            }
        }
    }

    private void checkCapabilityRemovals(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        JsonNode existingCaps = existing.get("capabilities");
        JsonNode proposedCaps = proposed.get("capabilities");

        if (existingCaps == null || !existingCaps.isObject()) {
            return;
        }

        Iterator<String> fieldNames = existingCaps.fieldNames();
        while (fieldNames.hasNext()) {
            String capName = fieldNames.next();
            boolean existingValue = existingCaps.get(capName).asBoolean(false);

            if (existingValue) {
                // Check if capability was removed or disabled
                boolean proposedValue = false;
                if (proposedCaps != null && proposedCaps.has(capName)) {
                    proposedValue = proposedCaps.get(capName).asBoolean(false);
                }

                if (!proposedValue) {
                    differences.add(new AgentCardCompatibilityDifference(
                            AgentCardCompatibilityDifference.Type.CAPABILITY_REMOVED,
                            "Capability '" + capName + "' was removed or disabled"));
                }
            }
        }
    }

    private void checkAuthenticationRemovals(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        Set<String> existingSchemes = extractAuthSchemes(existing);
        Set<String> proposedSchemes = extractAuthSchemes(proposed);

        for (String scheme : existingSchemes) {
            if (!proposedSchemes.contains(scheme)) {
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.AUTH_SCHEME_REMOVED,
                        "Authentication scheme '" + scheme + "' was removed"));
            }
        }
    }

    private void checkModeRemovals(JsonNode existing, JsonNode proposed, String fieldName,
            String modeType, Set<AgentCardCompatibilityDifference> differences) {
        Set<String> existingModes = extractStringArray(existing, fieldName);
        Set<String> proposedModes = extractStringArray(proposed, fieldName);

        for (String mode : existingModes) {
            if (!proposedModes.contains(mode)) {
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.MODE_REMOVED,
                        "The " + modeType + " '" + mode + "' was removed"));
            }
        }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && field.isTextual()) ? field.asText() : null;
    }

    private Set<String> extractSkillIds(JsonNode node) {
        Set<String> skills = new HashSet<>();
        JsonNode skillsNode = node.get("skills");
        if (skillsNode != null && skillsNode.isArray()) {
            for (JsonNode skill : skillsNode) {
                JsonNode idNode = skill.get("id");
                if (idNode != null && idNode.isTextual()) {
                    skills.add(idNode.asText());
                }
            }
        }
        return skills;
    }

    private Set<String> extractAuthSchemes(JsonNode node) {
        Set<String> schemes = new HashSet<>();
        JsonNode authNode = node.get("authentication");
        if (authNode != null && authNode.isObject()) {
            JsonNode schemesNode = authNode.get("schemes");
            if (schemesNode != null && schemesNode.isArray()) {
                for (JsonNode scheme : schemesNode) {
                    if (scheme.isTextual()) {
                        schemes.add(scheme.asText());
                    }
                }
            }
        }
        return schemes;
    }

    private Set<String> extractStringArray(JsonNode node, String fieldName) {
        Set<String> values = new HashSet<>();
        JsonNode arrayNode = node.get(fieldName);
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    @Override
    protected CompatibilityDifference transform(AgentCardCompatibilityDifference original) {
        return original;
    }
}
