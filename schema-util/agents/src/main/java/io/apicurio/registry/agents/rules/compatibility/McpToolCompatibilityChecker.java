package io.apicurio.registry.agents.rules.compatibility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.compatibility.ApitomyJsonSchemaCompatibilityChecker;
import io.apicurio.registry.json.rules.validity.JsonSchemaDocumentValidator;
import io.apicurio.registry.rules.compatibility.AbstractCompatibilityChecker;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.compatibility.SimpleCompatibilityDifference;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.types.ContentTypes;
import com.networknt.schema.SpecVersion.VersionFlag;

import java.math.BigDecimal;
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

    // Numbers reach the shared checker as written, rather than rounded through a double: two
    // different high-precision enum values or bounds must not compare as one.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
    private static final JsonSchemaDocumentValidator VALIDATOR = JsonSchemaDocumentValidator.builder()
            .draftDetector(JsonSchemaDocumentValidator.dataModelsDraftDetector()).build();
    private static final Set<String> ANNOTATIONS = Set.of("title", "description", "default", "examples",
            "$comment", "deprecated", "readOnly", "writeOnly");
    private static final Set<String> SCALAR_KEYWORDS = Set.of("$schema", "type", "enum", "const", "required",
            "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf",
            "minLength", "maxLength", "minItems", "maxItems", "uniqueItems", "minProperties", "maxProperties");
    private static final String INPUT_SCHEMA = "inputSchema";
    private static final String OUTPUT_SCHEMA = "outputSchema";
    // JSON Pointer prefixes for rule-violation contexts.
    private static final String INPUT_PATH = "/inputSchema";
    private static final String OUTPUT_PATH = "/outputSchema";
    private static final Set<String> DRAFT4_UNSUPPORTED = Set.of("const", "exclusiveMinimum", "exclusiveMaximum");
    private final ApitomyJsonSchemaCompatibilityChecker schemaChecker = new ApitomyJsonSchemaCompatibilityChecker();

    @Override
    protected Set<SimpleCompatibilityDifference> isBackwardsCompatibleWith(String existing,
            String proposed, Map<String, TypedContent> resolvedReferences) {
        JsonNode oldTool = parse(existing);
        JsonNode newTool = parse(proposed);
        // Check unchanged schemas too: unsupported assertions must not disappear through equality
        // shortcuts or through a successful comparison of the other side.
        for (JsonNode tool : List.of(oldTool, newTool)) {
            validate(tool.get(INPUT_SCHEMA), INPUT_PATH);
            if (tool.has(OUTPUT_SCHEMA)) {
                validate(tool.get(OUTPUT_SCHEMA), OUTPUT_PATH);
            }
        }
        Set<SimpleCompatibilityDifference> differences = new HashSet<>();
        compare(oldTool.get(INPUT_SCHEMA), newTool.get(INPUT_SCHEMA), CompatibilityLevel.BACKWARD,
                INPUT_PATH, differences);
        if (oldTool.has(OUTPUT_SCHEMA)) {
            if (!newTool.has(OUTPUT_SCHEMA)) {
                differences.add(new SimpleCompatibilityDifference("The output schema guarantee was removed.",
                        OUTPUT_PATH));
            } else {
                compare(oldTool.get(OUTPUT_SCHEMA), newTool.get(OUTPUT_SCHEMA), CompatibilityLevel.FORWARD,
                        OUTPUT_PATH, differences);
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
        boolean draft4 = VALIDATOR.draftOf(schema).filter(VersionFlag.V4::equals).isPresent();
        checkSupported(schema, path, draft4);
    }

    /**
     * Bound guarantees to the tested structural subset. Do not project away unknown assertions:
     * the publication rule has no 'indeterminate' success and must report them as 422.
     * Walk schema positions only, not instance-valued annotations, enums or property names.
     * Draft 4 has no 'const' (an unknown keyword there asserts nothing, so the meta-schema accepts it)
     * and uses boolean exclusive bounds; both are outside the tested subset for that draft.
     */
    private void checkSupported(JsonNode schema, String path, boolean draft4) {
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
                        checkSupported(property.getValue(), location + "/" + escape(property.getKey()), draft4));
            } else if (keyword.equals("items") || keyword.equals("additionalProperties")) {
                if (field.getValue().isArray()) {
                    throw new UnprocessableSchemaException("Unsupported MCP tool compatibility construct at " + location);
                }
                checkSupported(field.getValue(), location, draft4);
            } else if ((draft4 && DRAFT4_UNSUPPORTED.contains(keyword))
                    || (!SCALAR_KEYWORDS.contains(keyword) && !ANNOTATIONS.contains(keyword))) {
                throw new UnprocessableSchemaException("Unsupported MCP tool compatibility construct at " + location);
            } else if (SCALAR_KEYWORDS.contains(keyword)) {
                boolean instanceValue = keyword.equals("enum") || keyword.equals("const");
                checkNumbersAreDoubleExact(field.getValue(), location, instanceValue);
            }
        }
    }

    /**
     * The shared checker compares numbers as doubles, so two values that round to the same double
     * (for example 9007199254740992.0 and 9007199254740993.0) would compare as equal and a breaking
     * change would pass. Such values cannot be compared reliably, so they are reported as 422.
     * The exception is an integer inside an 'enum' or 'const' value, which the checker compares
     * exactly; integers in bounds and 'multipleOf' are rounded like decimals
     * (Apitomy/apitomy-data-models#1288).
     * Values such as 0.1 round-trip through a double unchanged and are accepted.
     */
    private static void checkNumbersAreDoubleExact(JsonNode value, String location, boolean instanceValue) {
        if (value.isNumber()) {
            if (instanceValue && value.isIntegralNumber()) {
                return;
            }
            BigDecimal exact = value.decimalValue();
            double rounded = exact.doubleValue();
            if (Double.isInfinite(rounded) || new BigDecimal(Double.toString(rounded)).compareTo(exact) != 0) {
                throw new UnprocessableSchemaException(
                        "MCP tool compatibility cannot compare a number beyond double precision at " + location);
            }
        } else if (value.isContainerNode()) {
            int index = 0;
            for (var entries = value.fields(); entries.hasNext();) {
                var entry = entries.next();
                checkNumbersAreDoubleExact(entry.getValue(), location + "/" + escape(entry.getKey()), instanceValue);
            }
            if (value.isArray()) {
                for (JsonNode element : value) {
                    checkNumbersAreDoubleExact(element, location + "/" + index++, instanceValue);
                }
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
