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

            // SYNTAX_ONLY level: just check it's valid JSON (already done by parsing)
            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            // FULL level: validate required fields
            if (!tree.has("name") || tree.get("name").asText().trim().isEmpty()) {
                violations.add(new RuleViolation("Agent Card must have a non-empty 'name' field", "/name"));
            }

            // Validate optional but typed fields if present
            if (tree.has("version") && !tree.get("version").isTextual()) {
                violations.add(new RuleViolation("'version' field must be a string", "/version"));
            }

            if (tree.has("url") && !tree.get("url").isTextual()) {
                violations.add(new RuleViolation("'url' field must be a string", "/url"));
            }

            if (tree.has("capabilities") && !tree.get("capabilities").isObject()) {
                violations.add(new RuleViolation("'capabilities' field must be an object", "/capabilities"));
            }

            if (tree.has("skills") && !tree.get("skills").isArray()) {
                violations.add(new RuleViolation("'skills' field must be an array", "/skills"));
            }

            if (tree.has("defaultInputModes") && !tree.get("defaultInputModes").isArray()) {
                violations.add(
                        new RuleViolation("'defaultInputModes' field must be an array", "/defaultInputModes"));
            }

            if (tree.has("defaultOutputModes") && !tree.get("defaultOutputModes").isArray()) {
                violations.add(new RuleViolation("'defaultOutputModes' field must be an array",
                        "/defaultOutputModes"));
            }

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

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // Agent Cards don't support references in MVP
        if (references != null && !references.isEmpty()) {
            throw new RuleViolationException("Agent Cards do not support references",
                    RuleType.INTEGRITY, "NONE",
                    Collections.singleton(new RuleViolation("References are not supported for Agent Cards", "")));
        }
    }
}
