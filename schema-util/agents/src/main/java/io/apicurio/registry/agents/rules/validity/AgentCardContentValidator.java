package io.apicurio.registry.agents.rules.validity;

import io.apicurio.registry.rules.validity.AbstractContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Content validator for A2A Agent Card artifacts, aligned with A2A Protocol v1.0.
 */
public class AgentCardContentValidator extends AbstractContentValidator {

    private static final String PROTOCOL_VERSION = "protocolVersion";
    private static final String MUST_BE_AN_OBJECT = " must be an object";
    private static final String EXTENSIONS = "extensions";
    private static final String SKILL_AT_INDEX = "Skill at index ";
    private static final String TAGS_PATH = "/tags";
    private static final String SECURITY_REQUIREMENTS = "securityRequirements";
    private static final String DESCRIPTION = "description";
    private static final String SUPPORTED_INTERFACES = "supportedInterfaces";
    private static final String SUPPORTED_INTERFACES_PATH = "/supportedInterfaces";
    private static final String CAPABILITIES = "capabilities";
    private static final String SKILLS = "skills";
    private static final String SKILLS_PATH = "/skills";

    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {

        if (level == ValidityLevel.NONE) {
            return;
        }

        Set<RuleViolation> violations = new HashSet<>();

        try {
            JsonNode tree = ContentTypeUtil.parseJson(content.getContent());

            if (!tree.isObject()) {
                throw new RuleViolationException("Agent Card must be a JSON object",
                        RuleType.VALIDITY, level.name(),
                        Collections.singleton(new RuleViolation("Agent Card must be a JSON object", "")));
            }

            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            validateRequiredFields(tree, violations);
            validateStringFields(tree, violations);
            validateProviderField(tree, violations);
            validateSupportedInterfaces(tree, violations);
            validateCapabilitiesField(tree, violations);
            validateSkillsField(tree, violations);
            validateArrayFields(tree, violations);
            validateSecuritySchemes(tree, violations);
            validateSecurityRequirements(tree, violations);
            validateSignatures(tree, violations);

            if (!violations.isEmpty()) {
                throw new RuleViolationException("Invalid Agent Card", RuleType.VALIDITY, level.name(),
                        violations);
            }

        } catch (RuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleViolationException("Invalid Agent Card JSON: " + e.getMessage(),
                    RuleType.VALIDITY, level.name(), e);
        }
    }

    private void validateRequiredFields(JsonNode tree, Set<RuleViolation> violations) {
        validateRequiredNonEmptyString(tree, "name", violations);
        validateRequiredNonEmptyString(tree, DESCRIPTION, violations);
        validateRequiredString(tree, "version", violations);

        if (!tree.has(SUPPORTED_INTERFACES)) {
            violations.add(new RuleViolation(
                    "Agent Card must have a 'supportedInterfaces' field", SUPPORTED_INTERFACES_PATH));
        } else if (!tree.get(SUPPORTED_INTERFACES).isArray()) {
            violations.add(new RuleViolation(
                    "'supportedInterfaces' field must be an array", SUPPORTED_INTERFACES_PATH));
        } else if (tree.get(SUPPORTED_INTERFACES).isEmpty()) {
            violations.add(new RuleViolation(
                    "'supportedInterfaces' must contain at least one interface", SUPPORTED_INTERFACES_PATH));
        }

        if (!tree.has(CAPABILITIES)) {
            violations.add(new RuleViolation(
                    "Agent Card must have a 'capabilities' field", "/capabilities"));
        }

        if (!tree.has(SKILLS)) {
            violations.add(new RuleViolation(
                    "Agent Card must have a 'skills' field", SKILLS_PATH));
        } else if (!tree.get(SKILLS).isArray()) {
            violations.add(new RuleViolation("'skills' field must be an array", SKILLS_PATH));
        } else if (tree.get(SKILLS).isEmpty()) {
            violations.add(new RuleViolation(
                    "'skills' must contain at least one skill", SKILLS_PATH));
        }

        if (!tree.has("defaultInputModes")) {
            violations.add(new RuleViolation(
                    "Agent Card must have a 'defaultInputModes' field", "/defaultInputModes"));
        }
        if (!tree.has("defaultOutputModes")) {
            violations.add(new RuleViolation(
                    "Agent Card must have a 'defaultOutputModes' field", "/defaultOutputModes"));
        }
    }

