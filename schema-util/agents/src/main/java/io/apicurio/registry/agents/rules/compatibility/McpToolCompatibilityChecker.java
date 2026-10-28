package io.apicurio.registry.agents.rules.compatibility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.validity.JsonSchemaDocumentValidator;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.types.ContentTypes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Publication compatibility for versions of one MCP tool. BACKWARD accepts old inputs and
 * preserves old output guarantees; FORWARD reverses both comparisons. This is distinct from
 * comparing one tool's output to another tool's input for chaining.
 */
public class McpToolCompatibilityChecker extends AbstractCompatibilityChecker<SimpleCompatibilityDifference> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaDocumentValidator VALIDATOR = JsonSchemaDocumentValidator.builder()
            .draftDetector(JsonSchemaDocumentValidator.dataModelsDraftDetector()).build();
    private static final Set<String> ANNOTATIONS = Set.of("title", "description", "default", "examples",
            "$comment", "deprecated", "readOnly", "writeOnly");
    private static final Set<String> SCALAR_KEYWORDS = Set.of("$schema", "type", "enum", "const", "required",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "minProperties", "maxProperties");
    private final ApitomyJsonSchemaCompatibilityChecker schemaChecker = new ApitomyJsonSchemaCompatibilityChecker();

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        JsonNode oldTool = parse(existing);
        JsonNode newTool = parse(proposed);
        // Check unchanged schemas too: unsupported assertions must not disappear through equality
        // shortcuts or through a successful comparison of the other side.
        for (JsonNode tool : List.of(oldTool, newTool)) {
            validate(tool.get("inputSchema"), "/inputSchema");
            if (tool.has("outputSchema")) {
                validate(tool.get("outputSchema"), "/outputSchema");
            }
        }
        Set<SimpleCompatibilityDifference> differences = new HashSet<>();
        compare(oldTool.get("inputSchema"), newTool.get("inputSchema"), CompatibilityLevel.BACKWARD,
                "/inputSchema", differences);
        if (oldTool.has("outputSchema")) {
            if (!newTool.has("outputSchema")) {
                differences.add(new SimpleCompatibilityDifference("The output schema guarantee was removed.",
                        "/outputSchema"));
            } else {
                compare(oldTool.get("outputSchema"), newTool.get("outputSchema"), CompatibilityLevel.FORWARD,
                        "/outputSchema", differences);
            }
        }
        return differences;
    }

    private JsonNode parse(String content) {
        try {
            JsonNode tool = MAPPER.readTree(content);
            if (tool != null && tool.isObject()) {
                return tool;
            }
        } catch (JsonProcessingException e) {
            throw new UnprocessableSchemaException("MCP tool compatibility requires a JSON object.", e);
        }
        throw new UnprocessableSchemaException("MCP tool compatibility requires a JSON object.");
    }

    private void validate(JsonNode schema, String path) {
        if (schema == null || !schema.isObject()) {
            throw new UnprocessableSchemaException("MCP tool compatibility requires a schema object at " + path);
        }
        if (!VALIDATOR.validate(schema).isEmpty()) {
            throw new UnprocessableSchemaException("Invalid or unsupported JSON Schema at " + path);
        }
        checkSupported(schema, path);
    }

    /**
     * Bound guarantees to the tested structural subset. Do not project away unknown assertions:
     * the publication rule has no 'indeterminate' success and must report them as 422.
     * Walk schema positions only, not instance-valued annotations, enums or property names.
     */
    private void checkSupported(JsonNode schema, String path) {
        if (schema.isBoolean()) {
            return;
        }
        var fields = schema.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            String keyword = field.getKey();
            String location = path + "/" + escape(keyword);
            if (keyword.equals("properties")) {
                field.getValue().fields().forEachRemaining(property ->
                        checkSupported(property.getValue(), location + "/" + escape(property.getKey())));
            } else if (keyword.equals("items") || keyword.equals("additionalProperties")) {
                if (field.getValue().isArray()) {
                    throw new UnprocessableSchemaException("Unsupported MCP tool compatibility construct at " + location);
                }
                checkSupported(field.getValue(), location);
            } else if (!SCALAR_KEYWORDS.contains(keyword) && !ANNOTATIONS.contains(keyword)) {
                throw new UnprocessableSchemaException("Unsupported MCP tool compatibility construct at " + location);
            }
        }
    }

    private void compare(JsonNode oldSchema, JsonNode newSchema, CompatibilityLevel direction,
            String path, Set<SimpleCompatibilityDifference> differences) {
        try {
            var result = schemaChecker.testCompatibility(direction, List.of(content(oldSchema)),
                    content(newSchema), Map.of());
            result.getIncompatibleDifferences().forEach(difference -> {
                var violation = difference.asRuleViolation();
                differences.add(new SimpleCompatibilityDifference(violation.getDescription(),
                        path + violation.getContext()));
            });
        } catch (RuntimeException e) {
            throw new UnprocessableSchemaException("MCP tool compatibility could not be determined at " + path, e);
        }
    }

    private static TypedContent content(JsonNode schema) {
        return TypedContent.create(ContentHandle.create(schema.toString()), ContentTypes.APPLICATION_JSON);
    }

    private static String escape(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
