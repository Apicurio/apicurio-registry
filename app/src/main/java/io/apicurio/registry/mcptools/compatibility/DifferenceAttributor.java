package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.Difference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Attributes a difference reported by the comparison engine to the schema node it concerns.
 * Engine paths are not JSON Pointers: the names in them are not escaped, the items of an array
 * are named {@code allItemSchema}, and some differences summarize a set of properties without
 * naming them. A path is therefore walked against both projections rather than split into
 * segments, and the nodes it leads to carry the pointers reported to the caller.
 */
final class DifferenceAttributor {

    private static final String ENGINE_NODE = "";
    private static final String ENGINE_TYPE = "/type";
    private static final String ENGINE_UNION_SIZE = "/[size]";
    private static final String ENGINE_REQUIRED = "/required";
    private static final String ENGINE_ADDITIONAL_PROPERTIES = "/additionalProperties";
    private static final String ENGINE_ADDITIONAL_PROPERTIES_SCHEMA = "/schemaOfAdditionalItems";
    private static final String ENGINE_PROPERTIES_ONLY_IN_PRODUCER = "/propertySchemasRemoved";
    private static final String ENGINE_PROPERTIES_ONLY_IN_CONSUMER = "/propertySchemasAdded";
    private static final String ENGINE_PROPERTY_PREFIX = "/properties/";
    private static final String ENGINE_ITEMS = "/allItemSchema";

