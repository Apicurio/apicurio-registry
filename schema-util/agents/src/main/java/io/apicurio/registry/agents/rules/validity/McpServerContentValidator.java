package io.apicurio.registry.agents.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import tools.jackson.databind.json.JsonMapper;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.path.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.Error;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates the pinned MCP server.json schema locally, without resolving publisher-supplied URLs. */
public class McpServerContentValidator implements ContentValidator {

    public static final Pattern SERVER_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+/[a-zA-Z0-9._-]+$");
    private static final Schema SCHEMA = loadSchema();

    private static Schema loadSchema() {
        try (InputStream stream = McpServerContentValidator.class.getResourceAsStream("mcp-server-2025-12-11.json")) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled MCP server schema");
            }
            var schema = new JsonMapper().readTree(stream);
            var config = SchemaRegistryConfig.builder().pathType(PathType.JSON_POINTER).build();
            // The bundled schema refers only to its own definitions. Every loader is replaced by one
            // that refuses, so no document is ever fetched, from the network or the classpath.
            var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7, builder -> builder
                    .schemaRegistryConfig(config)
                    .schemaLoader(loader -> loader.resourceLoaders(loaders -> loaders.values(List::clear))
                            .allow(iri -> false)));
            var loaded = registry.getSchema(SchemaLocation.of(schema.path("$id").asString()), schema);
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
        List<Error> messages;
        try {
            var tree = ContentTypeUtil.parseJson(content.getContent());
            if (!tree.isObject()) {
                throw new IllegalArgumentException("Expected an object");
            }
            messages = level == ValidityLevel.FULL ? SCHEMA.validate(tree) : List.of();
        } catch (Exception e) {
            throw new RuleViolationException("MCP server definition must be a JSON object", RuleType.VALIDITY,
                    level.name(), Set.of(new RuleViolation("Invalid JSON object", "")));
        }
        if (!messages.isEmpty()) {
            Set<RuleViolation> violations = new HashSet<>();
            for (Error message : messages) {
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
        // server.json has no reference fields, so there is nothing in the content that must be mapped.
    }

    /** A server references the tools it provides. */
    @Override
    public Optional<Set<String>> getAllowedReferenceTargetTypes() {
        return Optional.of(Set.of(ArtifactType.MCP_TOOL));
    }
}
