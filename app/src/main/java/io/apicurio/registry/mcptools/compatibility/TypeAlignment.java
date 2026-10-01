package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ADDITIONAL_PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ITEMS;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.TYPE;

/**
 * Writes a single {@code type} as a one-entry union wherever the schema it is compared with
 * declares a union at the same node. The engine compares a union against a single type by shape
 * rather than by what each one accepts, so the two forms are not interchangeable: a producer
 * emitting {@code "string"} is reported incompatible with a consumer accepting
 * {@code ["string","null"]} until both sides are written the same way.
 *
 * <p>A union is written only where one of the two sides declares one. At a node that also
 * declares {@code properties} or {@code items}, the engine reports a difference found inside the
 * node on the node itself, so writing a union everywhere would lose the location of every nested
 * mismatch.
 */
final class TypeAlignment {

    private TypeAlignment() {
    }

    /**
     * Realigns a whole projection against the schema it will be compared with.
     *
     * @return the realigned projection, or empty when every node already matches
     */
    static Optional<ObjectNode> realign(ObjectNode projected, ObjectNode peer) {
        JsonNode realigned = align(projected, peer);
        return realigned == projected ? Optional.empty() : Optional.of((ObjectNode) realigned);
    }

    /**
     * @return the subschema written as the peer writes its {@code type} at every node they share,
     *         or the same instance when it already is
     */
    static JsonNode align(JsonNode subschema, JsonNode peer) {
        if (!subschema.isObject()) {
            return subschema;
        }
        ObjectNode realigned = null;
        JsonNode type = subschema.path(TYPE);
        if (type.isTextual() && peer.path(TYPE).isArray()) {
            realigned = subschema.deepCopy();
            realigned.set(TYPE, JsonNodeFactory.instance.arrayNode().add(type.textValue()));
        }
        for (Map.Entry<String, JsonNode> property : subschema.path(PROPERTIES).properties()) {
            JsonNode aligned = align(property.getValue(),
                    peer.path(PROPERTIES).path(property.getKey()));
            if (aligned != property.getValue()) {
                realigned = realigned == null ? subschema.deepCopy() : realigned;
                ((ObjectNode) realigned.get(PROPERTIES)).set(property.getKey(), aligned);
            }
        }
        realigned = alignKeyword(subschema, peer, ITEMS, realigned);
        realigned = alignKeyword(subschema, peer, ADDITIONAL_PROPERTIES, realigned);
        return realigned == null ? subschema : realigned;
    }

    private static ObjectNode alignKeyword(JsonNode subschema, JsonNode peer, String keyword,
            ObjectNode realigned) {
        JsonNode declared = subschema.get(keyword);
        if (declared == null) {
            return realigned;
        }
        JsonNode aligned = align(declared, peer.path(keyword));
        if (aligned == declared) {
            return realigned;
        }
        ObjectNode result = realigned == null ? subschema.deepCopy() : realigned;
        result.set(keyword, aligned);
        return result;
    }
}
