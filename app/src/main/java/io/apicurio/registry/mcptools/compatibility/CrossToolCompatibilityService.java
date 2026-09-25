package io.apicurio.registry.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.json.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.DiffType;
import io.apicurio.registry.json.rules.compatibility.jsonschema.diff.Difference;
import jakarta.enterprise.context.ApplicationScoped;
import org.everit.json.schema.Schema;
import org.everit.json.schema.SchemaException;
import org.everit.json.schema.loader.SchemaClient;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether the output of one MCP tool can be passed, as a whole object, to the input of
 * another. The producer's {@code outputSchema} is the original schema and the consumer's
 * {@code inputSchema} the updated one of a backward compatibility check.
 *
 * <p>The engine assumes that an object without {@code additionalProperties} can emit any
 * undeclared property, so the producer is also compared with its root object closed. Mismatches
 * that only the open producer causes become a {@link LimitationCode#PRODUCER_OBJECT_OPEN}
 * limitation, never a reason.
 */
@ApplicationScoped
public class CrossToolCompatibilityService {

    private static final Logger log = LoggerFactory.getLogger(CrossToolCompatibilityService.class);

    private static final String OUTPUT_SCHEMA = "outputSchema";
    private static final String INPUT_SCHEMA = "inputSchema";
    private static final String DOCUMENT_POINTER = "";
    private static final String OUTPUT_SCHEMA_POINTER = "/outputSchema";
    private static final String INPUT_SCHEMA_POINTER = "/inputSchema";

    private static final SchemaClient DENY_ALL_SCHEMA_CLIENT = url -> {
        throw new IllegalStateException("External JSON Schema resolution is disabled");
    };

    private static final Comparator<CompatibilityReason> REASON_ORDER = Comparator
            .comparing(CompatibilityReason::consumerPointer)
            .thenComparing(CompatibilityReason::producerPointer)
            .thenComparing(CompatibilityReason::code);

    /**
     * Projects and loads a producer tool's {@code outputSchema}.
     *
     * @param toolRoot the parsed producer tool definition
     */
    public PreparedProducer prepareProducer(JsonNode toolRoot) {
        if (!toolRoot.isObject()) {
            return PreparedProducer.unavailable(List.of(comparisonFailed(SchemaSide.PRODUCER,
                    DOCUMENT_POINTER, "The tool definition is not a JSON object")));
        }
        JsonNode outputSchema = toolRoot.get(OUTPUT_SCHEMA);
        if (outputSchema == null) {
            return PreparedProducer.unavailable(List.of(new CompatibilityLimitation(
                    LimitationCode.SOURCE_HAS_NO_OUTPUT_SCHEMA, SchemaSide.PRODUCER,
                    OUTPUT_SCHEMA_POINTER, OUTPUT_SCHEMA_POINTER, "The tool declares no outputSchema")));
        }
        if (!outputSchema.isObject()) {
            return PreparedProducer.unavailable(List.of(comparisonFailed(SchemaSide.PRODUCER,
                    OUTPUT_SCHEMA_POINTER, "'outputSchema' is not an object")));
        }

        SchemaProjection projection = SchemaProjector.project(outputSchema, OUTPUT_SCHEMA_POINTER,
                SchemaSide.PRODUCER);
        if (!projection.dialectSupported()) {
            return PreparedProducer.unavailable(projection.limitations());
        }
        try {
            Schema asWritten = load(projection.projected());
            Schema closed = projection.closable() ? load(projection.closed()) : null;
            return PreparedProducer.loaded(projection, asWritten, closed);
        } catch (SchemaException | JSONException e) {
            log.debug("MCP tool outputSchema cannot be loaded for comparison", e);
            List<CompatibilityLimitation> limitations = new ArrayList<>(projection.limitations());
            limitations.add(comparisonFailed(SchemaSide.PRODUCER, OUTPUT_SCHEMA_POINTER,
                    "The outputSchema cannot be read as a JSON Schema"));
            return PreparedProducer.unavailable(limitations);
        }
    }

    /**
     * Represents a producer tool whose content could not be parsed as JSON.
     */
    public PreparedProducer unreadableProducer() {
        return PreparedProducer.unavailable(List.of(comparisonFailed(SchemaSide.PRODUCER,
                DOCUMENT_POINTER, "The tool definition is not valid JSON")));
    }

    /**
     * Compares a prepared producer with a consumer tool.
     *
     * @param producer the prepared producer
     * @param consumerRoot the parsed consumer tool definition
     */
    public PairCompatibility compare(PreparedProducer producer, JsonNode consumerRoot) {
        List<CompatibilityLimitation> limitations = new ArrayList<>(producer.limitations());
        if (!producer.canMatch()) {
            return indeterminate(limitations);
        }
        if (!consumerRoot.isObject()) {
            limitations.add(comparisonFailed(SchemaSide.CONSUMER, DOCUMENT_POINTER,
                    "The tool definition is not a JSON object"));
            return indeterminate(limitations);
        }
        JsonNode inputSchema = consumerRoot.get(INPUT_SCHEMA);
        if (inputSchema == null || !inputSchema.isObject()) {
            limitations.add(comparisonFailed(SchemaSide.CONSUMER, INPUT_SCHEMA_POINTER,
                    "The tool declares no inputSchema object"));
            return indeterminate(limitations);
        }

        SchemaProjection consumer = SchemaProjector.project(inputSchema, INPUT_SCHEMA_POINTER,
                SchemaSide.CONSUMER);
        limitations.addAll(consumer.limitations());
        if (!consumer.dialectSupported()) {
            return indeterminate(limitations);
        }

        ObjectNode producerProjected = producer.projection().projected();
        Optional<ObjectNode> producerRealigned = TypeAlignment.realign(producerProjected,
                consumer.projected());
        ObjectNode consumerProjected = TypeAlignment.realign(consumer.projected(), producerProjected)
                .orElse(consumer.projected());

        Map<DifferenceKey, Difference> asWritten;
        Map<DifferenceKey, Difference> closed;
        try {
            Schema consumerSchema = load(consumerProjected);
            ProducerRun run = producerRun(producer, producerRealigned);
            asWritten = incompatibleDifferences(run.asWritten(), consumerSchema);
            closed = run.closed() == null ? asWritten
                    : incompatibleDifferences(run.closed(), consumerSchema);
        } catch (SchemaException | JSONException e) {
            log.debug("MCP tool inputSchema cannot be loaded for comparison", e);
            limitations.add(comparisonFailed(SchemaSide.CONSUMER, INPUT_SCHEMA_POINTER,
                    "The inputSchema cannot be read as a JSON Schema"));
            return indeterminate(limitations);
        } catch (IllegalStateException e) {
            log.debug("MCP tool schemas could not be compared", e);
            limitations.add(comparisonFailed(SchemaSide.CONSUMER, INPUT_SCHEMA_POINTER,
                    "The schemas could not be compared"));
            return indeterminate(limitations);
        }

        if (!closed.keySet().stream().allMatch(asWritten::containsKey)) {
            limitations.add(comparisonFailed(SchemaSide.PRODUCER, OUTPUT_SCHEMA_POINTER,
                    "Closing the producer object introduced a mismatch"));
        }
        if (!asWritten.keySet().stream().allMatch(closed::containsKey)) {
            limitations.add(new CompatibilityLimitation(LimitationCode.PRODUCER_OBJECT_OPEN,
                    SchemaSide.PRODUCER, OUTPUT_SCHEMA_POINTER, OUTPUT_SCHEMA_POINTER,
                    "The outputSchema does not set additionalProperties, so the verdict depends on the"
                            + " producer emitting only the properties it declares"));
        }

        DifferenceAttributor attributor = new DifferenceAttributor(producer.projection(), consumer,
                this::accepts);
        Set<CompatibilityReason> reasons = new LinkedHashSet<>();
        boolean unattributed = false;
        for (Map.Entry<DifferenceKey, Difference> difference : asWritten.entrySet()) {
            if (closed.containsKey(difference.getKey())) {
                Optional<List<CompatibilityReason>> attributed = attributor.attribute(difference.getValue());
                attributed.ifPresent(reasons::addAll);
                unattributed |= attributed.isEmpty();
            }
        }
        if (unattributed) {
            limitations.add(comparisonFailed(SchemaSide.CONSUMER, INPUT_SCHEMA_POINTER,
                    "A mismatch reported by the comparison engine could not be attributed to a property"));
        }

        List<CompatibilityReason> standing = reasons.stream()
                .filter(reason -> !isInvalidated(reason, limitations))
                .sorted(REASON_ORDER)
                .toList();
        CompatibilityVerdict verdict;
        if (!standing.isEmpty()) {
            verdict = CompatibilityVerdict.INCOMPATIBLE;
        } else if (limitations.isEmpty()) {
            verdict = CompatibilityVerdict.COMPATIBLE;
        } else {
            verdict = CompatibilityVerdict.INDETERMINATE;
        }
        return new PairCompatibility(verdict, standing, limitations);
    }

    /**
     * Represents a consumer tool whose content could not be parsed as JSON.
     */
    public PairCompatibility unreadableConsumer(PreparedProducer producer) {
        List<CompatibilityLimitation> limitations = new ArrayList<>(producer.limitations());
        limitations.add(comparisonFailed(SchemaSide.CONSUMER, DOCUMENT_POINTER,
                "The tool definition is not valid JSON"));
        return indeterminate(limitations);
    }

    /**
     * The producer schemas to compare with one consumer. The schemas loaded when the producer was
     * prepared are reused unless that consumer's unions require the producer to be written the
     * same way.
     */
    private ProducerRun producerRun(PreparedProducer producer, Optional<ObjectNode> realigned) {
        if (realigned.isEmpty()) {
            return new ProducerRun(producer.asWritten(), producer.closed());
        }
        ObjectNode projected = realigned.get();
        Schema closed = producer.projection().closable()
                ? load(SchemaProjection.closed(projected))
                : null;
        return new ProducerRun(load(projected), closed);
    }

    private Optional<Boolean> accepts(JsonNode emitted, JsonNode accepted) {
        try {
            return Optional.of(incompatibleDifferences(
                    load(TypeAlignment.realignSubschema(emitted, accepted)),
                    load(TypeAlignment.realignSubschema(accepted, emitted))).isEmpty());
        } catch (SchemaException | JSONException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * A mismatch stands unless a limitation on the same side covers the schema node it was found
     * at or one of that node's ancestors.
     */
    private static boolean isInvalidated(CompatibilityReason reason,
            List<CompatibilityLimitation> limitations) {
        return limitations.stream()
                .filter(limitation -> limitation.code().coversSubtree())
                .anyMatch(limitation -> JsonPointers.isAtOrBelow(
                        limitation.side() == SchemaSide.PRODUCER ? reason.producerPointer()
                                : reason.consumerPointer(),
                        limitation.node()));
    }

    /**
     * Keys differences by what they report in the consumer's terms, which do not change when the
     * producer is closed, so that the two runs can be compared.
     */
    private static Map<DifferenceKey, Difference> incompatibleDifferences(Schema producer,
            Schema consumer) {
        Map<DifferenceKey, Difference> differences = new LinkedHashMap<>();
        for (Difference difference : JsonSchemaDiffLibrary.findDifferences(producer, consumer)
                .getIncompatibleDifferences()) {
            differences.put(new DifferenceKey(difference.getDiffType(), difference.getPathUpdated(),
                    difference.getSubSchemaUpdated()), difference);
        }
        return differences;
    }

    private static Schema load(JsonNode schema) {
        Object json = schema.isBoolean() ? schema.booleanValue() : new JSONObject(schema.toString());
        return SchemaLoader.builder()
                .draftV7Support()
                .schemaClient(DENY_ALL_SCHEMA_CLIENT)
                .schemaJson(json)
                .build()
                .load()
                .build();
    }

    private static PairCompatibility indeterminate(List<CompatibilityLimitation> limitations) {
        return new PairCompatibility(CompatibilityVerdict.INDETERMINATE, List.of(), limitations);
    }

    private static CompatibilityLimitation comparisonFailed(SchemaSide side, String pointer,
            String message) {
        return new CompatibilityLimitation(LimitationCode.COMPARISON_FAILED, side, pointer, pointer,
                message);
    }

    private record DifferenceKey(DiffType type, String path, String updated) {
    }

    private record ProducerRun(Schema asWritten, Schema closed) {
    }
}
