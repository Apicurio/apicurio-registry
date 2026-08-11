package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Compatibility checker for A2A Agent Card artifacts (v1.0).
 *
 * Compatibility rules:
 * - Adding new skills: Always compatible
 * - Removing skills: Backward incompatible
 * - Adding capabilities: Always compatible
 * - Removing/disabling capabilities: Backward incompatible
 * - Removing interfaces (by url+protocolBinding): Backward incompatible
 * - Changing protocolVersion on existing interface: Backward incompatible
 * - Adding security schemes: Always compatible
 * - Removing security schemes: Backward incompatible
 * - Adding input/output modes: Always compatible
 * - Removing input/output modes: Backward incompatible
 */
public class AgentCardCompatibilityChecker
        extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final String CONTEXT_INTERFACES = "/supportedInterfaces";
    private static final String CONTEXT_SKILLS = "/skills";
    private static final String CONTEXT_CAPABILITIES = "/capabilities";
    private static final String CONTEXT_SECURITY_SCHEMES = "/securitySchemes";
    private static final String CONTEXT_MODES = "/modes";
    private static final String CONTEXT_DOCUMENT = "/document";

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        Set<SimpleCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = mapper.readTree(existing);
            JsonNode proposedNode = mapper.readTree(proposed);

            checkInterfaceCompatibility(existingNode, proposedNode, differences);
            checkSkillRemovals(existingNode, proposedNode, differences);
            checkCapabilityRemovals(existingNode, proposedNode, differences);
            checkSecuritySchemeRemovals(existingNode, proposedNode, differences);
            checkModeRemovals(existingNode, proposedNode, "defaultInputModes", "input mode",
                    differences);
            checkModeRemovals(existingNode, proposedNode, "defaultOutputModes", "output mode",
                    differences);

        } catch (Exception e) {
            differences.add(new SimpleCompatibilityDifference(
                    "Failed to parse Agent Card: " + e.getMessage(), CONTEXT_DOCUMENT));
        }

        return differences;
    }

    private void checkInterfaceCompatibility(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingKeys = extractInterfaceKeys(existing);
        Set<String> proposedKeys = extractInterfaceKeys(proposed);

        for (String key : existingKeys) {
            if (!proposedKeys.contains(key)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Interface '" + key + "' was removed", CONTEXT_INTERFACES));
            }
        }

        checkInterfaceProtocolVersionChanges(existing, proposed, differences);
    }

    private void checkInterfaceProtocolVersionChanges(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        JsonNode existingInterfaces = existing.get("supportedInterfaces");
        JsonNode proposedInterfaces = proposed.get("supportedInterfaces");

        if (existingInterfaces == null || proposedInterfaces == null) {
            return;
        }

        for (JsonNode existingIface : existingInterfaces) {
            String url = getTextValue(existingIface, "url");
            String binding = getTextValue(existingIface, "protocolBinding");
            String existingVersion = getTextValue(existingIface, "protocolVersion");

            if (url == null || binding == null || existingVersion == null) {
                continue;
            }

            for (JsonNode proposedIface : proposedInterfaces) {
                String pUrl = getTextValue(proposedIface, "url");
                String pBinding = getTextValue(proposedIface, "protocolBinding");
                String pVersion = getTextValue(proposedIface, "protocolVersion");

                if (url.equals(pUrl) && binding.equals(pBinding)
                        && pVersion != null && !existingVersion.equals(pVersion)) {
                    differences.add(new SimpleCompatibilityDifference(
                            "Protocol version changed from '" + existingVersion + "' to '"
                                    + pVersion + "' for interface " + url + " (" + binding + ")",
                            CONTEXT_INTERFACES));
                }
            }
        }
    }

    private void checkSkillRemovals(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingSkills = extractSkillIds(existing);
        Set<String> proposedSkills = extractSkillIds(proposed);

        for (String skillId : existingSkills) {
            if (!proposedSkills.contains(skillId)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Skill '" + skillId + "' was removed", CONTEXT_SKILLS));
            }
        }
    }

    private void checkCapabilityRemovals(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        JsonNode existingCaps = existing.get("capabilities");
        JsonNode proposedCaps = proposed.get("capabilities");

        if (existingCaps == null || !existingCaps.isObject()) {
            return;
        }

        Iterator<String> fieldNames = existingCaps.fieldNames();
        while (fieldNames.hasNext()) {
            String capName = fieldNames.next();
            if (!existingCaps.get(capName).isBoolean()) {
                continue;
            }
            boolean existingValue = existingCaps.get(capName).asBoolean(false);

            if (existingValue) {
                boolean proposedValue = false;
                if (proposedCaps != null && proposedCaps.has(capName)) {
                    proposedValue = proposedCaps.get(capName).asBoolean(false);
                }

                if (!proposedValue) {
                    differences.add(new SimpleCompatibilityDifference(
                            "Capability '" + capName + "' was removed or disabled",
                            CONTEXT_CAPABILITIES));
                }
            }
        }
    }

    private void checkSecuritySchemeRemovals(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingSchemes = extractSecuritySchemeNames(existing);
        Set<String> proposedSchemes = extractSecuritySchemeNames(proposed);

        for (String scheme : existingSchemes) {
            if (!proposedSchemes.contains(scheme)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Security scheme '" + scheme + "' was removed", CONTEXT_SECURITY_SCHEMES));
            }
        }
    }

    private void checkModeRemovals(JsonNode existing, JsonNode proposed, String fieldName,
            String modeType, Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingModes = extractStringArray(existing, fieldName);
        Set<String> proposedModes = extractStringArray(proposed, fieldName);

        for (String mode : existingModes) {
            if (!proposedModes.contains(mode)) {
                differences.add(new SimpleCompatibilityDifference(
                        "The " + modeType + " '" + mode + "' was removed", CONTEXT_MODES));
            }
        }
    }

    private String getTextValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field != null && field.isTextual()) ? field.asText() : null;
    }

    private Set<String> extractInterfaceKeys(JsonNode node) {
        Set<String> keys = new HashSet<>();
        JsonNode interfaces = node.get("supportedInterfaces");
        if (interfaces != null && interfaces.isArray()) {
            for (JsonNode iface : interfaces) {
                String url = getTextValue(iface, "url");
                String binding = getTextValue(iface, "protocolBinding");
                if (url != null && binding != null) {
                    keys.add(url + "|" + binding);
                }
            }
        }
        return keys;
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

    private Set<String> extractSecuritySchemeNames(JsonNode node) {
        Set<String> schemes = new HashSet<>();
        JsonNode schemesNode = node.get("securitySchemes");
        if (schemesNode != null && schemesNode.isObject()) {
            schemesNode.fieldNames().forEachRemaining(schemes::add);
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
}
