package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * them, so the keywords that remain mean the same thing in every supported dialect. The one
 * keyword removed without a limitation is {@code format} on the producer, which cannot change a
 * verdict while no evaluated consumer keyword constrains the contents of a value.
 *
 * <p>Objects and arrays are projected to {@link #MAX_SCHEMA_DEPTH} levels below the schema root.
 * Structure deeper than that is removed and reported as a
 * {@link LimitationCode#DEPTH_LIMIT_REACHED} limitation, so that the comparison walks a bounded
 * part of any schema, however deeply it nests.
 */
final class SchemaProjector {

    /**
     * How many levels of objects and arrays below the schema root are projected. The deepest of
     * the 125 tool schemas published by the GitHub MCP server nests three levels, an array of
     * objects whose properties are scalars, so this leaves two levels of headroom.
     */
    static final int MAX_SCHEMA_DEPTH = 5;

    static final String TYPE = "type";
    static final String PROPERTIES = "properties";
    static final String REQUIRED = "required";
    static final String ITEMS = "items";
    static final String ADDITIONAL_PROPERTIES = "additionalProperties";

    private static final String SCHEMA = "$schema";
    private static final String FORMAT = "format";
    private static final String NUMBER = "number";
    private static final String INTEGER = "integer";

    private static final int ROOT_DEPTH = 0;

    private static final Set<String> NON_SEMANTIC = Set.of("title", "description", "default",
            "examples", "$comment");

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

        return new SchemaProjection(side, base, schema,
                projectNode(schema, base, side, ROOT_DEPTH, limitations), limitations);
    }

    private static ObjectNode projectNode(JsonNode schema, String node, SchemaSide side, int depth,
            List<CompatibilityLimitation> limitations) {
        ObjectNode projected = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> field : schema.properties()) {
            String keyword = field.getKey();
            if (NON_SEMANTIC.contains(keyword) || (depth == ROOT_DEPTH && SCHEMA.equals(keyword))) {
                continue;
            }
            JsonNode value = field.getValue();
            String pointer = JsonPointers.append(node, keyword);
            switch (keyword) {
                case TYPE -> projectType(value, node, pointer, side, depth, projected, limitations);
                case REQUIRED -> projected.set(REQUIRED, value);
                case PROPERTIES ->
                        projectProperties(value, node, pointer, side, depth, projected, limitations);
                case ITEMS -> projectItems(value, node, pointer, side, depth, projected, limitations);
                case ADDITIONAL_PROPERTIES ->
                        projectNested(value, ADDITIONAL_PROPERTIES, node, pointer, side, depth,
                                projected, limitations);
                default -> {
                    if (!droppedFromProducer(side, keyword)) {
                        limitations.add(unsupportedKeyword(side, node, pointer, keyword));
                    }
                }
            }
        }
        return projected;
    }

    private static void projectProperties(JsonNode properties, String node, String pointer,
            SchemaSide side, int depth, ObjectNode projected,
            List<CompatibilityLimitation> limitations) {
        if (!properties.isObject()) {
            projected.set(PROPERTIES, properties);
            return;
        }
        if (depth == MAX_SCHEMA_DEPTH) {
            limitations.add(depthLimitReached(side, node, pointer, PROPERTIES));
            return;
        }
        ObjectNode projectedProperties = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> property : properties.properties()) {
            projectedProperties.set(property.getKey(),
                    projectSubschema(property.getValue(),
                            JsonPointers.append(pointer, property.getKey()), side, depth + 1,
                            limitations));
        }
        projected.set(PROPERTIES, projectedProperties);
    }

    /**
     * Only a single schema is evaluated. The tuple form, a boolean and anything else are removed
     * with a limitation, rather than replaced by a schema that would accept more than they do.
     */
    private static void projectItems(JsonNode items, String node, String pointer, SchemaSide side,
            int depth, ObjectNode projected, List<CompatibilityLimitation> limitations) {
        if (!items.isObject()) {
            limitations.add(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, side, node,
                    pointer, "'items' is evaluated only as a single schema"));
            return;
        }
        projectNested(items, ITEMS, node, pointer, side, depth, projected, limitations);
    }

    private static void projectNested(JsonNode subschema, String keyword, String node, String pointer,
            SchemaSide side, int depth, ObjectNode projected,
            List<CompatibilityLimitation> limitations) {
        if (subschema.isObject() && depth == MAX_SCHEMA_DEPTH) {
            limitations.add(depthLimitReached(side, node, pointer, keyword));
            return;
        }
        projected.set(keyword, projectSubschema(subschema, pointer, side, depth + 1, limitations));
    }

    /**
     * Boolean and malformed subschemas are kept as declared, for the engine to evaluate or reject.
     */
    private static JsonNode projectSubschema(JsonNode subschema, String node, SchemaSide side,
            int depth, List<CompatibilityLimitation> limitations) {
        return subschema.isObject() ? projectNode(subschema, node, side, depth, limitations)
                : subschema;
    }

    /**
     * Reduces a union to the types it accepts: duplicates are dropped, and {@code integer} is
     * dropped beside {@code number} because every integer is a number. A union left with one type
     * is written as that type, so that two nodes accepting the same values are written the same
     * way. A list that does not name types is not evaluated, and a {@code type} that is neither a
     * list nor a name is left for the engine to reject. On the schema root a union is not
     * evaluated at all.
     */
    private static void projectType(JsonNode type, String node, String pointer, SchemaSide side,
            int depth, ObjectNode projected, List<CompatibilityLimitation> limitations) {
        if (!type.isArray()) {
            projected.set(TYPE, type);
            return;
        }
        if (depth == ROOT_DEPTH) {
            limitations.add(new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, side, node,
                    pointer, "'type' given as an array is not evaluated yet"));
            return;
        }
        List<String> names = new ArrayList<>();
        for (JsonNode entry : type) {
            if (!entry.isTextual()) {
                limitations.add(unnamedTypes(side, node, pointer));
                return;
            }
            if (!names.contains(entry.textValue())) {
                names.add(entry.textValue());
            }
        }
        if (names.isEmpty()) {
            limitations.add(unnamedTypes(side, node, pointer));
            return;
        }
        if (names.contains(NUMBER)) {
            names.remove(INTEGER);
        }
        if (names.size() == 1) {
            projected.put(TYPE, names.get(0));
        } else {
            ArrayNode union = projected.putArray(TYPE);
            names.forEach(union::add);
        }
    }

    /**
     * {@code format} on the producer only narrows what it emits, and no evaluated consumer keyword
     * constrains the contents of a value, so dropping it cannot turn an incompatible pair into a
     * compatible one. It becomes a limitation again as soon as a keyword such as {@code pattern}
     * is evaluated.
     */
    private static boolean droppedFromProducer(SchemaSide side, String keyword) {
        return side == SchemaSide.PRODUCER && FORMAT.equals(keyword);
    }

    private static CompatibilityLimitation depthLimitReached(SchemaSide side, String node,
            String pointer, String keyword) {
        return new CompatibilityLimitation(LimitationCode.DEPTH_LIMIT_REACHED, side, node, pointer,
                "'" + keyword + "' nests deeper than the comparison evaluates");
    }

    private static CompatibilityLimitation unnamedTypes(SchemaSide side, String node, String pointer) {
        return new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, side, node, pointer,
                "'type' does not list type names");
    }

    private static CompatibilityLimitation unsupportedKeyword(SchemaSide side, String node,
            String pointer, String keyword) {
        return new CompatibilityLimitation(LimitationCode.UNSUPPORTED_KEYWORD, side, node, pointer,
                "'" + keyword + "' is not evaluated yet");
    }
}
