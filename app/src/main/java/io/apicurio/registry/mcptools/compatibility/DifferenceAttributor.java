package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.Difference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Attributes a difference reported by the comparison engine to the properties it concerns.
 * Engine paths are not JSON Pointers: property names in them are not escaped, and some
 * differences summarize a set of properties without naming them, so properties are identified
 * from the two projections, never by splitting a path.
 */
final class DifferenceAttributor {

    private static final String ENGINE_ROOT = "";
    private static final String ENGINE_ROOT_TYPE = "/type";
    private static final String ENGINE_REQUIRED = "/required";
    private static final String ENGINE_ADDITIONAL_PROPERTIES = "/additionalProperties";
    private static final String ENGINE_ADDITIONAL_PROPERTIES_SCHEMA = "/schemaOfAdditionalItems";
    private static final String ENGINE_PROPERTIES_ONLY_IN_PRODUCER = "/propertySchemasRemoved";
    private static final String ENGINE_PROPERTIES_ONLY_IN_CONSUMER = "/propertySchemasAdded";
    private static final String ENGINE_PROPERTY_PREFIX = "/properties/";
    private static final String ENGINE_TYPE_SUFFIX = "/type";
    private static final String ENGINE_UNION_SIZE_SUFFIX = "/[size]";

    private final SchemaProjection producer;
    private final SchemaProjection consumer;
    private final BiFunction<JsonNode, JsonNode, Optional<Boolean>> accepts;

    /**
     * @param accepts decides whether every value the first subschema permits is accepted by the
     *        second, or returns empty when the two cannot be compared
     */
    DifferenceAttributor(SchemaProjection producer, SchemaProjection consumer,
            BiFunction<JsonNode, JsonNode, Optional<Boolean>> accepts) {
        this.producer = producer;
        this.consumer = consumer;
        this.accepts = accepts;
    }

    /**
     * Returns the reasons behind a difference, or empty when it cannot be attributed.
     */
    Optional<List<CompatibilityReason>> attribute(Difference difference) {
        String path = difference.getPathUpdated();
        return switch (path) {
            case ENGINE_ROOT, ENGINE_ROOT_TYPE -> Optional.of(List.of(new CompatibilityReason(
                    ReasonCode.TYPE_NOT_ACCEPTED, producer.typePointer(), consumer.typePointer(),
                    "The producer's output type is not accepted by the consumer")));
            case ENGINE_REQUIRED -> requiredMember(difference);
            case ENGINE_ADDITIONAL_PROPERTIES, ENGINE_ADDITIONAL_PROPERTIES_SCHEMA ->
                    undeclaredProperties();
            case ENGINE_PROPERTIES_ONLY_IN_PRODUCER -> propertiesOnlyInProducer();
            case ENGINE_PROPERTIES_ONLY_IN_CONSUMER -> propertiesOnlyInConsumer();
            default -> path.startsWith(ENGINE_PROPERTY_PREFIX) ? propertyInBoth(path)
                    : Optional.empty();
        };
    }

    private Optional<List<CompatibilityReason>> requiredMember(Difference difference) {
        String name = difference.getSubSchemaUpdated();
        if (difference.getDiffType() != DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED
                || !consumer.required().contains(name) || producer.required().contains(name)) {
            return Optional.empty();
        }
        String message = producer.declaresProperty(name)
                ? "Required input '" + name + "' is declared by the producer but not required"
                : "Required input '" + name + "' is not declared by the producer";
        return Optional.of(List.of(new CompatibilityReason(ReasonCode.REQUIRED_NOT_GUARANTEED,
                producer.requiredPointer(), consumer.requiredMemberPointer(name), message)));
    }

    private Optional<List<CompatibilityReason>> undeclaredProperties() {
        if (consumer.additionalProperties() == null) {
            return Optional.empty();
        }
        return Optional.of(List.of(new CompatibilityReason(
                ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED, producer.additionalPropertiesPointer(),
                consumer.additionalPropertiesPointer(),
                "The producer may emit undeclared properties that the consumer does not accept")));
    }

    /**
     * The engine reports only that the producer declares properties the consumer does not, so each
     * one is judged against the consumer's {@code additionalProperties}. The difference is
     * attributed once one of them has been judged, whether or not any was rejected: the engine
     * also reports this difference when the two sides merely write the same values differently.
     */
    private Optional<List<CompatibilityReason>> propertiesOnlyInProducer() {
        JsonNode consumerAdditional = consumer.additionalProperties();
        if (consumerAdditional == null) {
            return Optional.empty();
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
        boolean judged = false;
        for (String name : producer.propertyNames()) {
            JsonNode emitted = producer.projectedProperty(name);
            if (consumer.declaresProperty(name) || BooleanNode.FALSE.equals(emitted)) {
                continue;
            }
            Optional<Boolean> accepted = consumerAdditional.isBoolean()
                    ? Optional.of(consumerAdditional.booleanValue())
                    : accepts.apply(emitted, consumerAdditional);
            if (accepted.isEmpty()) {
                return Optional.empty();
            }
            judged = true;
            if (!accepted.get()) {
                reasons.add(new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                        producer.propertyPointer(name), consumer.additionalPropertiesPointer(),
                        "The producer may emit '" + name + "', which the consumer does not accept"));
            }
        }
        return judged ? Optional.of(reasons) : Optional.empty();
    }

    private Optional<List<CompatibilityReason>> propertiesOnlyInConsumer() {
        JsonNode producerAdditional = producer.additionalProperties();
        JsonNode emitted = producerAdditional == null ? BooleanNode.TRUE : producerAdditional;
        if (BooleanNode.FALSE.equals(emitted)) {
            return Optional.empty();
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
        boolean judged = false;
        for (String name : consumer.propertyNames()) {
            if (producer.declaresProperty(name)) {
                continue;
            }
            Optional<Boolean> accepted = accepts.apply(emitted, consumer.projectedProperty(name));
            if (accepted.isEmpty()) {
                return Optional.empty();
            }
            judged = true;
            if (!accepted.get()) {
                reasons.add(new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                        producer.additionalPropertiesPointer(), consumer.propertyTypePointer(name),
                        "The producer may emit '" + name + "' with a value the consumer does not accept"));
            }
        }
        return judged ? Optional.of(reasons) : Optional.empty();
    }

    /**
     * A union mismatch is reported either on the property itself or on the size of the union, so
     * both forms name the same property.
     */
    private Optional<List<CompatibilityReason>> propertyInBoth(String path) {
        List<String> matches = consumer.propertyNames().stream()
                .filter(producer::declaresProperty)
                .filter(name -> path.equals(ENGINE_PROPERTY_PREFIX + name)
                        || path.equals(ENGINE_PROPERTY_PREFIX + name + ENGINE_TYPE_SUFFIX)
                        || path.equals(ENGINE_PROPERTY_PREFIX + name + ENGINE_UNION_SIZE_SUFFIX))
                .toList();
        if (matches.size() != 1) {
            return Optional.empty();
        }
        String name = matches.get(0);
        return Optional.of(List.of(new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                producer.propertyTypePointer(name), consumer.propertyTypePointer(name),
                "The producer's value for '" + name + "' is not accepted by the consumer")));
    }
}
