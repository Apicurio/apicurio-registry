package io.apicurio.registry.agents.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import io.apitomy.datamodels.jsonschema.compat.DiffType;
import io.apitomy.datamodels.jsonschema.compat.Difference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Attributes a difference reported by the comparison engine to the schema nodes it concerns. The
 * engine reports each difference with a JSON Pointer into the producer's projection and one into
 * the consumer's, ending at the keyword that changed, and both are followed into the tool
 * schemas. A difference in which properties an object declares is reported at that object's
 * {@code properties} without naming them, so those properties are identified from the two nodes.
 */
final class DifferenceAttributor {

    private static final String PROPERTIES = "properties";
    private static final String REQUIRED = "required";
    private static final String ITEMS = "items";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String TYPE = "type";

    private final SchemaProjection producer;
    private final SchemaNode closedProducer;
    private final List<String> openProducerObjects;
    private final SchemaProjection consumer;
    private final BiFunction<JsonNode, JsonNode, Optional<Boolean>> accepts;

    /**
     * @param producerClosed the producer with its objects closed, or {@code null} when it has none
     *        to close. Only differences that survive the comparison with it are attributed, so a
     *        reason must hold for the closed producer, and its subschemas are read from it.
     * @param accepts decides whether every value the first subschema permits is accepted by the
     *        second, or returns empty when the two cannot be compared
     */
    DifferenceAttributor(SchemaProjection producer, SchemaProjection consumer, JsonNode producerClosed,
            BiFunction<JsonNode, JsonNode, Optional<Boolean>> accepts) {
        this.producer = producer;
        this.closedProducer = producerClosed == null ? producer.root()
                : new SchemaNode(producer.original(), producerClosed, producer.base());
        this.openProducerObjects = producer.root().openObjects();
        this.consumer = consumer;
        this.accepts = accepts;
    }

    /**
     * Returns the reasons behind a difference, or empty when it cannot be attributed. Both
     * pointers must lead to the same schema node, which they do whenever the engine compares a
     * producer node with the consumer node at the same place.
     */
    Optional<List<CompatibilityReason>> attribute(Difference difference) {
        Location location = Location.of(difference.getPathUpdated().segments());
        if (!location.node().equals(Location.of(difference.getPathOriginal().segments()).node())) {
            return Optional.empty();
        }
        SchemaNode emitted = closedProducer.at(location.node());
        SchemaNode accepted = consumer.root().at(location.node());
        if (location.keyword() == null || TYPE.equals(location.keyword())) {
            return valueNotAccepted(location.node());
        }
        return switch (location.keyword()) {
            case REQUIRED -> requiredMember(difference, location.member(), emitted, accepted);
            case PROPERTIES -> propertiesOnlyInOneSchema(emitted, accepted);
            case ADDITIONAL_PROPERTIES -> undeclaredProperties(emitted, accepted);
            case ITEMS -> Optional.of(List.of(itemsNotAccepted(emitted, accepted)));
            default -> Optional.of(List.of(new CompatibilityReason(ReasonCode.VALUE_NOT_ACCEPTED,
                    emitted.pointer(), accepted.pointer(), difference.getShortDescription())));
        };
    }

    /**
     * The producer objects the verdict depends on being closed, when only the open producer causes
     * a difference: the object the difference is reported at when it is open, and otherwise the
     * open objects in the properties only the producer declares and in its
     * {@code additionalProperties}, which is what a difference in the declared properties of a
     * closed object concerns. A property both declare is reported at its own pointer. When there
     * are none, every open object below it.
     */
    List<String> openObjects(Difference difference) {
        List<String> node = Location.of(difference.getPathOriginal().segments()).node();
        SchemaNode emitted = producer.root().at(node);
        if (emitted.openObject()) {
            return List.of(emitted.pointer());
        }
        SchemaNode accepted = consumer.root().at(node);
        List<String> open = new ArrayList<>();
        for (String name : emitted.propertyNames()) {
            if (!accepted.declaresProperty(name)) {
                open.addAll(emitted.property(name).openObjects());
            }
        }
        open.addAll(emitted.additionalPropertiesSchema().openObjects());
        return open.isEmpty() ? openObjects(emitted.pointer()) : open;
    }

