package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.HashSet;

/** Validates the pinned MCP server.json schema locally, without resolving publisher-supplied URLs. */
public class McpServerContentValidator implements ContentValidator {

    public static final Pattern SERVER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+/[a-zA-Z0-9._-]+$");
    private static final Schema SCHEMA = loadSchema();

    private static Schema loadSchema() {
        try (InputStream stream = McpServerContentValidator.class.getResourceAsStream("mcp-server-2025-12-11.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled MCP server schema");
            }
            return SchemaLoader.builder().schemaJson(new JSONObject(new JSONTokener(stream)))
                    .draftV7Support().schemaClient(url -> {
                        throw new IllegalArgumentException("External schema resolution is disabled");
                    }).build().load().build();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load bundled MCP server schema", e);
        }
    }

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> references) {
        if (level == ValidityLevel.NONE) {
            return;
        }
        try {
            var tree = ContentTypeUtil.parseJson(content.getContent());
            if (!tree.isObject()) {
                throw new IllegalArgumentException("Expected an object");
            }
            JSONObject document = new JSONObject(tree.toString());
            if (level == ValidityLevel.FULL) {
                SCHEMA.validate(document);
            }
        } catch (ValidationException e) {
            Set<RuleViolation> violations = new HashSet<>();
            collectViolations(e, violations);
            throw new RuleViolationException("Invalid MCP server definition", RuleType.VALIDITY,
                    level.name(), violations);
        } catch (Exception e) {
            throw new RuleViolationException("MCP server definition must be a JSON object", RuleType.VALIDITY,
                    level.name(), Set.of(new RuleViolation("Invalid JSON object", "")));
        }
    }

    private void collectViolations(ValidationException error, Set<RuleViolation> violations) {
        if (error.getCausingExceptions().isEmpty()) {
            String pointer = error.getPointerToViolation();
            violations.add(new RuleViolation(error.getErrorMessage(),
                    pointer.startsWith("#") ? pointer.substring(1) : pointer));
        } else {
            error.getCausingExceptions().forEach(cause -> collectViolations(cause, violations));
        }
    }

    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references) {
        if (references != null && !references.isEmpty()) {
            throw new RuleViolationException("MCP server definitions do not support references",
                    RuleType.INTEGRITY, "NONE", Set.of(new RuleViolation(
                            "References are not supported for MCP server definitions", "")));
        }
    }
}
