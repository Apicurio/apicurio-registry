package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.ADDITIONAL_PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.PROPERTIES;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.REQUIRED;
import static io.apicurio.registry.mcptools.compatibility.SchemaProjector.TYPE;

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

    /**
     * Whether the root object declares properties but leaves {@code additionalProperties}
     * unset, so that the engine assumes it can also emit undeclared properties.
     */
    boolean closable() {
        JsonNode properties = projected.get(PROPERTIES);
        return properties != null && properties.isObject() && !properties.isEmpty()
                && !projected.has(ADDITIONAL_PROPERTIES);
    }

    ObjectNode closed() {
        ObjectNode closed = projected.deepCopy();
        closed.set(ADDITIONAL_PROPERTIES, BooleanNode.FALSE);
        return closed;
    }

    List<String> propertyNames() {
        List<String> names = new ArrayList<>();
        JsonNode properties = projected.path(PROPERTIES);
        if (properties.isObject()) {
            properties.fieldNames().forEachRemaining(names::add);
        }
        return names;
    }

    boolean declaresProperty(String name) {
        return projected.path(PROPERTIES).has(name);
    }

    JsonNode projectedProperty(String name) {
        return projected.path(PROPERTIES).get(name);
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
     * The projected {@code additionalProperties}, or {@code null} when it is not declared.
     */
    JsonNode additionalProperties() {
        return projected.get(ADDITIONAL_PROPERTIES);
    }

    String rootPointer() {
        return base;
    }

    String typePointer() {
        return original.has(TYPE) ? JsonPointers.append(base, TYPE) : base;
    }

    String requiredPointer() {
        return original.has(REQUIRED) ? JsonPointers.append(base, REQUIRED) : base;
    }

    String requiredMemberPointer(String name) {
        JsonNode required = original.path(REQUIRED);
        for (int index = 0; index < required.size(); index++) {
            if (name.equals(required.get(index).asText())) {
                return JsonPointers.append(base, REQUIRED, String.valueOf(index));
            }
        }
        return requiredPointer();
    }

    String additionalPropertiesPointer() {
        return original.has(ADDITIONAL_PROPERTIES) ? JsonPointers.append(base, ADDITIONAL_PROPERTIES)
                : base;
    }

    String propertyPointer(String name) {
        return JsonPointers.append(base, PROPERTIES, name);
    }

    /**
     * Points at the property's {@code type} keyword when it declares one, and at the property
     * subschema otherwise.
     */
    String propertyTypePointer(String name) {
        JsonNode property = original.path(PROPERTIES).path(name);
        return property.has(TYPE) ? JsonPointers.append(base, PROPERTIES, name, TYPE)
                : propertyPointer(name);
    }
}
