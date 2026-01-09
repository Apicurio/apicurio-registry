package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content validator for A2A Agent Card artifacts.
 *
 * Validates that the content is a valid Agent Card JSON document following the A2A protocol specification.
 *
 * Validation levels:
 * - NONE: No validation
 * - SYNTAX_ONLY: Validates that the content is valid JSON and is an object
 * - FULL: Full schema validation including required fields, type checking, and structure validation
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 */
public class AgentCardContentValidator implements ContentValidator {

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

            // SYNTAX_ONLY level: just check it's valid JSON object (already done)
            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            // FULL level: comprehensive validation
            validateRequiredFields(tree, violations);
            validateStringFields(tree, violations);
            validateProviderField(tree, violations);
            validateCapabilitiesField(tree, violations);
            validateSkillsField(tree, violations);
            validateArrayFields(tree, violations);
            validateAuthenticationField(tree, violations);

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
        // 'name' is required and must be non-empty
        if (!tree.has("name")) {
            violations.add(new RuleViolation("Agent Card must have a 'name' field", "/name"));
        } else if (!tree.get("name").isTextual()) {
            violations.add(new RuleViolation("'name' field must be a string", "/name"));
        } else if (tree.get("name").asText().trim().isEmpty()) {
            violations.add(new RuleViolation("'name' field must not be empty", "/name"));
        }
    }

    private void validateStringFields(JsonNode tree, Set<RuleViolation> violations) {
        // Validate optional string fields
        validateOptionalString(tree, "description", violations);
        validateOptionalString(tree, "version", violations);
        validateOptionalString(tree, "url", violations);
    }

    private void validateOptionalString(JsonNode tree, String fieldName, Set<RuleViolation> violations) {
        if (tree.has(fieldName) && !tree.get(fieldName).isTextual()) {
            violations.add(new RuleViolation("'" + fieldName + "' field must be a string", "/" + fieldName));
        }
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

        if (provider.has("organization") && !provider.get("organization").isTextual()) {
            violations.add(new RuleViolation("'provider.organization' must be a string", "/provider/organization"));
        }

        if (provider.has("url") && !provider.get("url").isTextual()) {
            violations.add(new RuleViolation("'provider.url' must be a string", "/provider/url"));
        }
    }

    private void validateCapabilitiesField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("capabilities")) {
            return;
        }

        JsonNode capabilities = tree.get("capabilities");
        if (!capabilities.isObject()) {
            violations.add(new RuleViolation("'capabilities' field must be an object", "/capabilities"));
            return;
        }

        // Validate known capability fields are booleans
        validateCapabilityBoolean(capabilities, "streaming", violations);
        validateCapabilityBoolean(capabilities, "pushNotifications", violations);
    }

    private void validateCapabilityBoolean(JsonNode capabilities, String fieldName, Set<RuleViolation> violations) {
        if (capabilities.has(fieldName) && !capabilities.get(fieldName).isBoolean()) {
            violations.add(new RuleViolation("'capabilities." + fieldName + "' must be a boolean",
                    "/capabilities/" + fieldName));
        }
    }

    private void validateSkillsField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("skills")) {
            return;
        }

        JsonNode skills = tree.get("skills");
        if (!skills.isArray()) {
            violations.add(new RuleViolation("'skills' field must be an array", "/skills"));
            return;
        }

        int index = 0;
        for (JsonNode skill : skills) {
            String basePath = "/skills/" + index;

            if (!skill.isObject()) {
                violations.add(new RuleViolation("Skill at index " + index + " must be an object", basePath));
                index++;
                continue;
            }

            // 'id' is required for skills
            if (!skill.has("id")) {
                violations.add(new RuleViolation("Skill at index " + index + " must have an 'id' field",
                        basePath + "/id"));
            } else if (!skill.get("id").isTextual()) {
                violations.add(new RuleViolation("Skill 'id' must be a string", basePath + "/id"));
            } else if (skill.get("id").asText().trim().isEmpty()) {
                violations.add(new RuleViolation("Skill 'id' must not be empty", basePath + "/id"));
            }

            // 'name' is required for skills
            if (!skill.has("name")) {
                violations.add(new RuleViolation("Skill at index " + index + " must have a 'name' field",
                        basePath + "/name"));
            } else if (!skill.get("name").isTextual()) {
                violations.add(new RuleViolation("Skill 'name' must be a string", basePath + "/name"));
            }

            // Optional fields validation
            if (skill.has("description") && !skill.get("description").isTextual()) {
                violations.add(new RuleViolation("Skill 'description' must be a string", basePath + "/description"));
            }

            if (skill.has("tags") && !skill.get("tags").isArray()) {
                violations.add(new RuleViolation("Skill 'tags' must be an array", basePath + "/tags"));
            } else if (skill.has("tags")) {
                validateStringArray(skill.get("tags"), basePath + "/tags", "tag", violations);
            }

            if (skill.has("examples") && !skill.get("examples").isArray()) {
                violations.add(new RuleViolation("Skill 'examples' must be an array", basePath + "/examples"));
            } else if (skill.has("examples")) {
                validateStringArray(skill.get("examples"), basePath + "/examples", "example", violations);
            }

            index++;
        }
    }

    private void validateArrayFields(JsonNode tree, Set<RuleViolation> violations) {
        validateStringArrayField(tree, "defaultInputModes", violations);
        validateStringArrayField(tree, "defaultOutputModes", violations);
    }

    private void validateStringArrayField(JsonNode tree, String fieldName, Set<RuleViolation> violations) {
        if (!tree.has(fieldName)) {
            return;
        }

        JsonNode array = tree.get(fieldName);
        if (!array.isArray()) {
            violations.add(new RuleViolation("'" + fieldName + "' field must be an array", "/" + fieldName));
            return;
        }

        validateStringArray(array, "/" + fieldName, "item", violations);
    }

    private void validateStringArray(JsonNode array, String basePath, String itemName, Set<RuleViolation> violations) {
        int index = 0;
        for (JsonNode item : array) {
            if (!item.isTextual()) {
                violations.add(new RuleViolation("Each " + itemName + " must be a string",
                        basePath + "/" + index));
            }
            index++;
        }
    }

    private void validateAuthenticationField(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("authentication")) {
            return;
        }

        JsonNode auth = tree.get("authentication");
        if (!auth.isObject()) {
            violations.add(new RuleViolation("'authentication' field must be an object", "/authentication"));
            return;
        }

        if (auth.has("schemes")) {
            if (!auth.get("schemes").isArray()) {
                violations.add(new RuleViolation("'authentication.schemes' must be an array",
                        "/authentication/schemes"));
            } else {
                validateStringArray(auth.get("schemes"), "/authentication/schemes", "scheme", violations);
            }
        }

        if (auth.has("credentials")) {
            if (!auth.get("credentials").isArray()) {
                violations.add(new RuleViolation("'authentication.credentials' must be an array",
                        "/authentication/credentials"));
            } else {
                int index = 0;
                for (JsonNode cred : auth.get("credentials")) {
                    if (!cred.isObject()) {
                        violations.add(new RuleViolation("Credential at index " + index + " must be an object",
                                "/authentication/credentials/" + index));
                    }
                    index++;
                }
            }
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // Agent Cards don't support references
        if (references != null && !references.isEmpty()) {
            throw new RuleViolationException("Agent Cards do not support references",
                    RuleType.INTEGRITY, "NONE",
                    Collections.singleton(new RuleViolation("References are not supported for Agent Cards", "")));
        }
    }
}
