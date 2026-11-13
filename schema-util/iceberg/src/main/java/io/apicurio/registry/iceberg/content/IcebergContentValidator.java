package io.apicurio.registry.iceberg.content;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content validator for Apache Iceberg table and view metadata.
 *
 * Validates that the content is a valid Iceberg metadata JSON document.
 *
 * Validation levels:
 * - NONE: No validation
 * - SYNTAX_ONLY: Validates that the content is valid JSON and is an object
 * - FULL: Full validation including required fields and structure
 *
 * @see <a href="https://iceberg.apache.org/spec/">Apache Iceberg Specification</a>
 */
public class IcebergContentValidator implements ContentValidator {

    private final boolean isTable;

    public IcebergContentValidator(boolean isTable) {
        this.isTable = isTable;
    }

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
                throw new RuleViolationException("Iceberg metadata must be a JSON object",
                        RuleType.VALIDITY, level.name(),
                        Collections.singleton(new RuleViolation("Iceberg metadata must be a JSON object", "")));
            }

            if (level == ValidityLevel.SYNTAX_ONLY) {
                return;
            }

            // FULL validation
            validateFormatVersion(tree, violations);

            if (isTable) {
                validateTableMetadata(tree, violations);
            } else {
                validateViewMetadata(tree, violations);
            }

            if (!violations.isEmpty()) {
                throw new RuleViolationException("Invalid Iceberg metadata", RuleType.VALIDITY, level.name(),
                        violations);
            }

        } catch (RuleViolationException e) {
            throw e;
        } catch (Exception e) {
            throw new RuleViolationException("Invalid Iceberg metadata JSON: " + e.getMessage(),
                    RuleType.VALIDITY, level.name(), e);
        }
    }

    private void validateFormatVersion(JsonNode tree, Set<RuleViolation> violations) {
        if (!tree.has("format-version")) {
            violations.add(new RuleViolation("Iceberg metadata must have a 'format-version' field", "/format-version"));
        } else if (!tree.get("format-version").isInt()) {
            violations.add(new RuleViolation("'format-version' must be an integer", "/format-version"));
        }
    }

    private void validateTableMetadata(JsonNode tree, Set<RuleViolation> violations) {
        // table-uuid is required
        if (!tree.has("table-uuid")) {
            violations.add(new RuleViolation("Iceberg table metadata must have a 'table-uuid' field", "/table-uuid"));
        } else if (!tree.get("table-uuid").isTextual()) {
            violations.add(new RuleViolation("'table-uuid' must be a string", "/table-uuid"));
        }

        // location is required
        if (!tree.has("location")) {
            violations.add(new RuleViolation("Iceberg table metadata must have a 'location' field", "/location"));
        } else if (!tree.get("location").isTextual()) {
            violations.add(new RuleViolation("'location' must be a string", "/location"));
        }

        // Validate schemas array (format version 2+)
        if (tree.has("schemas")) {
            if (!tree.get("schemas").isArray()) {
                violations.add(new RuleViolation("'schemas' must be an array", "/schemas"));
            }
        }

        // Validate current-schema-id if present
        if (tree.has("current-schema-id") && !tree.get("current-schema-id").isInt()) {
            violations.add(new RuleViolation("'current-schema-id' must be an integer", "/current-schema-id"));
        }

        // Validate partition-specs if present
        if (tree.has("partition-specs") && !tree.get("partition-specs").isArray()) {
            violations.add(new RuleViolation("'partition-specs' must be an array", "/partition-specs"));
        }

        // Validate sort-orders if present
        if (tree.has("sort-orders") && !tree.get("sort-orders").isArray()) {
            violations.add(new RuleViolation("'sort-orders' must be an array", "/sort-orders"));
        }

        // Validate snapshots if present
        if (tree.has("snapshots") && !tree.get("snapshots").isArray()) {
            violations.add(new RuleViolation("'snapshots' must be an array", "/snapshots"));
        }
    }

    private void validateViewMetadata(JsonNode tree, Set<RuleViolation> violations) {
        // view-uuid is required
        if (!tree.has("view-uuid")) {
            violations.add(new RuleViolation("Iceberg view metadata must have a 'view-uuid' field", "/view-uuid"));
        } else if (!tree.get("view-uuid").isTextual()) {
            violations.add(new RuleViolation("'view-uuid' must be a string", "/view-uuid"));
        }

        // location is required
        if (!tree.has("location")) {
            violations.add(new RuleViolation("Iceberg view metadata must have a 'location' field", "/location"));
        } else if (!tree.get("location").isTextual()) {
            violations.add(new RuleViolation("'location' must be a string", "/location"));
        }

        // versions array is required
        if (!tree.has("versions")) {
            violations.add(new RuleViolation("Iceberg view metadata must have a 'versions' array", "/versions"));
        } else if (!tree.get("versions").isArray()) {
            violations.add(new RuleViolation("'versions' must be an array", "/versions"));
        }

        // current-version-id is required
        if (!tree.has("current-version-id")) {
            violations.add(new RuleViolation("Iceberg view metadata must have a 'current-version-id' field", "/current-version-id"));
        } else if (!tree.get("current-version-id").isInt()) {
            violations.add(new RuleViolation("'current-version-id' must be an integer", "/current-version-id"));
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        // Iceberg metadata doesn't support external references
        if (references != null && !references.isEmpty()) {
            throw new RuleViolationException("Iceberg metadata does not support references",
                    RuleType.INTEGRITY, "NONE",
                    Collections.singleton(new RuleViolation("References are not supported for Iceberg metadata", "")));
        }
    }
}
