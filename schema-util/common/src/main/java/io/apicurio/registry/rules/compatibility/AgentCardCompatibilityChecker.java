package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.TypedContent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
 * - Adding capability extensions: Always compatible
 * - Removing capability extensions (by uri): Backward incompatible
 * - Removing interfaces (by url+protocolBinding): Backward incompatible
 * - Changing protocolVersion on existing interface: Backward incompatible
 * - Removing protocolVersion from an existing interface: Backward incompatible
 * - Changing the card-level protocolVersion: Backward incompatible
 * - Adding a card-level protocolVersion where there was none: Always compatible
 * - Adding security schemes: Always compatible
 * - Removing security schemes: Backward incompatible
 * - Changing an existing security scheme's definition: Backward incompatible
 * - Changing an existing security scheme's description: Always compatible
 * - Adding input/output modes: Always compatible
 * - Removing input/output modes: Backward incompatible
 */
public class AgentCardCompatibilityChecker
        extends AbstractCompatibilityChecker<AgentCardCompatibilityDifference> {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Security scheme fields whose value defines how a client must authenticate. The description
     * field is documentation, and fields outside this list are ignored on purpose: the schema
     * permits vendor extensions, and churn in one of those must not fail a publish.
     */
    private static final List<String> SECURITY_SCHEME_FIELDS = List.of("type", "scheme",
            "bearerFormat", "name", "location", "oauth2MetadataUrl", "openIdConnectUrl");

    private static final String FLOWS_FIELD = "flows";

    @Override
    protected Set<AgentCardCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        Set<AgentCardCompatibilityDifference> differences = new HashSet<>();

        try {
            JsonNode existingNode = mapper.readTree(existing);
            JsonNode proposedNode = mapper.readTree(proposed);

            checkInterfaceCompatibility(existingNode, proposedNode, differences);
            checkSkillRemovals(existingNode, proposedNode, differences);
            checkCapabilityRemovals(existingNode, proposedNode, differences);
            checkSecuritySchemeRemovals(existingNode, proposedNode, differences);
            checkSecuritySchemeDefinitionChanges(existingNode, proposedNode, differences);
            checkCardProtocolVersionChange(existingNode, proposedNode, differences);
            checkModeRemovals(existingNode, proposedNode, "defaultInputModes", "input mode",
                    differences);
            checkModeRemovals(existingNode, proposedNode, "defaultOutputModes", "output mode",
                    differences);

        } catch (Exception e) {
            differences.add(new AgentCardCompatibilityDifference(
                    AgentCardCompatibilityDifference.Type.PARSE_ERROR,
                    "Failed to parse Agent Card: " + e.getMessage()));
        }

        return differences;
    }

    private void checkInterfaceCompatibility(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        Set<String> existingKeys = extractInterfaceKeys(existing);
        Set<String> proposedKeys = extractInterfaceKeys(proposed);

        for (String key : existingKeys) {
            if (!proposedKeys.contains(key)) {
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.INTERFACE_REMOVED,
                        "Interface '" + key + "' was removed"));
            }
        }

        checkInterfaceProtocolVersionChanges(existing, proposed, differences);
    }

    private void checkInterfaceProtocolVersionChanges(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
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

                if (!url.equals(pUrl) || !binding.equals(pBinding)) {
                    continue;
                }

                // A retained interface that drops its protocolVersion withdraws a guarantee the
                // client had, so it is treated the same as changing it. The validity rule requires
                // the field, but that rule is opt-in, so the card can reach this checker without it.
                if (pVersion == null) {
                    differences.add(new AgentCardCompatibilityDifference(
                            AgentCardCompatibilityDifference.Type.PROTOCOL_VERSION_REMOVED,
                            "Protocol version '" + existingVersion + "' was removed from interface "
                                    + url + " (" + binding + ")"));
                } else if (!existingVersion.equals(pVersion)) {
                    differences.add(new AgentCardCompatibilityDifference(
                            AgentCardCompatibilityDifference.Type.PROTOCOL_VERSION_CHANGED,
                            "Protocol version changed from '" + existingVersion + "' to '"
                                    + pVersion + "' for interface " + url + " (" + binding + ")"));
                }
            }
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
                    differences.add(new AgentCardCompatibilityDifference(
                            AgentCardCompatibilityDifference.Type.CAPABILITY_REMOVED,
                            "Capability '" + capName + "' was removed or disabled"));
                }
            }
        }

        checkCapabilityExtensionRemovals(existingCaps, proposedCaps, differences);
    }

    /**
     * capabilities.extensions is an array, so the boolean scan above skips it. Extensions are
     * identified by uri.
     */
    private void checkCapabilityExtensionRemovals(JsonNode existingCaps, JsonNode proposedCaps,
            Set<AgentCardCompatibilityDifference> differences) {
        Set<String> existingUris = extractExtensionUris(existingCaps);
        Set<String> proposedUris = extractExtensionUris(proposedCaps);

        for (String uri : existingUris) {
            if (!proposedUris.contains(uri)) {
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.CAPABILITY_EXTENSION_REMOVED,
                        "Capability extension '" + uri + "' was removed"));
            }
        }
    }

    private void checkSecuritySchemeRemovals(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        Set<String> existingSchemes = extractSecuritySchemeNames(existing);
        Set<String> proposedSchemes = extractSecuritySchemeNames(proposed);

        for (String scheme : existingSchemes) {
            if (!proposedSchemes.contains(scheme)) {
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.SECURITY_SCHEME_REMOVED,
                        "Security scheme '" + scheme + "' was removed"));
            }
        }
    }

    /**
     * A retained scheme name says nothing about how a client authenticates: the definition behind
     * it can change completely. checkSecuritySchemeRemovals only diffs the names, so compare the
     * fields that carry the meaning.
     */
    private void checkSecuritySchemeDefinitionChanges(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        JsonNode existingSchemes = existing.get("securitySchemes");
        JsonNode proposedSchemes = proposed.get("securitySchemes");

        if (existingSchemes == null || !existingSchemes.isObject() || proposedSchemes == null
                || !proposedSchemes.isObject()) {
            return;
        }

        Iterator<String> schemeNames = existingSchemes.fieldNames();
        while (schemeNames.hasNext()) {
            String schemeName = schemeNames.next();
            JsonNode existingScheme = existingSchemes.get(schemeName);
            JsonNode proposedScheme = proposedSchemes.get(schemeName);

            // A scheme that is gone entirely is already reported as a removal, and one the
            // existing card never defined as an object promised nothing to take away.
            if (proposedScheme == null || !existingScheme.isObject()) {
                continue;
            }

            if (!proposedScheme.isObject()) {
                // The name survives, so checkSecuritySchemeRemovals stays quiet, but the
                // definition behind it is gone. The validity rule rejects this, and it is opt-in.
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.SECURITY_SCHEME_CHANGED,
                        "Security scheme '" + schemeName + "' is no longer defined as an object"));
                continue;
            }

            for (String field : SECURITY_SCHEME_FIELDS) {
                String existingValue = getSchemeFieldValue(existingScheme, field);
                if (existingValue == null) {
                    // Nothing was promised, so whatever the proposed card says cannot break a
                    // client that was written against the existing one.
                    continue;
                }
                String proposedValue = getSchemeFieldValue(proposedScheme, field);
                if (!existingValue.equals(proposedValue)) {
                    differences.add(new AgentCardCompatibilityDifference(
                            AgentCardCompatibilityDifference.Type.SECURITY_SCHEME_CHANGED,
                            describeSchemeFieldChange(schemeName, field, existingValue,
                                    proposedValue)));
                }
            }

            JsonNode existingFlows = existingScheme.get(FLOWS_FIELD);
            if (existingFlows != null && !existingFlows.isNull()) {
                checkRetainedValues(schemeName, FLOWS_FIELD, existingFlows,
                        proposedScheme.get(FLOWS_FIELD), differences);
            }
        }
    }

    /**
     * flows is a free-form object in the schema and the validity rule does not inspect it, so
     * compare it structurally rather than by known field names: whatever the existing card
     * declared must still be there, unchanged. Additions - a new grant type, a new scope - are
     * ignored, so they stay compatible. Reports the outermost point at which the two stop
     * matching rather than every leaf beneath it, which keeps the number of differences
     * proportional to the edit.
     */
    private void checkRetainedValues(String schemeName, String path, JsonNode existing,
            JsonNode proposed, Set<AgentCardCompatibilityDifference> differences) {
        if (proposed == null) {
            differences.add(new AgentCardCompatibilityDifference(
                    AgentCardCompatibilityDifference.Type.SECURITY_SCHEME_CHANGED,
                    "Security scheme '" + schemeName + "' no longer declares '" + path + "'"));
            return;
        }

        if (existing.isObject() && proposed.isObject()) {
            Iterator<String> fieldNames = existing.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                checkRetainedValues(schemeName, path + "/" + fieldName, existing.get(fieldName),
                        proposed.get(fieldName), differences);
            }
            return;
        }

        if (existing.isArray() && proposed.isArray()) {
            checkRetainedArrayValues(schemeName, path, existing, proposed, differences);
            return;
        }

        if (!existing.equals(proposed)) {
            differences.add(new AgentCardCompatibilityDifference(
                    AgentCardCompatibilityDifference.Type.SECURITY_SCHEME_CHANGED,
                    describeSchemeFieldChange(schemeName, path, renderValue(existing),
                            renderValue(proposed))));
        }
    }

    /**
     * An array inside flows is a set of offered values - a scope list is the usual case - so only
     * losing an entry is a break. This mirrors how checkModeRemovals treats defaultInputModes:
     * comparing the arrays whole would report a purely additive edit as incompatible.
     */
    private void checkRetainedArrayValues(String schemeName, String path, JsonNode existing,
            JsonNode proposed, Set<AgentCardCompatibilityDifference> differences) {
        for (JsonNode existingItem : existing) {
            boolean retained = false;
            for (JsonNode proposedItem : proposed) {
                if (existingItem.equals(proposedItem)) {
                    retained = true;
                    break;
                }
            }
            if (!retained) {
                differences.add(new AgentCardCompatibilityDifference(
                        AgentCardCompatibilityDifference.Type.SECURITY_SCHEME_CHANGED,
                        "Security scheme '" + schemeName + "' no longer offers '"
                                + renderValue(existingItem) + "' in '" + path + "'"));
            }
        }
    }

    /**
     * The card-level protocolVersion is the version the agent as a whole speaks -
     * RegistryAgentCardBuilder fills it from the same config value as the per-interface one - so a
     * client can rely on it without reading supportedInterfaces. It is optional, so an existing
     * card that did not declare one promised nothing that a proposed card could take away.
     */
    private void checkCardProtocolVersionChange(JsonNode existing, JsonNode proposed,
            Set<AgentCardCompatibilityDifference> differences) {
        String existingVersion = getTextValue(existing, "protocolVersion");
        if (existingVersion == null) {
            return;
        }

        String proposedVersion = getTextValue(proposed, "protocolVersion");
        if (existingVersion.equals(proposedVersion)) {
            return;
        }

        differences.add(new AgentCardCompatibilityDifference(
                AgentCardCompatibilityDifference.Type.CARD_PROTOCOL_VERSION_CHANGED,
                "The agent protocolVersion " + describeChange(existingVersion, proposedVersion)));
    }

    private String describeSchemeFieldChange(String schemeName, String field, String existingValue,
            String proposedValue) {
        return "Security scheme '" + schemeName + "' field '" + field + "' "
                + describeChange(existingValue, proposedValue);
    }

    private String describeChange(String existingValue, String proposedValue) {
        return proposedValue == null
                ? "was removed (was '" + existingValue + "')"
                : "changed from '" + existingValue + "' to '" + proposedValue + "'";
    }

    /**
     * Unlike protocolVersion, which the JSON schema types as a string, security scheme fields are
     * never type-checked: AgentCardContentValidator only asserts that a scheme is an object. So
     * compare whatever value is there rather than textual values alone, or a field turning into a
     * number would be reported as though it had been removed.
     */
    private String getSchemeFieldValue(JsonNode scheme, String fieldName) {
        JsonNode field = scheme.get(fieldName);
        return (field == null || field.isNull()) ? null : renderValue(field);
    }

    private String renderValue(JsonNode node) {
        return node.isTextual() ? node.asText() : node.toString();
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

    private Set<String> extractExtensionUris(JsonNode capabilities) {
        Set<String> uris = new HashSet<>();
        if (capabilities == null) {
            return uris;
        }
        JsonNode extensions = capabilities.get("extensions");
        if (extensions != null && extensions.isArray()) {
            for (JsonNode extension : extensions) {
                // getTextValue already drops a missing or non-textual uri. A blank one is
                // dropped too: it is not a usable identity, and tracking it would report a
                // removal of extension '' when the entry disappears.
                String uri = getTextValue(extension, "uri");
                if (uri != null && !uri.isBlank()) {
                    uris.add(uri);
                }
            }
        }
        return uris;
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