    private void validateRequiredNonEmptyString(JsonNode tree, String fieldName,
            Set<RuleViolation> violations) {
        if (!tree.has(fieldName)) {
            violations.add(new RuleViolation(
                    "Agent Card must have a '" + fieldName + "' field", "/" + fieldName));
        } else if (!tree.get(fieldName).isTextual()) {
            violations.add(new RuleViolation(
                    "'" + fieldName + "' field must be a string", "/" + fieldName));
        } else if (tree.get(fieldName).asText().trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "'" + fieldName + "' field must not be empty", "/" + fieldName));
        }
    }

    private void validateRequiredString(JsonNode tree, String fieldName,
            Set<RuleViolation> violations) {
        if (!tree.has(fieldName)) {
            violations.add(new RuleViolation(
                    "Agent Card must have a '" + fieldName + "' field", "/" + fieldName));
        } else if (!tree.get(fieldName).isTextual()) {
            violations.add(new RuleViolation(
                    "'" + fieldName + "' field must be a string", "/" + fieldName));
        }
    }

    private void validateStringFields(JsonNode tree, Set<RuleViolation> violations) {
        JsonValidationUtils.validateOptionalString(tree, PROTOCOL_VERSION, violations);
        // iconUrl and documentationUrl are optional string fields; the A2A agent card spec does
        // not restrict these to http(s) only (e.g. data: URIs are valid for inline icons),
        // so we validate only that they are strings rather than enforcing a URL scheme.
        JsonValidationUtils.validateOptionalString(tree, "iconUrl", violations);
        JsonValidationUtils.validateOptionalString(tree, "documentationUrl", violations);
    }

    private void validateProviderField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("provider")) {
            return;
        }

        JsonNode provider = tree.get("provider");
        if (!provider.isObject()) {
            violations.add(new RuleViolation("'provider' field must be an object", "/provider"));
            return;
        }

        if (!provider.has("organization") || !provider.get("organization").isTextual()) {
            violations.add(new RuleViolation(
                    "'provider.organization' is required and must be a string",
                    "/provider/organization"));
        }

        if (!provider.has("url") || !provider.get("url").isTextual()) {
            violations.add(new RuleViolation(
                    "'provider.url' is required and must be a string", "/provider/url"));
        } else {
            JsonValidationUtils.validateHttpUrl(provider.get("url").asText(), "/provider/url", violations);
        }
    }

    private void validateSupportedInterfaces(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has(SUPPORTED_INTERFACES) || !tree.get(SUPPORTED_INTERFACES).isArray()) {
            return;
        }

        JsonNode interfaces = tree.get(SUPPORTED_INTERFACES);
        int index = 0;
        for (JsonNode iface : interfaces) {
            String basePath = "/supportedInterfaces/" + index;

            if (!iface.isObject()) {
                violations.add(new RuleViolation(
                        "Interface at index " + index + MUST_BE_AN_OBJECT, basePath));
                index++;
                continue;
            }

            if (!iface.has("url") || !iface.get("url").isTextual()) {
                violations.add(new RuleViolation(
                        "Interface 'url' is required and must be a string", basePath + "/url"));
            } else {
                JsonValidationUtils.validateHttpUrl(iface.get("url").asText(), basePath + "/url", violations);
            }

            if (!iface.has("protocolBinding") || !iface.get("protocolBinding").isTextual()) {
                violations.add(new RuleViolation(
                        "Interface 'protocolBinding' is required and must be a string",
                        basePath + "/protocolBinding"));
            }

            if (!iface.has(PROTOCOL_VERSION) || !iface.get(PROTOCOL_VERSION).isTextual()) {
                violations.add(new RuleViolation(
                        "Interface 'protocolVersion' is required and must be a string",
                        basePath + "/protocolVersion"));
            }

            index++;
        }
    }

    private void validateCapabilitiesField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has(CAPABILITIES)) {
            return;
        }

        JsonNode capabilities = tree.get(CAPABILITIES);
        if (!capabilities.isObject()) {
            violations.add(new RuleViolation("'capabilities' field must be an object", "/capabilities"));
            return;
        }

        validateCapabilityBoolean(capabilities, "streaming", violations);
        validateCapabilityBoolean(capabilities, "pushNotifications", violations);
        validateCapabilityBoolean(capabilities, "extendedAgentCard", violations);

        if (capabilities.has(EXTENSIONS)) {
            if (!capabilities.get(EXTENSIONS).isArray()) {
                violations.add(new RuleViolation(
                        "'capabilities.extensions' must be an array", "/capabilities/extensions"));
            } else {
                int index = 0;
                for (JsonNode ext : capabilities.get(EXTENSIONS)) {
                    if (!ext.isObject()) {
                        violations.add(new RuleViolation(
                                "Extension at index " + index + MUST_BE_AN_OBJECT,
                                "/capabilities/extensions/" + index));
                    }
                    index++;
                }
            }
        }
    }

    private void validateCapabilityBoolean(JsonNode capabilities, String fieldName,
            Set<RuleViolation> violations) {
        if (capabilities.has(fieldName) && !capabilities.get(fieldName).isBoolean()) {
            violations.add(new RuleViolation("'capabilities." + fieldName + "' must be a boolean",
                    "/capabilities/" + fieldName));
        }
    }

    private void validateSkillsField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has(SKILLS) || !tree.get(SKILLS).isArray()) {
            return;
        }

        JsonNode skills = tree.get(SKILLS);
        int index = 0;
        for (JsonNode skill : skills) {
            validateSkill(skill, index, violations);
            index++;
        }
    }

    private void validateSkill(JsonNode skill, int index, Set<RuleViolation> violations) {
        String basePath = "/skills/" + index;

        if (!skill.isObject()) {
            violations.add(new RuleViolation(
                    SKILL_AT_INDEX + index + MUST_BE_AN_OBJECT, basePath));
            return;
        }

        validateSkillId(skill, index, basePath, violations);
        validateSkillNameAndDescription(skill, index, basePath, violations);
        validateSkillTags(skill, index, basePath, violations);
        validateSkillExamples(skill, basePath, violations);

        validateOptionalSkillStringArray(skill, "inputModes", basePath, violations);
        validateOptionalSkillStringArray(skill, "outputModes", basePath, violations);

        if (skill.has(SECURITY_REQUIREMENTS) && !skill.get(SECURITY_REQUIREMENTS).isArray()) {
            violations.add(new RuleViolation(
                    "Skill 'securityRequirements' must be an array",
                    basePath + "/securityRequirements"));
        }
    }

    private void validateSkillId(JsonNode skill, int index, String basePath,
            Set<RuleViolation> violations) {
        if (!skill.has("id")) {
            violations.add(new RuleViolation(
                    SKILL_AT_INDEX + index + " must have an 'id' field", basePath + "/id"));
        } else if (!skill.get("id").isTextual()) {
            violations.add(new RuleViolation("Skill 'id' must be a string", basePath + "/id"));
        } else if (skill.get("id").asText().trim().isEmpty()) {
            violations.add(new RuleViolation("Skill 'id' must not be empty", basePath + "/id"));
        }
    }

    private void validateSkillNameAndDescription(JsonNode skill, int index, String basePath,
            Set<RuleViolation> violations) {
        if (!skill.has("name")) {
            violations.add(new RuleViolation(
                    SKILL_AT_INDEX + index + " must have a 'name' field", basePath + "/name"));
        } else if (!skill.get("name").isTextual()) {
            violations.add(new RuleViolation("Skill 'name' must be a string", basePath + "/name"));
        }

        if (!skill.has(DESCRIPTION)) {
            violations.add(new RuleViolation(
                    SKILL_AT_INDEX + index + " must have a 'description' field",
                    basePath + "/description"));
        } else if (!skill.get(DESCRIPTION).isTextual()) {
            violations.add(new RuleViolation(
                    "Skill 'description' must be a string", basePath + "/description"));
        }
    }

    private void validateSkillTags(JsonNode skill, int index, String basePath,
            Set<RuleViolation> violations) {
        if (!skill.has("tags")) {
            violations.add(new RuleViolation(
                    SKILL_AT_INDEX + index + " must have a 'tags' field", basePath + TAGS_PATH));
        } else if (!skill.get("tags").isArray()) {
            violations.add(new RuleViolation("Skill 'tags' must be an array", basePath + TAGS_PATH));
        } else if (skill.get("tags").isEmpty()) {
            violations.add(new RuleViolation(
                    "Skill 'tags' must contain at least one tag", basePath + TAGS_PATH));
        } else {
            JsonValidationUtils.validateStringArray(skill.get("tags"), basePath + TAGS_PATH, "tag",
                    violations);
        }
    }

    private void validateSkillExamples(JsonNode skill, String basePath, Set<RuleViolation> violations) {
        if (skill.has("examples") && !skill.get("examples").isArray()) {
            violations.add(new RuleViolation(
                    "Skill 'examples' must be an array", basePath + "/examples"));
        } else if (skill.has("examples")) {
            JsonValidationUtils.validateStringArray(skill.get("examples"), basePath + "/examples",
                    "example", violations);
        }
    }

    private void validateOptionalSkillStringArray(JsonNode skill, String fieldName,
            String basePath, Set<RuleViolation> violations) {
        if (!skill.has(fieldName)) {
            return;
        }
        JsonNode array = skill.get(fieldName);
        if (!array.isArray()) {
            violations.add(new RuleViolation(
                    "Skill '" + fieldName + "' must be an array", basePath + "/" + fieldName));
            return;
        }
        JsonValidationUtils.validateStringArray(array, basePath + "/" + fieldName, "item", violations);
    }

    private void validateArrayFields(JsonNode tree, Set<RuleViolation> violations) {
        JsonValidationUtils.validateStringArrayField(tree, "defaultInputModes", violations);
        JsonValidationUtils.validateStringArrayField(tree, "defaultOutputModes", violations);
    }

    private void validateSecuritySchemes(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("securitySchemes")) {
            return;
        }

        JsonNode schemes = tree.get("securitySchemes");
        if (!schemes.isObject()) {
            violations.add(new RuleViolation(
                    "'securitySchemes' field must be an object", "/securitySchemes"));
            return;
        }

        Iterator<String> fieldNames = schemes.fieldNames();
        while (fieldNames.hasNext()) {
            String schemeName = fieldNames.next();
            JsonNode scheme = schemes.get(schemeName);
            if (!scheme.isObject()) {
                violations.add(new RuleViolation(
                        "Security scheme '" + schemeName + "' must be an object",
                        "/securitySchemes/" + schemeName));
            }
        }
    }

    private void validateSecurityRequirements(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has(SECURITY_REQUIREMENTS)) {
            return;
        }

        JsonNode requirements = tree.get(SECURITY_REQUIREMENTS);
        if (!requirements.isArray()) {
            violations.add(new RuleViolation(
                    "'securityRequirements' field must be an array", "/securityRequirements"));
            return;
        }

        int index = 0;
        for (JsonNode req : requirements) {
            if (!req.isObject()) {
                violations.add(new RuleViolation(
                        "Security requirement at index " + index + MUST_BE_AN_OBJECT,
                        "/securityRequirements/" + index));
            }
            index++;
        }
    }

    private void validateSignatures(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("signatures")) {
            return;
        }

        JsonNode signatures = tree.get("signatures");
        if (!signatures.isArray()) {
            violations.add(new RuleViolation("'signatures' field must be an array", "/signatures"));
            return;
        }

        int index = 0;
        for (JsonNode sig : signatures) {
            String basePath = "/signatures/" + index;
            if (!sig.isObject()) {
                violations.add(new RuleViolation(
                        "Signature at index " + index + MUST_BE_AN_OBJECT, basePath));
            } else {
                if (!sig.has("protected") || !sig.get("protected").isTextual()) {
                    violations.add(new RuleViolation(
                            "Signature 'protected' is required and must be a string",
                            basePath + "/protected"));
                }
                if (!sig.has("signature") || !sig.get("signature").isTextual()) {
                    violations.add(new RuleViolation(
                            "Signature 'signature' is required and must be a string",
                            basePath + "/signature"));
                }
            }
            index++;
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        Set<String> allRefs = extractExternalJsonRefs(content);
        if (!allRefs.isEmpty()) {
            validateMappedReferences(references, allRefs, "Unmapped reference detected.");
        }
    }

    /** Tools, servers, models and prompts the agent uses, and peer agents it delegates to. */
    @Override
    public Optional<Set<String>> getAllowedReferenceTargetTypes() {
        return Optional.of(Set.of(ArtifactType.MCP_TOOL, ArtifactType.MCP_SERVER, ArtifactType.MODEL_SCHEMA,
                ArtifactType.PROMPT_TEMPLATE, ArtifactType.AGENT_CARD));
    }
}