    /**
     * The open producer objects at or below a node: the node itself when it is open, and the open
     * objects below it otherwise, such as a property only the producer declares that is compared
     * with the consumer's {@code additionalProperties}. Never empty, so that a mismatch that only
     * the open producer causes always leaves a limitation.
     */
    private List<String> openObjects(String node) {
        if (openProducerObjects.contains(node)) {
            return List.of(node);
        }
        List<String> below = openProducerObjects.stream()
                .filter(object -> JsonPointers.isAtOrBelow(object, node)).toList();
        return below.isEmpty() ? List.of(node) : below;
    }

    /**
     * The engine reads object keywords as an object schema, so a producer that declares no
     * {@code type} is compared as if it could emit nothing but objects, and no difference is
     * reported for the values it may emit beside them.
     */
    Optional<CompatibilityReason> unrestrictedOutputType() {
        if (producer.root().declaresType() || !consumer.root().declaresType()) {
            return Optional.empty();
        }
        return Optional.of(outputTypeNotAccepted());
    }

    /**
     * Reasons from a consumer {@code additionalProperties} schema, which the engine may not
     * compare fully: several producer properties can feed it, so it cannot be narrowed to any one
     * of them, and each is compared with it separately. The comparison is made with the producer
     * closed, and again as written, so that a mismatch only an open producer object causes adds
     * that object to {@code openObjects} instead of a reason. Empty when one of them cannot be
     * compared.
     *
     * @see TypeNarrowing
     */
    Optional<List<CompatibilityReason>> undeclaredOutputNotAccepted(Set<String> openObjects) {
        List<CompatibilityReason> reasons = new ArrayList<>();
        if (!undeclaredOutputNotAccepted(closedProducer, consumer.root(), accepts, reasons)) {
            return Optional.empty();
        }
        if (producer.closable()) {
            List<CompatibilityReason> asWritten = new ArrayList<>();
            if (!undeclaredOutputNotAccepted(producer.root(), consumer.root(), accepts, asWritten)) {
                return Optional.empty();
            }
            asWritten.stream().filter(reason -> !reasons.contains(reason))
                    .forEach(reason -> openObjects.addAll(openObjects(reason.producerPointer())));
        }
        return Optional.of(reasons);
    }

    /**
     * Compares each producer schema that feeds a consumer {@code additionalProperties} schema with
     * it, at every place where a producer node reaches a consumer node: the properties only the
     * producer declares, and the producer's own {@code additionalProperties}.
     *
     * @return {@code false} when one of them cannot be compared
     */
    static boolean undeclaredOutputNotAccepted(SchemaNode emitted, SchemaNode accepted,
            BiFunction<JsonNode, JsonNode, Optional<Boolean>> accepts, List<CompatibilityReason> reasons) {
        JsonNode consumerAdditional = accepted.projected().path(ADDITIONAL_PROPERTIES);
        JsonNode producerAdditional = emitted.projected().path(ADDITIONAL_PROPERTIES);
        if (consumerAdditional.isObject()) {
            for (String name : emitted.propertyNames()) {
                JsonNode value = emitted.projectedProperty(name);
                if (accepted.declaresProperty(name) || BooleanNode.FALSE.equals(value)) {
                    continue;
                }
                Optional<Boolean> decided = accepts.apply(value, consumerAdditional);
                if (decided.isEmpty()) {
                    return false;
                }
                if (!decided.get()) {
                    reasons.add(propertyNotAccepted(emitted, accepted, name));
                }
            }
            if (producerAdditional.isObject()) {
                Optional<Boolean> decided = accepts.apply(producerAdditional, consumerAdditional);
                if (decided.isEmpty()) {
                    return false;
                }
                if (!decided.get()) {
                    reasons.add(undeclaredPropertiesNotAccepted(emitted, accepted));
                }
            }
        }
        for (String name : accepted.propertyNames()) {
            if (emitted.declaresProperty(name)) {
                if (!undeclaredOutputNotAccepted(emitted.property(name), accepted.property(name), accepts,
                        reasons)) {
                    return false;
                }
            } else if (producerAdditional.isObject() && !undeclaredOutputNotAccepted(
                    emitted.additionalPropertiesSchema(), accepted.property(name), accepts, reasons)) {
                return false;
            }
        }
        return !emitted.projected().path(ITEMS).isObject() || !accepted.projected().path(ITEMS).isObject()
                || undeclaredOutputNotAccepted(emitted.itemsSchema(), accepted.itemsSchema(), accepts,
                        reasons);
    }

