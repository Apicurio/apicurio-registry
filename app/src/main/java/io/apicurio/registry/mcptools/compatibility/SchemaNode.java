package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ADDITIONAL_PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ITEMS;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.REQUIRED;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.TYPE;

/**
 * One schema node inside a tool document, holding the node as declared next to the projection the
 * comparison engine evaluates, so that a difference found in the projection is reported at the
 * pointer that addresses the declaration.
 *
 * @param name the property name this node was reached by, or {@code null} for a schema root and
 *        for the items of an array
 * @param original the node as declared in the tool document
 * @param projected the evaluated keywords of the node
 * @param pointer JSON Pointer to the node inside the tool document
 */
record SchemaNode(String name, JsonNode original, JsonNode projected, String pointer) {

    private static final Set<String> STRUCTURED_TYPES = Set.of("object", "array");

    SchemaNode property(String property) {
        return new SchemaNode(property, original.path(PROPERTIES).path(property),
                projected.path(PROPERTIES).path(property),
                JsonPointers.append(pointer, PROPERTIES, property));
    }

    SchemaNode items() {
        return new SchemaNode(null, original.path(ITEMS), projected.path(ITEMS), itemsPointer());
    }

    SchemaNode additionalPropertiesSchema() {
        return new SchemaNode(null, original.path(ADDITIONAL_PROPERTIES),
                projected.path(ADDITIONAL_PROPERTIES), additionalPropertiesPointer());
    }

    /**
     * Whether the node restricts the value to the types it names. Object and array keywords do not
     * restrict it: the engine reads them as a type, but a value of any other type satisfies them.
     */
    boolean declaresType() {
        return declaresType(projected);
    }

    static boolean declaresType(JsonNode subschema) {
        JsonNode type = subschema.path(TYPE);
        return type.isTextual() || (type.isArray() && !type.isEmpty());
    }

    /**
     * Whether the subschema can hold an object or an array, which are the only values that the
     * evaluated keywords constrain.
     */
    static boolean declaresStructuredType(JsonNode subschema) {
        JsonNode type = subschema.path(TYPE);
        if (type.isTextual()) {
            return STRUCTURED_TYPES.contains(type.textValue());
        }
        for (JsonNode name : type) {
            if (STRUCTURED_TYPES.contains(name.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the node permits no value at all, which a schema states by being {@code false}.
     */
    boolean permitsNothing() {
        return projected.isBoolean() && !projected.booleanValue();
    }

    boolean declaresItems() {
        return projected.path(ITEMS).isObject();
    }

    boolean declaresAdditionalPropertiesSchema() {
        return projected.path(ADDITIONAL_PROPERTIES).isObject();
    }

    List<String> propertyNames() {
        List<String> names = new ArrayList<>();
        JsonNode properties = projected.path(PROPERTIES);
        if (properties.isObject()) {
            properties.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    boolean declaresProperty(String property) {
        return projected.path(PROPERTIES).has(property);
    }

    JsonNode projectedProperty(String property) {
        return projected.path(PROPERTIES).get(property);
    }

    List<String> required() {
        List<String> required = new ArrayList<>();
        for (JsonNode member : original.path(REQUIRED)) {
            if (member.isTextual()) {
                required.add(member.asText());
            }
        }
        return required;
    }

    /**
     * The projected {@code additionalProperties}, or {@code null} when the node does not declare it.
     */
    JsonNode additionalProperties() {
        return projected.get(ADDITIONAL_PROPERTIES);
    }

    String typePointer() {
        return keywordPointer(TYPE);
    }

    String requiredPointer() {
        return keywordPointer(REQUIRED);
    }

    String additionalPropertiesPointer() {
        return keywordPointer(ADDITIONAL_PROPERTIES);
    }

    String itemsPointer() {
        return keywordPointer(ITEMS);
    }

    String requiredMemberPointer(String member) {
        JsonNode required = original.path(REQUIRED);
        for (int index = 0; index < required.size(); index++) {
            if (member.equals(required.get(index).asText())) {
                return JsonPointers.append(pointer, REQUIRED, String.valueOf(index));
            }
        }
        return requiredPointer();
    }

    /**
     * Points at the keyword when the node declares it, and at the node itself otherwise, so that a
     * pointer always addresses something that exists in the tool document.
     */
    private String keywordPointer(String keyword) {
        return original.has(keyword) ? JsonPointers.append(pointer, keyword) : pointer;
    }
}
