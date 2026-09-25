package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.SpecVersionDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the draft-07 projection of a tool schema that the comparison engine evaluates. Keywords
 * outside the evaluated set are removed and recorded as limitations on the node that carries
 * them, so the keywords that remain mean the same thing in every supported dialect.
 */
final class SchemaProjector {

    static final String TYPE = "type";
    static final String PROPERTIES = "properties";
    static final String REQUIRED = "required";
    static final String ADDITIONAL_PROPERTIES = "additionalProperties";

    private static final String SCHEMA = "$schema";

    private static final Set<String> NON_SEMANTIC = Set.of("title", "description", "default",
            "examples", "$comment");

    private static final Set<String> NESTED_STRUCTURE = Set.of(PROPERTIES, REQUIRED, "items",
            ADDITIONAL_PROPERTIES);

    private SchemaProjector() {
    }

    /**
     * An absent {@code $schema} means JSON Schema 2020-12, the MCP default. A {@code $schema}
     * that is not a string, or names a dialect other than draft-04, draft-06, draft-07, 2019-09
     * or 2020-12, makes the whole schema a limitation and leaves nothing to compare.
     */
    static SchemaProjection project(JsonNode schema, String base, SchemaSide side) {
        List<CompatibilityLimitation> limitations = new ArrayList<>();

        JsonNode declaredDialect = schema.get(SCHEMA);
        if (declaredDialect != null) {
            String message = null;
            if (!declaredDialect.isTextual()) {
                message = "'$schema' must be a string";
            } else if (SpecVersionDetector.detectOptionalVersion(schema, false).isEmpty()) {
                message = "JSON Schema dialect '" + declaredDialect.asText() + "' is not supported";
            }
            if (message != null) {
                limitations.add(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_DIALECT, side,
                        base, JsonPointers.append(base, SCHEMA), message));
                return new SchemaProjection(side, base, schema, null, limitations);
            }
        }

        ObjectNode projected = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> field : schema.properties()) {
            String keyword = field.getKey();
            if (SCHEMA.equals(keyword) || NON_SEMANTIC.contains(keyword)) {
                continue;
            }
            JsonNode value = field.getValue();
            String pointer = JsonPointers.append(base, keyword);
            switch (keyword) {
                case TYPE -> projectType(value, base, pointer, side, projected, limitations);
                case PROPERTIES -> projected.set(PROPERTIES,
                        projectProperties(value, pointer, side, limitations));
                case REQUIRED -> projected.set(REQUIRED, value);
                case ADDITIONAL_PROPERTIES -> projected.set(ADDITIONAL_PROPERTIES,
                        projectSubschema(value, pointer, side, limitations));
                default -> limitations.add(unsupportedKeyword(side, base, pointer, keyword));
            }
        }
        return new SchemaProjection(side, base, schema, projected, limitations);
    }

    private static JsonNode projectProperties(JsonNode properties, String pointer, SchemaSide side,
            List<CompatibilityLimitation> limitations) {
        if (!properties.isObject()) {
            return properties;
        }
        ObjectNode projected = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> property : properties.properties()) {
            projected.set(property.getKey(), projectSubschema(property.getValue(),
                    JsonPointers.append(pointer, property.getKey()), side, limitations));
        }
        return projected;
    }

    /**
     * Boolean and malformed subschemas are kept as declared, for the engine to evaluate or reject.
     */
    private static JsonNode projectSubschema(JsonNode subschema, String node, SchemaSide side,
            List<CompatibilityLimitation> limitations) {
        if (!subschema.isObject()) {
            return subschema;
        }
        ObjectNode projected = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> field : subschema.properties()) {
            String keyword = field.getKey();
            String pointer = JsonPointers.append(node, keyword);
            if (TYPE.equals(keyword)) {
                projectType(field.getValue(), node, pointer, side, projected, limitations);
            } else if (NESTED_STRUCTURE.contains(keyword)) {
                limitations.add(new CompatibilityLimitation(LimitationCode.DEPTH_LIMIT_REACHED, side,
                        node, pointer, "Nested '" + keyword + "' is not evaluated yet"));
            } else if (!NON_SEMANTIC.contains(keyword)) {
                limitations.add(unsupportedKeyword(side, node, pointer, keyword));
            }
        }
        return projected;
    }

    private static void projectType(JsonNode type, String node, String pointer, SchemaSide side,
            ObjectNode projected, List<CompatibilityLimitation> limitations) {
        if (type.isArray()) {
            limitations.add(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, side, node,
                    pointer, "'type' given as an array is not evaluated yet"));
        } else {
            projected.set(TYPE, type);
        }
    }

    private static CompatibilityLimitation unsupportedKeyword(SchemaSide side, String node,
            String pointer, String keyword) {
        return new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, side, node, pointer,
                "'" + keyword + "' is not evaluated yet");
    }
}