    /**
     * A difference in what a node accepts: the type of the schema root, of a property, of an
     * array's items, or of the undeclared properties of an object.
     */
    private Optional<List<CompatibilityReason>> valueNotAccepted(List<String> node) {
        if (node.isEmpty()) {
            return Optional.of(List.of(outputTypeNotAccepted()));
        }
        String step = node.get(node.size() - 1);
        List<String> parent = node.subList(0, node.size() - 1);
        if (ADDITIONAL_PROPERTIES.equals(step)) {
            return undeclaredProperties(closedProducer.at(parent), consumer.root().at(parent));
        }
        if (ITEMS.equals(step)) {
            return Optional.of(List.of(itemsNotAccepted(closedProducer.at(parent),
                    consumer.root().at(parent))));
        }
        List<String> object = node.subList(0, node.size() - 2);
        SchemaNode emitted = closedProducer.at(object);
        SchemaNode accepted = consumer.root().at(object);
        if (!emitted.declaresProperty(step) || !accepted.declaresProperty(step)) {
            return Optional.empty();
        }
        return Optional.of(List.of(propertyValueNotAccepted(emitted, accepted, step)));
    }

    private CompatibilityReason outputTypeNotAccepted() {
        return new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED, producer.root().typePointer(),
                consumer.root().typePointer(),
                "The producer may emit a value whose type the consumer does not accept");
    }

    private Optional<List<CompatibilityReason>> requiredMember(Difference difference, String index,
            SchemaNode emitted, SchemaNode accepted) {
        if (difference.getDiffType() != DiffType.OBJECT_TYPE_REQUIRED_PROPERTIES_MEMBER_ADDED || index == null) {
            return Optional.empty();
        }
        String name = memberName(accepted.projected().path(REQUIRED), index);
        if (name == null || !accepted.required().contains(name) || emitted.required().contains(name)) {
            return Optional.empty();
        }
        String message = emitted.declaresProperty(name)
                ? "Required input '" + name + "' is declared by the producer but not required"
                : "Required input '" + name + "' is not declared by the producer";
        return Optional.of(List.of(new CompatibilityReason(ReasonCode.REQUIRED_NOT_GUARANTEED,
                emitted.requiredPointer(), accepted.requiredMemberPointer(name), message)));
    }

    private Optional<List<CompatibilityReason>> undeclaredProperties(SchemaNode emitted, SchemaNode accepted) {
        if (accepted.additionalProperties() == null) {
            return Optional.empty();
        }
        return Optional.of(List.of(undeclaredPropertiesNotAccepted(emitted, accepted)));
    }

    /**
     * A difference in which properties an object declares can come from either schema, so both
     * are checked. Empty when it can't be explained.
     */
    private Optional<List<CompatibilityReason>> propertiesOnlyInOneSchema(SchemaNode emitted,
            SchemaNode accepted) {
        Optional<List<CompatibilityReason>> inProducer = propertiesOnlyInProducer(emitted, accepted);
        Optional<List<CompatibilityReason>> inConsumer = propertiesOnlyInConsumer(emitted, accepted);
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
    private Optional<List<CompatibilityReason>> propertiesOnlyInProducer(SchemaNode emitted,
            SchemaNode accepted) {
        JsonNode consumerAdditional = accepted.additionalProperties();
        if (consumerAdditional == null) {
            return Optional.of(List.of());
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
        for (String name : emitted.propertyNames()) {
            JsonNode value = emitted.projectedProperty(name);
            if (accepted.declaresProperty(name) || BooleanNode.FALSE.equals(value)) {
                continue;
            }
            Optional<Boolean> decided = consumerAdditional.isBoolean()
                    ? Optional.of(consumerAdditional.booleanValue())
                    : accepts.apply(value, consumerAdditional);
            if (decided.isEmpty()) {
                return Optional.empty();
            }
            if (!decided.get()) {
                reasons.add(propertyNotAccepted(emitted, accepted, name));
            }
        }
        return Optional.of(reasons);
    }

    /**
     * Reasons from properties only the consumer declares: an empty list if there are none, or
     * empty if they can't be decided.
     */
    private Optional<List<CompatibilityReason>> propertiesOnlyInConsumer(SchemaNode emitted,
            SchemaNode accepted) {
        JsonNode value = producerAdditionalProperties(emitted);
        if (BooleanNode.FALSE.equals(value)) {
            return Optional.of(List.of());
        }
        List<CompatibilityReason> reasons = new ArrayList<>();
        for (String name : accepted.propertyNames()) {
            if (emitted.declaresProperty(name)) {
                continue;
            }
            Optional<Boolean> decided = accepts.apply(value, accepted.projectedProperty(name));
            if (decided.isEmpty()) {
                return Optional.empty();
            }
            if (!decided.get()) {
                reasons.add(undeclaredValueNotAccepted(emitted, accepted, name));
            }
        }
        return Optional.of(reasons);
    }

    private static CompatibilityReason propertyValueNotAccepted(SchemaNode emitted, SchemaNode accepted,
            String name) {
        return new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED, emitted.propertyTypePointer(name),
                accepted.propertyTypePointer(name),
                "The producer's value for '" + name + "' is not accepted by the consumer");
    }

    private static CompatibilityReason propertyNotAccepted(SchemaNode emitted, SchemaNode accepted,
            String name) {
        return new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                emitted.propertyPointer(name), accepted.additionalPropertiesPointer(),
                "The producer may emit '" + name + "', which the consumer does not accept");
    }

    private static CompatibilityReason undeclaredValueNotAccepted(SchemaNode emitted, SchemaNode accepted,
            String name) {
        return new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED,
                emitted.additionalPropertiesPointer(), accepted.propertyTypePointer(name),
                "The producer may emit '" + name + "' with a value the consumer does not accept");
    }

    private static CompatibilityReason undeclaredPropertiesNotAccepted(SchemaNode emitted,
            SchemaNode accepted) {
        return new CompatibilityReason(ReasonCode.ADDITIONAL_PROPERTY_NOT_ACCEPTED,
                emitted.additionalPropertiesPointer(), accepted.additionalPropertiesPointer(),
                "The producer may emit undeclared properties that the consumer does not accept");
    }

    private static CompatibilityReason itemsNotAccepted(SchemaNode emitted, SchemaNode accepted) {
        return new CompatibilityReason(ReasonCode.TYPE_NOT_ACCEPTED, emitted.itemsSchema().typePointer(),
                accepted.itemsSchema().typePointer(),
                "The producer may emit array items that the consumer does not accept");
    }

    /**
     * What an object of the producer may emit under a name it doesn't declare, which is anything
     * without {@code additionalProperties}. A closed object declares it {@code false}.
     */
    private static JsonNode producerAdditionalProperties(SchemaNode emitted) {
        JsonNode declared = emitted.additionalProperties();
        return declared != null ? declared : BooleanNode.TRUE;
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

    /**
     * Where a pointer into a projection leads: the schema node, and the keyword of that node it
     * ends at, if any. Segments are read from the root, so a property named like a keyword is
     * never taken for one.
     *
     * @param node the keywords and property names leading to the schema node
     * @param keyword the keyword the pointer ends at, or {@code null} when it addresses the node
     * @param member the index inside {@code required}, or {@code null}
     */
    private record Location(List<String> node, String keyword, String member) {

        static Location of(List<String> path) {
            List<String> node = new ArrayList<>();
            int index = 0;
            while (index < path.size()) {
                String keyword = path.get(index);
                boolean last = index == path.size() - 1;
                switch (keyword) {
                    case PROPERTIES -> {
                        if (last) {
                            return new Location(node, PROPERTIES, null);
                        }
                        node.add(PROPERTIES);
                        node.add(path.get(index + 1));
                        index += 2;
                    }
                    case ITEMS, ADDITIONAL_PROPERTIES -> {
                        if (last) {
                            return new Location(node, keyword, null);
                        }
                        node.add(keyword);
                        index++;
                    }
                    case REQUIRED -> {
                        return new Location(node, REQUIRED, last ? null : path.get(index + 1));
                    }
                    default -> {
                        return new Location(node, keyword, null);
                    }
                }
            }
            return new Location(node, null, null);
        }
    }
}
