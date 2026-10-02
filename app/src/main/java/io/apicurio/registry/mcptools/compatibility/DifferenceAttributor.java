package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import io.apitomy.datamodels.jsonschema.compat.DiffType;
import io.apitomy.datamodels.jsonschema.compat.Difference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Attributes a difference reported by the comparison engine to the properties it concerns.
 * Engine paths are JSON Pointers into the consumer's projection, ending at the keyword that
 * changed. A difference in which properties are declared is reported at {@code /properties}
 * without naming them, so those properties are identified from the two projections.
 */
final class DifferenceAttributor {

    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String TYPE = "type";

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
        List<String> path = difference.getPathUpdated().segments();
        if (path.isEmpty() || path.equals(List.of(TYPE))) {
            return Optional.of(List.of(outputTypeNotAccepted()));
        }
        return switch (path.get(0)) {
            case REQUIRED -> requiredMember(difference, path);
            case ADDITIONAL_PROPERTIES -> undeclaredProperties();
            case PROPERTIES -> path.size() == 1 ? propertiesOnlyInOneSchema() : propertyInBoth(path.get(1));
            default -> Optional.empty();
        };
    }

    /**
     * The engine reads object keywords as an object schema, so a producer that declares no
     * {@code type} is compared as if it could emit nothing but objects, and no difference is
     * reported for the values it may emit beside them.
     */
    Optional<CompatibilityReason> unrestrictedOutputType() {
        if (producer.declaresType() || !consumer.declaresType()) {
            return Optional.empty();
        }
        return Optional.of(outputTypeNotAccepted());
    }

    private CompatibilityReason outputTypeNotAccepted() {
        return new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED, producer.typePointer(),
                consumer.typePointer(),
                "The producer may emit a value whose type the consumer does not accept");
    }

    private Optional<List<CompatibilityReason>> requiredMember(Difference difference, List<String> path) {
        if (difference.getDiffType() != DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED || path.size() != 2) {
            return Optional.empty();
        }
        String name = memberName(consumer.projected().path(REQUIRED), path.get(1));
        if (name == null || !consumer.required().contains(name) || producer.required().contains(name)) {
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
     * A difference in which properties are declared can come from either schema, so both are
     * checked. Empty when it can't be explained.
     */
    private Optional<List<CompatibilityReason>> propertiesOnlyInOneSchema() {
        Optional<List<CompatibilityReason>> inProducer = propertiesOnlyInProducer();
        Optional<List<CompatibilityReason>> inConsumer = propertiesOnlyInConsumer();
        if (inProducer.isEmpty() || inConsumer.isEmpty()) {
            return Optional.empty();
        }
        List<CompatibilityReason> reasons = new ArrayList<>(inProducer.get());
        reasons.addAll(inConsumer.get());
        return reasons.isEmpty() ? Optional.empty() : Optional.of(reasons);
    }

    /**
     * Reasons from properties only the producer declares: an empty list if there are none, or
     * empty if they can't be decided.
     */
    private Optional<List<CompatibilityReason>> propertiesOnlyInProducer() {
        JsonNode consumerAdditional = consumer.additionalProperties();
        if (consumerAdditional == null) {
            return Optional.of(List.of());
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
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
            if (!accepted.get()) {
                reasons.add(new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                        producer.propertyPointer(name), consumer.additionalPropertiesPointer(),
                        "The producer may emit '" + name + "', which the consumer does not accept"));
            }
        }
        return Optional.of(reasons);
    }

    /**
     * Reasons from properties only the consumer declares: an empty list if there are none, or
     * empty if they can't be decided.
     */
    private Optional<List<CompatibilityReason>> propertiesOnlyInConsumer() {
        JsonNode producerAdditional = producer.additionalProperties();
        JsonNode emitted = producerAdditional == null ? BooleanNode.TRUE : producerAdditional;
        if (BooleanNode.FALSE.equals(emitted)) {
            return Optional.of(List.of());
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
        for (String name : consumer.propertyNames()) {
            if (producer.declaresProperty(name)) {
                continue;
            }
            Optional<Boolean> accepted = accepts.apply(emitted, consumer.projectedProperty(name));
            if (accepted.isEmpty()) {
                return Optional.empty();
            }
            if (!accepted.get()) {
                reasons.add(new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                        producer.additionalPropertiesPointer(), consumer.propertyTypePointer(name),
                        "The producer may emit '" + name + "' with a value the consumer does not accept"));
            }
        }
        return Optional.of(reasons);
    }

    private Optional<List<CompatibilityReason>> propertyInBoth(String name) {
        if (!consumer.declaresProperty(name) || !producer.declaresProperty(name)) {
            return Optional.empty();
        }
        return Optional.of(List.of(new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                producer.propertyTypePointer(name), consumer.propertyTypePointer(name),
                "The producer's value for '" + name + "' is not accepted by the consumer")));
    }

    /** The string at {@code index} in a {@code required} array, or {@code null}. */
    private static String memberName(JsonNode required, String index) {
        try {
            JsonNode member = required.get(Integer.parseInt(index));
            return member != null && member.isTextual() ? member.asText() : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
