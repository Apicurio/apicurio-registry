package io.apicurio.registry.agents.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.ADDITIONAL_PROPERTIES;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.ITEMS;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.PROPERTIES;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.REQUIRED;
import static io.apicurio.registry.agents.mcptools.compatibility.SchemaProjector.TYPE;

/**
 * One schema node inside a tool document, holding the node as declared next to the projection
 * that the comparison engine evaluates, so that a difference found in the projection is reported
 * at a pointer into the declaration.
 *
 * @param original the node as declared, or a missing node when the tool document has none there
 * @param projected the evaluated keywords of the node, or a missing node
 * @param pointer JSON Pointer to the node, or to its closest declared ancestor when the tool
 *        document has no node there
 */
record SchemaNode(JsonNode original, JsonNode projected, String pointer) {

    /**
     * The node that a path of keywords and property names leads to, such as the segments of a
     * pointer the engine reports into the projection.
     */
    SchemaNode at(List<String> path) {
        SchemaNode node = this;
        for (String segment : path) {
            node = node.child(segment);
        }
        return node;
    }

    SchemaNode property(String name) {
        return child(PROPERTIES).child(name);
    }

    SchemaNode itemsSchema() {
        return child(ITEMS);
    }

    SchemaNode additionalPropertiesSchema() {
        return child(ADDITIONAL_PROPERTIES);
    }

    private SchemaNode child(String segment) {
        JsonNode declared = original.path(segment);
        return new SchemaNode(declared, projected.path(segment),
                declared.isMissingNode() ? pointer : JsonPointers.append(pointer, segment));
    }

    /**
     * Whether the node restricts the value to one type. Object keywords do not restrict it.
     */
    boolean declaresType() {
        return projected.path(TYPE).isTextual();
    }

    /**
     * Whether the node is an object that declares properties but leaves
     * {@code additionalProperties} unset, so that the engine assumes it can also emit undeclared
     * properties.
     */
    boolean openObject() {
        return isOpenObject(projected);
    }

    static boolean isOpenObject(JsonNode node) {
        JsonNode properties = node.path(PROPERTIES);
        return properties.isObject() && !properties.isEmpty() && !node.has(ADDITIONAL_PROPERTIES);
    }

    /**
     * Pointers to the open objects at or below this node, through properties, {@code items} and
     * {@code additionalProperties}.
     */
    List<String> openObjects() {
        List<String> pointers = new ArrayList<>();
        collectOpenObjects(pointers);
        return pointers;
    }

    private void collectOpenObjects(List<String> pointers) {
        if (!projected.isObject()) {
            return;
        }
        if (openObject()) {
            pointers.add(pointer);
        }
        for (String name : propertyNames()) {
            property(name).collectOpenObjects(pointers);
        }
        itemsSchema().collectOpenObjects(pointers);
        additionalPropertiesSchema().collectOpenObjects(pointers);
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
     * The projected {@code additionalProperties}, or {@code null} when the node does not declare
     * it.
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

    String requiredMemberPointer(String name) {
        JsonNode required = original.path(REQUIRED);
        for (int index = 0; index < required.size(); index++) {
            if (name.equals(required.get(index).asText())) {
                return JsonPointers.append(pointer, REQUIRED, String.valueOf(index));
            }
        }
        return requiredPointer();
    }

    String propertyPointer(String name) {
        return property(name).pointer();
    }

    /**
     * Points at the property's {@code type} keyword when it declares one, and at the property
     * subschema otherwise.
     */
    String propertyTypePointer(String name) {
        return property(name).typePointer();
    }

    /**
     * Points at the keyword when the node declares it, and at the node itself otherwise, so that
     * a pointer always addresses something in the tool document.
     */
    private String keywordPointer(String keyword) {
        return original.has(keyword) ? JsonPointers.append(pointer, keyword) : pointer;
    }
}
