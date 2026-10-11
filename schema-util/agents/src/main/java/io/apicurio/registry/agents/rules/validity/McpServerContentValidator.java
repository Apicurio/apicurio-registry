package io.apicurio.registry.agents.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.DisallowSchemaLoader;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the pinned MCP server.json schema locally, without resolving publisher-supplied URLs. */
public class McpServerContentValidator implements ContentValidator {

    public static final Pattern SERVER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+/[a-zA-Z0-9._-]+$");
    private static final JsonSchema SCHEMA = loadSchema();

    private static JsonSchema loadSchema() {
        try (InputStream stream = McpServerContentValidator.class.getResourceAsStream("mcp-server-2025-12-11.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled MCP server schema");
            }
            var schema = new ObjectMapper().readTree(stream);
            // The bundled schema refers only to its own definitions. Every loader is replaced by one
            // that refuses, so no document is ever fetched, from the network or the classpath.
            var factory = JsonSchemaFactory.getInstance(VersionFlag.V7, builder -> builder
                    .schemaLoaders(loaders -> loaders.values(List::clear).add(DisallowSchemaLoader.getInstance())));
            var config = SchemaValidatorsConfig.builder().pathType(PathType.JSON_POINTER).build();
            var loaded = factory.getSchema(SchemaLocation.of(schema.path("$id").asText()), schema, config);
            loaded.initializeValidators();
            return loaded;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load bundled MCP server schema", e);
        }
    }

    @Override
    public void validate(ValidityLevel level, TypedContent content, Map<String, TypedContent> references) {
        if (level == ValidityLevel.NONE) {
            return;
        }
        Set<ValidationMessage> messages;
        try {
            var tree = ContentTypeUtil.parseJson(content.getContent());
            if (!tree.isObject()) {
                throw new IllegalArgumentException("Expected an object");
            }
            messages = level == ValidityLevel.FULL ? SCHEMA.validate(tree) : Set.of();
        } catch (Exception e) {
            throw new RuleViolationException("MCP server definition must be a JSON object", RuleType.VALIDITY,
                    level.name(), Set.of(new RuleViolation("Invalid JSON object", "")));
        }
        if (!messages.isEmpty()) {
            Set<RuleViolation> violations = new HashSet<>();
            for (ValidationMessage message : messages) {
                var location = message.getInstanceLocation().toString();
                violations.add(new RuleViolation(withoutLocation(message.getMessage(), location), location));
            }
            throw new RuleViolationException("Invalid MCP server definition", RuleType.VALIDITY,
                    level.name(), violations);
        }
    }

    /** The library starts each message with the instance location, which the violation carries as its context. */
    private static String withoutLocation(String message, String location) {
        var prefix = location + ": ";
        return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
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