    private static final Set<String> ENGINE_KEYWORDS = Set.of(ENGINE_NODE, ENGINE_TYPE,
            ENGINE_UNION_SIZE, ENGINE_REQUIRED, ENGINE_ADDITIONAL_PROPERTIES,
            ENGINE_ADDITIONAL_PROPERTIES_SCHEMA, ENGINE_PROPERTIES_ONLY_IN_PRODUCER,
            ENGINE_PROPERTIES_ONLY_IN_CONSUMER);

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
        Optional<NodePair> resolved = resolve(difference.getPathUpdated());
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        NodePair node = resolved.get();
        return switch (node.keyword()) {
            case ENGINE_NODE, ENGINE_TYPE, ENGINE_UNION_SIZE ->
                    Optional.of(List.of(typeNotAccepted(node)));
            case ENGINE_REQUIRED -> requiredMember(difference, node);
            case ENGINE_ADDITIONAL_PROPERTIES, ENGINE_ADDITIONAL_PROPERTIES_SCHEMA ->
                    undeclaredProperties(node);
            case ENGINE_PROPERTIES_ONLY_IN_PRODUCER -> propertiesOnlyInProducer(node);
            case ENGINE_PROPERTIES_ONLY_IN_CONSUMER -> propertiesOnlyInConsumer(node);
            default -> Optional.empty();
        };
    }

    /**
     * The pointer to the producer node a difference was found at, used to report which object was
     * left open when closing the producer removes the difference. The engine reports everything
     * it finds inside an {@code additionalProperties} schema on the keyword itself, so a
     * difference there is reported against that schema rather than against the node holding it.
     */
    Optional<String> producerNode(Difference difference) {
        return resolve(difference.getPathUpdated()).map(node -> switch (node.keyword()) {
            case ENGINE_ADDITIONAL_PROPERTIES, ENGINE_ADDITIONAL_PROPERTIES_SCHEMA ->
                    node.producer().additionalPropertiesPointer();
            default -> node.producer().pointer();
        });
    }

    /**
     * The mismatches the engine can leave unreported. It reads the object and array keywords as a
     * type, so a node that declares no {@code type} is compared as if it could emit nothing else,
     * and it does not always report a property that one side alone declares, whose values the
     * other side takes through its {@code additionalProperties}. Both are decided here, for every
     * node the two schemas share.
     */
    List<CompatibilityReason> unreportedMismatches() {
        List<CompatibilityReason> reasons = new ArrayList<>();
        collectUnreported(new NodePair(producer.root(), consumer.root(), ENGINE_NODE), reasons);
        return reasons;
    }

    private void collectUnreported(NodePair node, List<CompatibilityReason> reasons) {
        if (node.consumer().declaresType() && !node.producer().declaresType()
                && !node.producer().permitsNothing()) {
            reasons.add(typeNotAccepted(node));
        }
        for (String property : node.producer().propertyNames()) {
            if (node.consumer().declaresProperty(property)) {
                collectUnreported(node.property(property), reasons);
            } else {
                declaredByProducerAlone(node, property).ifPresent(reasons::add);
            }
        }
        for (String property : node.consumer().propertyNames()) {
            if (!node.producer().declaresProperty(property)) {
                declaredByConsumerAlone(node, property).ifPresent(reasons::add);
            }
        }
        if (node.producer().declaresItems() && node.consumer().declaresItems()) {
            collectUnreported(node.items(), reasons);
        }
        if (node.producer().declaresAdditionalPropertiesSchema()
                && node.consumer().declaresAdditionalPropertiesSchema()) {
            collectUnreported(node.additionalProperties(), reasons);
        }
    }

    /**
     * A property that only the producer declares is taken by the consumer's
     * {@code additionalProperties}. An absent one accepts every value, so there is nothing to
     * decide.
     */
    private Optional<CompatibilityReason> declaredByProducerAlone(NodePair node, String property) {
        JsonNode accepted = node.consumer().additionalProperties();
        if (accepted == null || !rejects(node.producer().projectedProperty(property), accepted)) {
            return Optional.empty();
        }
        return Optional.of(new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                node.producer().property(property).pointer(),
                node.consumer().additionalPropertiesPointer(),
                "The producer may emit '" + property + "', which the consumer does not accept"));
    }

    /**
     * A property that only the consumer declares is filled from the producer's
     * {@code additionalProperties}. An absent one means the producer is open, which the closed run
     * decides rather than this one.
     */
    private Optional<CompatibilityReason> declaredByConsumerAlone(NodePair node, String property) {
        JsonNode emitted = node.producer().additionalProperties();
        if (emitted == null || !rejects(emitted, node.consumer().projectedProperty(property))) {
            return Optional.empty();
        }
        return Optional.of(new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                node.producer().additionalPropertiesPointer(),
                node.consumer().property(property).typePointer(),
                "The producer may emit '" + property + "' with a value the consumer does not accept"));
    }

    private boolean rejects(JsonNode emitted, JsonNode accepted) {
        return accepts.apply(emitted, accepted).filter(judged -> !judged).isPresent();
    }

    /**
     * Walks an engine path down both projections at once. A name is matched against the
     * properties the two sides share rather than read off the path, because a name may contain
     * the separator the path is built with.
     */
    private Optional<NodePair> resolve(String path) {
        NodePair node = new NodePair(producer.root(), consumer.root(), ENGINE_NODE);
        String rest = path;
        while (!ENGINE_KEYWORDS.contains(rest)) {
            if (rest.startsWith(ENGINE_ITEMS)) {
                rest = rest.substring(ENGINE_ITEMS.length());
                node = node.items();
                continue;
            }
            if (!rest.startsWith(ENGINE_PROPERTY_PREFIX)) {
                return Optional.empty();
            }
            String remainder = rest.substring(ENGINE_PROPERTY_PREFIX.length());
            List<String> matches = node.producer().propertyNames().stream()
                    .filter(node.consumer()::declaresProperty)
                    .filter(name -> remainder.equals(name) || remainder.startsWith(name + "/"))
                    .toList();
            if (matches.size() != 1) {
                return Optional.empty();
            }
            rest = remainder.substring(matches.get(0).length());
            node = node.property(matches.get(0));
        }
        return Optional.of(node.at(rest));
    }

    private CompatibilityReason typeNotAccepted(NodePair node) {
        String property = node.producer().name();
        String message = property == null
                ? "The producer may emit a value whose type the consumer does not accept"
                : "The producer's value for '" + property + "' is not accepted by the consumer";
        return new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED, node.producer().typePointer(),
                node.consumer().typePointer(), message);
    }

    private Optional<List<CompatibilityReason>> requiredMember(Difference difference, NodePair node) {
        String name = difference.getSubSchemaUpdated();
        if (difference.getDiffType() != DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED
                || !node.consumer().required().contains(name)
                || node.producer().required().contains(name)) {
            return Optional.empty();
        }
        String message = node.producer().declaresProperty(name)
                ? "Required input '" + name + "' is declared by the producer but not required"
                : "Required input '" + name + "' is not declared by the producer";
        return Optional.of(List.of(new CompatibilityReason(ReasonCode.REQUIRED_NOT_GUARANTEED,
                node.producer().requiredPointer(), node.consumer().requiredMemberPointer(name),
                message)));
    }

    /**
     * The engine reports that the consumer narrows what an undeclared property may hold, which is
     * a mismatch only when the consumer rejects something the producer can put there. The two
     * {@code additionalProperties} are therefore judged against each other, as the declared
     * properties are.
     */
    private Optional<List<CompatibilityReason>> undeclaredProperties(NodePair node) {
        JsonNode accepted = node.consumer().additionalProperties();
        if (accepted == null) {
            return Optional.empty();
        }
        JsonNode producerAdditional = node.producer().additionalProperties();
        JsonNode emitted = producerAdditional == null ? BooleanNode.TRUE : producerAdditional;
        if (BooleanNode.FALSE.equals(emitted)) {
            return Optional.of(List.of());
        }
        Optional<Boolean> judged = accepts.apply(emitted, accepted);
        if (judged.isEmpty()) {
            return Optional.empty();
        }
        if (judged.get()) {
            return Optional.of(List.of());
        }
        return Optional.of(List.of(new CompatibilityReason(
                ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                node.producer().additionalPropertiesPointer(),
                node.consumer().additionalPropertiesPointer(),
                "The producer may emit undeclared properties that the consumer does not accept")));
    }

    /**
     * The engine reports only that the producer declares properties the consumer does not, so each
     * one is judged against the consumer's {@code additionalProperties}. The difference is
     * attributed once one of them has been judged, whether or not any was rejected: the engine
     * also reports this difference when the two sides merely write the same values differently.
     */
    private Optional<List<CompatibilityReason>> propertiesOnlyInProducer(NodePair node) {
        JsonNode consumerAdditional = node.consumer().additionalProperties();
        if (consumerAdditional == null) {
            return Optional.empty();
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
        boolean judged = false;
        for (String name : node.producer().propertyNames()) {
            JsonNode emitted = node.producer().projectedProperty(name);
            if (node.consumer().declaresProperty(name) || BooleanNode.FALSE.equals(emitted)) {
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
                        node.producer().property(name).pointer(),
                        node.consumer().additionalPropertiesPointer(),
                        "The producer may emit '" + name + "', which the consumer does not accept"));
            }
        }
        return judged ? Optional.of(reasons) : Optional.empty();
    }

    private Optional<List<CompatibilityReason>> propertiesOnlyInConsumer(NodePair node) {
        JsonNode producerAdditional = node.producer().additionalProperties();
        JsonNode emitted = producerAdditional == null ? BooleanNode.TRUE : producerAdditional;
        if (BooleanNode.FALSE.equals(emitted)) {
            return Optional.empty();
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
        boolean judged = false;
        for (String name : node.consumer().propertyNames()) {
            if (node.producer().declaresProperty(name)) {
                continue;
            }
            Optional<Boolean> accepted = accepts.apply(emitted,
                    node.consumer().projectedProperty(name));
            if (accepted.isEmpty()) {
                return Optional.empty();
            }
            judged = true;
            if (!accepted.get()) {
                reasons.add(new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                        node.producer().additionalPropertiesPointer(),
                        node.consumer().property(name).typePointer(),
                        "The producer may emit '" + name + "' with a value the consumer does not accept"));
            }
        }
        return judged ? Optional.of(reasons) : Optional.empty();
    }

    /**
     * The same node in both schemas, and the engine keyword the difference was reported under.
     */
    private record NodePair(SchemaNode producer, SchemaNode consumer, String keyword) {

        NodePair property(String name) {
            return new NodePair(producer.property(name), consumer.property(name), keyword);
        }

        NodePair items() {
            return new NodePair(producer.items(), consumer.items(), keyword);
        }

        NodePair additionalProperties() {
            return new NodePair(producer.additionalPropertiesSchema(),
                    consumer.additionalPropertiesSchema(), keyword);
        }

        NodePair at(String engineKeyword) {
            return new NodePair(producer, consumer, engineKeyword);
        }
    }
}
