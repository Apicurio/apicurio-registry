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
 * - Withdrawing an OAuth2 scope: Backward incompatible
 * - Rewording an OAuth2 scope's description: Always compatible
 * - Adding input/output modes: Always compatible
 * - Removing input/output modes: Backward incompatible
 */
public class AgentCardCompatibilityChecker
        extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final String CONTEXT_INTERFACES = "/supportedInterfaces";
    private static final String CONTEXT_SKILLS = "/skills";
    private static final String CONTEXT_CAPABILITIES = "/capabilities";
    private static final String CONTEXT_CAPABILITY_EXTENSIONS = "/capabilities/extensions";
    private static final String CONTEXT_SECURITY_SCHEMES = "/securitySchemes";
    private static final String CONTEXT_PROTOCOL_VERSION = "/protocolVersion";
    private static final String CONTEXT_MODES = "/modes";
    private static final String CONTEXT_DOCUMENT = "/document";
    private static final String MSG_WAS_REMOVED = "' was removed";

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Security scheme fields whose value defines how a client must authenticate. The description
     * field is documentation, and fields outside this list are ignored on purpose: the schema
     * permits vendor extensions, and churn in one of those must not fail a publish.
     */
    private static final List<String> SECURITY_SCHEME_FIELDS = List.of("type", "scheme",
            "bearerFormat", "name", "location", "oauth2MetadataUrl", "openIdConnectUrl");

    private static final String FLOWS_FIELD = "flows";

    private static final String SCOPES_FIELD = "scopes";

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
            checkSecuritySchemeDefinitionChanges(existingNode, proposedNode, differences);
            checkCardProtocolVersionChange(existingNode, proposedNode, differences);
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
                        "Interface '" + key + MSG_WAS_REMOVED, CONTEXT_INTERFACES));
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
            String existingVersion = getComparableValue(existingIface, "protocolVersion");

            if (url == null || binding == null || existingVersion == null) {
                continue;
            }

            // Neither the schema nor validateSupportedInterfaces requires url+protocolBinding to be
            // unique, so weigh every matching entry before concluding: one of them keeping the
            // version means nothing was withdrawn, and duplicates must not multiply the difference.
            boolean matched = false;
            boolean versionRetained = false;
            String changedVersion = null;

            for (JsonNode proposedIface : proposedInterfaces) {
                if (!url.equals(getTextValue(proposedIface, "url"))
                        || !binding.equals(getTextValue(proposedIface, "protocolBinding"))) {
                    continue;
                }

                matched = true;
                String pVersion = getComparableValue(proposedIface, "protocolVersion");
                if (existingVersion.equals(pVersion)) {
                    versionRetained = true;
                    break;
                }
                if (pVersion != null) {
                    changedVersion = pVersion;
                }
            }

            // An interface with no match at all is already reported as an interface removal.
            if (!matched || versionRetained) {
                continue;
            }

            if (changedVersion != null) {
                differences.add(new SimpleCompatibilityDifference(
                        "Protocol version changed from '" + existingVersion + "' to '"
                                + changedVersion + "' for interface " + url + " (" + binding + ")",
                        CONTEXT_INTERFACES));
            } else {
                // A retained interface that drops its protocolVersion withdraws a guarantee the
                // client had. The validity rule requires the field, but it is opt-in, so the card
                // can reach this checker without it.
                differences.add(new SimpleCompatibilityDifference(
                        "Protocol version '" + existingVersion + "' was removed from interface "
                                + url + " (" + binding + ")", CONTEXT_INTERFACES));
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
                        "Skill '" + skillId + MSG_WAS_REMOVED, CONTEXT_SKILLS));
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

        checkCapabilityExtensionRemovals(existingCaps, proposedCaps, differences);
    }

    /**
     * capabilities.extensions is an array, so the boolean scan above skips it. Extensions are
     * identified by uri.
     */
    private void checkCapabilityExtensionRemovals(JsonNode existingCaps, JsonNode proposedCaps,
            Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingUris = extractExtensionUris(existingCaps);
        Set<String> proposedUris = extractExtensionUris(proposedCaps);

        for (String uri : existingUris) {
            if (!proposedUris.contains(uri)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Capability extension '" + uri + MSG_WAS_REMOVED,
                        CONTEXT_CAPABILITY_EXTENSIONS));
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
                        "Security scheme '" + scheme + MSG_WAS_REMOVED, CONTEXT_SECURITY_SCHEMES));
            }
        }
    }

    /**
     * A retained scheme name says nothing about how a client authenticates: the definition behind
     * it can change completely. checkSecuritySchemeRemovals only diffs the names, so compare the
     * fields that carry the meaning.
     */
    private void checkSecuritySchemeDefinitionChanges(JsonNode existing, JsonNode proposed,
            Set<SimpleCompatibilityDifference> differences) {
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
                differences.add(new SimpleCompatibilityDifference(
                        "Security scheme '" + schemeName + "' is no longer defined as an object",
                        CONTEXT_SECURITY_SCHEMES));
                continue;
            }

            for (String field : SECURITY_SCHEME_FIELDS) {
                String existingValue = getComparableValue(existingScheme, field);
                if (existingValue == null) {
                    // Nothing was promised, so whatever the proposed card says cannot break a
                    // client that was written against the existing one.
                    continue;
                }
                String proposedValue = getComparableValue(proposedScheme, field);
                if (!existingValue.equals(proposedValue)) {
                    differences.add(new SimpleCompatibilityDifference(
                            describeSchemeFieldChange(schemeName, field, existingValue,
                                    proposedValue),
                            CONTEXT_SECURITY_SCHEMES));
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
            JsonNode proposed, Set<SimpleCompatibilityDifference> differences) {
        if (proposed == null) {
            differences.add(new SimpleCompatibilityDifference(
                    "Security scheme '" + schemeName + "' no longer declares '" + path + "'",
                    CONTEXT_SECURITY_SCHEMES));
            return;
        }

        if (path.endsWith("/" + SCOPES_FIELD) && existing.isObject() && proposed.isObject()) {
            checkRetainedScopeNames(schemeName, path, existing, proposed, differences);
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
            differences.add(new SimpleCompatibilityDifference(
                    describeSchemeFieldChange(schemeName, path, renderValue(existing),
                            renderValue(proposed)),
                    CONTEXT_SECURITY_SCHEMES));
        }
    }

    /**
     * A scopes map is name -> short description, so only the names are contract: withdrawing one
     * breaks a client, rewording one does not. Comparing the values would contradict the
     * description exemption applied to the scheme's own fields, and would judge the map form more
     * harshly than the array form that checkRetainedArrayValues already treats as a set.
     */
    private void checkRetainedScopeNames(String schemeName, String path, JsonNode existing,
            JsonNode proposed, Set<SimpleCompatibilityDifference> differences) {
        Iterator<String> scopeNames = existing.fieldNames();
        while (scopeNames.hasNext()) {
            String scopeName = scopeNames.next();
            if (!proposed.has(scopeName)) {
                differences.add(new SimpleCompatibilityDifference(
                        "Security scheme '" + schemeName + "' no longer offers '" + scopeName
                                + "' in '" + path + "'", CONTEXT_SECURITY_SCHEMES));
            }
        }
    }

    /**
     * An array inside flows is a set of offered values - a scope list is the usual case - so only
     * losing an entry is a break. This mirrors how checkModeRemovals treats defaultInputModes:
     * comparing the arrays whole would report a purely additive edit as incompatible.
     */
    private void checkRetainedArrayValues(String schemeName, String path, JsonNode existing,
            JsonNode proposed, Set<SimpleCompatibilityDifference> differences) {
        for (JsonNode existingItem : existing) {
            boolean retained = false;
            for (JsonNode proposedItem : proposed) {
                if (existingItem.equals(proposedItem)) {
                    retained = true;
                    break;
                }
            }
            if (!retained) {
                differences.add(new SimpleCompatibilityDifference(
                        "Security scheme '" + schemeName + "' no longer offers '"
                                + renderValue(existingItem) + "' in '" + path + "'",
                        CONTEXT_SECURITY_SCHEMES));
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
            Set<SimpleCompatibilityDifference> differences) {
        String existingVersion = getComparableValue(existing, "protocolVersion");
        if (existingVersion == null) {
            return;
        }

        String proposedVersion = getComparableValue(proposed, "protocolVersion");
        if (existingVersion.equals(proposedVersion)) {
            return;
        }

        differences.add(new SimpleCompatibilityDifference(
                "The agent protocolVersion " + describeChange(existingVersion, proposedVersion),
                CONTEXT_PROTOCOL_VERSION));
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
     * Compares whatever value is present rather than textual values alone. getTextValue yields null
     * for a non-textual node, which is indistinguishable from an absent one, so a field that turns
     * into a number would otherwise be reported as having been removed when it was changed. The
     * validity rule would reject those shapes, but it is opt-in.
     */
    private String getComparableValue(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return (field == null || field.isNull()) ? null : renderValue(field);
    }

    private String renderValue(JsonNode node) {
        return node.isTextual() ? node.asText() : node.toString();
    }

    private void checkModeRemovals(JsonNode existing, JsonNode proposed, String fieldName,
            String modeType, Set<SimpleCompatibilityDifference> differences) {
        Set<String> existingModes = extractStringArray(existing, fieldName);
        Set<String> proposedModes = extractStringArray(proposed, fieldName);

        for (String mode : existingModes) {
            if (!proposedModes.contains(mode)) {
                differences.add(new SimpleCompatibilityDifference(
                        "The " + modeType + " '" + mode + MSG_WAS_REMOVED, CONTEXT_MODES));
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
}
