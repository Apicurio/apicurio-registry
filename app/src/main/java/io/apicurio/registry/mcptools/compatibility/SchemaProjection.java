package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ADDITIONAL_PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ITEMS;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.PROPERTIES;

/**
 * A tool schema together with the draft-07 projection that the comparison engine evaluates.
 *
 * @param side which tool schema this is
 * @param base JSON Pointer of the schema inside its tool document
 * @param original the schema as declared in the tool document
 * @param projected the evaluated keywords only, or {@code null} when the dialect is unsupported
 * @param limitations everything left out of the projection
 */
record SchemaProjection(SchemaSide side, String base, JsonNode original, ObjectNode projected,
        List<CompatibilityLimitation> limitations) {

    SchemaProjection {
        limitations = List.copyOf(limitations);
    }

    boolean dialectSupported() {
        return projected != null;
    }

    SchemaNode root() {
        return new SchemaNode(null, original, projected, base);
    }

    /**
     * Whether any object in the projection declares properties but leaves
     * {@code additionalProperties} unset, so that the engine assumes it can also emit undeclared
     * properties.
     */
    boolean closable() {
        return closable(projected);
    }

    ObjectNode closed() {
        return closed(projected);
    }

    /**
     * @return a copy of the projection with every object that declares properties, at any depth,
     *         emitting only those properties
     */
    static ObjectNode closed(ObjectNode projected) {
        ObjectNode closed = projected.deepCopy();
        close(closed);
        return closed;
    }

    private static boolean closable(JsonNode node) {
        if (!node.isObject()) {
            return false;
        }
        if (openObject(node)) {
            return true;
        }
        for (JsonNode property : node.path(PROPERTIES)) {
            if (closable(property)) {
                return true;
            }
        }
        return closable(node.path(ITEMS)) || closable(node.path(ADDITIONAL_PROPERTIES));
    }

    private static void close(JsonNode node) {
        if (!node.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        if (openObject(object)) {
            object.set(ADDITIONAL_PROPERTIES, BooleanNode.FALSE);
        }
        for (JsonNode property : object.path(PROPERTIES)) {
            close(property);
        }
        close(object.path(ITEMS));
        close(object.path(ADDITIONAL_PROPERTIES));
    }

    private static boolean openObject(JsonNode node) {
        JsonNode properties = node.path(PROPERTIES);
        return properties.isObject() && !properties.isEmpty() && !node.has(ADDITIONAL_PROPERTIES);
    }
}
