package io.apicurio.registry.agents.mcptools.compatibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.SpecVersion.VersionFlag;
import io.apicurio.registry.json.rules.validity.JsonSchemaDocumentValidator;
import io.apitomy.datamodels.DataModelsException;
import io.apitomy.datamodels.jsonschema.compat.DiffType;
import io.apitomy.datamodels.jsonschema.compat.Difference;
import io.apitomy.datamodels.jsonschema.compat.JsonSchemaCompatibilityChecker;
import jakarta.enterprise.context.ApplicationScoped;
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

    /**
     * Compares the projections, which contain no references, so there is nothing to resolve. It
     * holds no state between checks, so one instance serves every comparison.
     */
    private static final JsonSchemaCompatibilityChecker CHECKER = JsonSchemaCompatibilityChecker.builder().build();

    /**
     * Decides whether a projection can be read as a JSON Schema at all. Projections declare no
     * {@code $schema}, and the keywords they keep mean the same in every dialect, so draft 7 is used.
     */
    private static final JsonSchemaDocumentValidator READABILITY = JsonSchemaDocumentValidator.builder()
            .draftDetector(schema -> Optional.of(VersionFlag.V7))
            .build();

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
        if (!isReadable(projection.projected())) {
            List<CompatibilityLimitation> limitations = new ArrayList<>(projection.limitations());
            limitations.add(comparisonFailed(SchemaSide.PRODUCER, OUTPUT_SCHEMA_POINTER,
                    "The outputSchema cannot be read as a JSON Schema"));
            return PreparedProducer.unavailable(limitations);
        }
        return PreparedProducer.prepared(projection, projection.projected(),
                projection.closable() ? projection.closed() : null);
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

        if (!isReadable(consumer.projected())) {
            limitations.add(comparisonFailed(SchemaSide.CONSUMER, INPUT_SCHEMA_POINTER,
                    "The inputSchema cannot be read as a JSON Schema"));
            return indeterminate(limitations);
        }

        Map<DifferenceKey, Difference> asWritten;
        Map<DifferenceKey, Difference> closed;
        try {
            asWritten = incompatibleDifferences(producer.asWritten(), consumer.projected());
            closed = producer.closed() == null ? asWritten
                    : incompatibleDifferences(producer.closed(), consumer.projected());
        } catch (IllegalStateException | DataModelsException e) {
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
                producer.closed() != null, this::accepts);
        Set<CompatibilityReason> reasons = new LinkedHashSet<>();
        attributor.unrestrictedOutputType().ifPresent(reasons::add);
        reasons.addAll(attributor.numbersNotAccepted());
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

    private Optional<Boolean> accepts(JsonNode emitted, JsonNode accepted) {
        try {
            return Optional.of(incompatibleDifferences(emitted, accepted).isEmpty());
        } catch (IllegalStateException | DataModelsException e) {
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
     *
     * @throws IllegalStateException if part of the schemas could not be compared
     */
    private static Map<DifferenceKey, Difference> incompatibleDifferences(JsonNode producer,
            JsonNode consumer) {
        var result = CHECKER.checkBackward(producer.toString(), consumer.toString());
        if (result.hasUnsupportedFeatures()) {
            throw new IllegalStateException("The schemas could not be fully compared: "
                    + String.join("; ", result.getUnsupportedFeatures()));
        }
        Map<DifferenceKey, Difference> differences = new LinkedHashMap<>();
        for (Difference difference : result.getIncompatibleDifferences()) {
            differences.put(new DifferenceKey(difference.getDiffType(), difference.getPathUpdated().toString()),
                    difference);
        }
        return differences;
    }

    private static boolean isReadable(JsonNode projection) {
        return READABILITY.validate(projection).isEmpty();
    }

    private static PairCompatibility indeterminate(List<CompatibilityLimitation> limitations) {
        return new PairCompatibility(CompatibilityVerdict.INDETERMINATE, List.of(), limitations);
    }

    private static CompatibilityLimitation comparisonFailed(SchemaSide side, String pointer,
            String message) {
        return new CompatibilityLimitation(LimitationCode.COMPARISON_FAILED, side, pointer, pointer,
                message);
    }

    private record DifferenceKey(DiffType type, String path) {
    }
}
