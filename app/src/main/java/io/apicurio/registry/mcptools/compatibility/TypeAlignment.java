package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ADDITIONAL_PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.TYPE;

/**
 * Writes a single {@code type} as a one-entry union wherever the schema it is compared with
 * declares a union at the same node. The engine compares a union against a single type by shape
 * rather than by what each one accepts, so the two forms are not interchangeable: a producer
 * emitting {@code "string"} is reported incompatible with a consumer accepting
 * {@code ["string","null"]} until both sides are written the same way.
 */
final class TypeAlignment {

    private TypeAlignment() {
    }

    /**
     * Realigns the declared properties and {@code additionalProperties} of a projection against
     * the schema it will be compared with.
     *
     * @return the realigned projection, or empty when every node already matches
     */
    static Optional<ObjectNode> realign(ObjectNode projected, ObjectNode peer) {
        ObjectNode realigned = null;
        for (Map.Entry<String, JsonNode> property : projected.path(PROPERTIES).properties()) {
            JsonNode aligned = realignSubschema(property.getValue(),
                    peer.path(PROPERTIES).path(property.getKey()));
            if (aligned != property.getValue()) {
                realigned = realigned == null ? projected.deepCopy() : realigned;
                ((ObjectNode) realigned.get(PROPERTIES)).set(property.getKey(), aligned);
            }
        }
        JsonNode additional = projected.get(ADDITIONAL_PROPERTIES);
        if (additional != null) {
            JsonNode aligned = realignSubschema(additional, peer.path(ADDITIONAL_PROPERTIES));
            if (aligned != additional) {
                realigned = realigned == null ? projected.deepCopy() : realigned;
                realigned.set(ADDITIONAL_PROPERTIES, aligned);
            }
        }
        return Optional.ofNullable(realigned);
    }

    /**
     * @return the subschema written as the peer writes its {@code type}, or the same instance when
     *         it already is
     */
    static JsonNode realignSubschema(JsonNode subschema, JsonNode peer) {
        JsonNode type = subschema.path(TYPE);
        if (!type.isTextual() || !peer.path(TYPE).isArray()) {
            return subschema;
        }
        ObjectNode realigned = ((ObjectNode) subschema).deepCopy();
        realigned.set(TYPE, JsonNodeFactory.instance.arrayNode().add(type.textValue()));
        return realigned;
    }
}
