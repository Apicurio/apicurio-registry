package io.apicurio.registry.agents.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.ADDITIONAL_PROPERTIES;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.ITEMS;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.PROPERTIES;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.REQUIRED;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.TYPE;

/**
 * Narrows each consumer node to the types the producer can emit into it. Only producer values
 * reach the consumer, so this changes nothing the consumer accepts, but it keeps the engine from
 * two misreadings:
 * <ul>
 * <li>When the consumer's list names a type the producer's does not, the engine records a
 * widening and compares nothing below that node, reading {@code integer} as {@code number} on the
 * way. A producer object is then accepted by a consumer {@code ["object","null"]} whose properties
 * reject it, and a producer {@code number} by a consumer {@code ["integer","null"]}.</li>
 * <li>The engine reads object keywords as an object type, so a consumer node without a
 * {@code type} is taken to reject the strings or arrays a producer may emit, which it accepts.</li>
 * </ul>
 * A consumer node therefore keeps only the types the producer can emit, takes the producer's types
 * when it declares none, and drops the object or array keywords when the producer emits no object
 * or no array, since they constrain nothing else.
 *
 * <p>A node fed by one producer node is narrowed together with everything below it: the root, a
 * property both declare, {@code items} on both sides, and a property only the consumer declares,
 * fed by the producer's {@code additionalProperties}. The consumer's {@code additionalProperties}
 * can be fed by several producer properties, so only its own types are narrowed, to those of all
 * of them, and it is compared with each of them separately.
 */
final class TypeNarrowing {

    private static final String OBJECT = "object";
    private static final String ARRAY = "array";
    private static final String INTEGER = "integer";
    private static final String NUMBER = "number";

    private TypeNarrowing() {
    }

    /**
     * @return a narrowed copy of the consumer schema, or the consumer schema itself when it is not
     *         an object
     */
    static JsonNode narrow(JsonNode emitted, JsonNode accepted) {
        if (!accepted.isObject()) {
            return accepted;
        }
        ObjectNode narrowed = ((ObjectNode) accepted).deepCopy();
        narrow(List.of(emitted), narrowed);
        return narrowed;
    }

    private static void narrow(List<JsonNode> sources, ObjectNode accepted) {
        List<String> emittedTypes = typesOf(sources);
        if (emittedTypes != null) {
            narrowType(emittedTypes, accepted);
        }
        if (sources.size() != 1) {
            return;
        }
        JsonNode emitted = sources.get(0);
        JsonNode emittedProperties = emitted.path(PROPERTIES);
        JsonNode emittedAdditional = emitted.path(ADDITIONAL_PROPERTIES);
        for (Map.Entry<String, JsonNode> property : accepted.path(PROPERTIES).properties()) {
            if (property.getValue().isObject()) {
                JsonNode source = emittedProperties.has(property.getKey())
                        ? emittedProperties.get(property.getKey()) : emittedAdditional;
                narrow(List.of(source), (ObjectNode) property.getValue());
            }
        }
        if (accepted.path(ITEMS).isObject()) {
            narrow(List.of(emitted.path(ITEMS)), (ObjectNode) accepted.get(ITEMS));
        }
        if (accepted.path(ADDITIONAL_PROPERTIES).isObject()) {
            List<JsonNode> undeclared = new ArrayList<>();
            for (Map.Entry<String, JsonNode> property : emittedProperties.properties()) {
                if (!accepted.path(PROPERTIES).has(property.getKey())) {
                    undeclared.add(property.getValue());
                }
            }
            undeclared.add(emittedAdditional);
            List<String> undeclaredTypes = typesOf(undeclared);
            if (undeclaredTypes != null) {
                narrowType(undeclaredTypes, (ObjectNode) accepted.get(ADDITIONAL_PROPERTIES));
            }
        }
    }

    /**
     * Keeps the consumer's types that a producer value can have. An integer is a number, so a
     * producer {@code number} can be accepted as an {@code integer} and a producer {@code integer}
     * as a {@code number}. A consumer that keeps none of its types is left as declared, since the
     * engine reports that difference itself.
     */
    private static void narrowType(List<String> emittedTypes, ObjectNode accepted) {
        if (emittedTypes.isEmpty()) {
            return;
        }
        List<String> acceptedTypes = typeNames(accepted.path(TYPE));
        if (acceptedTypes.isEmpty()) {
            setTypes(accepted, emittedTypes);
        } else {
            List<String> kept = new ArrayList<>();
            for (String type : acceptedTypes) {
                if (emittedTypes.contains(type) || (INTEGER.equals(type) && emittedTypes.contains(NUMBER))
                        || (NUMBER.equals(type) && emittedTypes.contains(INTEGER))) {
                    kept.add(type);
                }
            }
            if (kept.isEmpty()) {
                return;
            }
            if (kept.size() < acceptedTypes.size()) {
                setTypes(accepted, kept);
            }
        }
        if (!emittedTypes.contains(OBJECT)) {
            accepted.remove(List.of(PROPERTIES, REQUIRED, ADDITIONAL_PROPERTIES));
        }
        if (!emittedTypes.contains(ARRAY)) {
            accepted.remove(ITEMS);
        }
    }

    private static void setTypes(ObjectNode accepted, List<String> types) {
        if (types.size() == 1) {
            accepted.put(TYPE, types.get(0));
        } else {
            ArrayNode union = JsonNodeFactory.instance.arrayNode();
            types.forEach(union::add);
            accepted.set(TYPE, union);
        }
    }

    /**
     * The types the producer nodes can emit together, or {@code null} when one of them declares
     * none and can emit anything. A node declared {@code false} emits nothing.
     */
    private static List<String> typesOf(List<JsonNode> sources) {
        List<String> types = new ArrayList<>();
        for (JsonNode source : sources) {
            if (BooleanNode.FALSE.equals(source)) {
                continue;
            }
            List<String> names = typeNames(source.path(TYPE));
            if (names.isEmpty()) {
                return null;
            }
            names.stream().filter(name -> !types.contains(name)).forEach(types::add);
        }
        return types;
    }

    private static List<String> typeNames(JsonNode type) {
        List<String> names = new ArrayList<>();
        if (type.isTextual()) {
            names.add(type.textValue());
        } else if (type.isArray()) {
            type.forEach(name -> names.add(name.asText()));
        }
        return names;
    }
}
