package io.apicurio.registry.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.contracts.rules.RuleExecutionEngine;
import io.apicurio.registry.contracts.rules.RuleExecutionResult;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.ContractRulesetCache;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleSet;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.serde.data.SerdeMetadata;
import io.apicurio.registry.serde.data.SerdeRecord;
import io.apicurio.registry.serde.tracing.SerDesAttributes;
import io.apicurio.registry.serde.tracing.SerDesTracer;
import io.apicurio.registry.serde.utils.BoundedCacheFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.serde.BaseSerde.MAGIC_BYTE;

public abstract class AbstractSerializer<T, U> implements AutoCloseable {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(
            AbstractSerializer.class.getName());

    /**
     * Default initial buffer size for ByteArrayOutputStream.
     * Pre-sized to avoid array resizing for typical message sizes.
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private static final SerDesTracer tracer = new SerDesTracer();

    /**
     * Maximum number of entries in the fast-path cache.
     * Prevents unbounded memory growth in long-running applications.
     */
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * Cache key combining topic and a schema identifier.
     * The schemaKey can be either a Class (for SpecificRecord) or a Schema object (for GenericRecord).
     */
    private record SchemaCacheKey(String topic, Object schemaKey) {}

    /**
     * Fast-path cache: maps (topic, schema key) to resolved schema.
     * This bypasses the full resolution flow (object creation + 4 cache lookups)
     * after the first serialization of a message type per topic.
     * Uses LRU eviction to prevent unbounded growth.
     */
    private final Map<SchemaCacheKey, SchemaLookupResult<T>> fastPathCache = BoundedCacheFactory.createLRU(MAX_CACHE_SIZE);

    private final BaseSerde<T, U> baseSerde;
    private boolean contractRulesEnabled = false;
    private boolean contractRulesFailOnError = true;
    private RuleExecutionEngine ruleEngine;
    private DeadLetterQueue deadLetterQueue;
    private ContractRuleLoader ruleLoader;

    public AbstractSerializer() {
        this.baseSerde = new BaseSerde<>();
    }

    public AbstractSerializer(RegistryClientFacade clientFacade) {
        this.baseSerde = new BaseSerde<>(clientFacade);
    }

    public AbstractSerializer(SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(schemaResolver);
    }

    public AbstractSerializer(RegistryClientFacade clientFacade, SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(clientFacade, schemaResolver);
    }

    public AbstractSerializer(RegistryClientFacade clientFacade, ArtifactReferenceResolverStrategy<T, U> strategy,
                              SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(clientFacade, strategy, schemaResolver);
    }

    public abstract SchemaParser<T, U> schemaParser();

    public abstract void serializeData(ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    /**
     * Gets a cache key for the schema associated with this data.
     *
     * For types where the schema is tied to the class (e.g., Avro SpecificRecord, Protobuf GeneratedMessage),
     * this should return the class object.
     *
     * For types where the schema varies per instance (e.g., Avro GenericRecord),
     * this should return an object that uniquely identifies the schema (e.g., the Schema object itself).
     *
     * For types where caching is not safe, this should return null.
     *
     * Subclasses should override this method to provide type-specific logic.
     * The default implementation returns null (no caching).
     *
     * @param data the data to get a cache key for
     * @return an object suitable as a cache key, or null if caching is not safe
     */
    protected Object getSchemaCacheKey(U data) {
        return null;
    }

    public void configure(SerdeConfig config, boolean isKey) {
        baseSerde.configure(config, isKey, schemaParser());
        Object enabled = config.originals().get(SerdeConfig.CONTRACT_RULES_ENABLED);
        if (enabled != null) {
            contractRulesEnabled = Boolean.parseBoolean(enabled.toString());
        }
        Object failOnError = config.originals().get(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR);
        if (failOnError != null) {
            contractRulesFailOnError = Boolean.parseBoolean(failOnError.toString());
        }
        if (contractRulesEnabled) {
            ruleEngine = RuleExecutionEngine.createStandalone();
            long ttl = SerdeConfig.CONTRACT_RULES_CACHE_TTL_SECONDS_DEFAULT;
            Object ttlObj = config.originals().get(SerdeConfig.CONTRACT_RULES_CACHE_TTL_SECONDS);
            if (ttlObj != null) {
                ttl = Long.parseLong(ttlObj.toString());
            }
            ruleLoader = new ContractRuleLoader(new ContractRulesetCache(ttl), baseSerde::getClientFacade);
        }
    }

    @FunctionalInterface
    public interface RecordWriter<T, U> {
        byte[] write(SchemaLookupResult<T> schema, U data) throws IOException;
    }

    public byte[] serializeData(String topic, U data) {
        return serializeData(topic, null, data, this::writeRecord);
    }

    public byte[] serializeData(String topic, SerdeMetadata metadata, U data, RecordWriter<T, U> writer) {
        if (data == null) {
            return null;
        }
        return tracer.traceSerialize(topic, span -> {
            try {
                SchemaLookupResult<T> schema = null;
                SchemaCacheKey cacheKey = null;

                boolean canUseFastPath = metadata == null || metadata.isFastPathSafe();
                Object schemaKey = canUseFastPath ? getSchemaCacheKey(data) : null;
                if (schemaKey != null) {
                    cacheKey = new SchemaCacheKey(topic, schemaKey);
                    schema = fastPathCache.get(cacheKey);
                }

                boolean cacheHit = schema != null;

                if (schema == null) {
                    schema = resolveSchema(topic, metadata, data);

                    if (cacheKey != null) {
                        fastPathCache.put(cacheKey, schema);
                    }
                }

                SerDesTracer.setSchemaAttributes(span, schema, cacheHit);

                U toWrite = applyContractRulesForWrite(topic, schema, data);

                byte[] result = writer.write(schema, toWrite);
                span.setAttribute(SerDesAttributes.DATA_SIZE, (long) result.length);
                return result;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private SchemaLookupResult<T> resolveSchema(String topic, SerdeMetadata metadata, U data) {
        DefaultSchemaResolver.currentOperation.set("SERIALIZE");
        try {
            SerdeMetadata resolverMetadata = metadata != null ? metadata
                    : new SerdeMetadata(topic, baseSerde.isKey());
            return tracer.traceSchemaResolve(topic, "serialize", resolveSpan -> baseSerde
                    .getSchemaResolver().resolveSchema(new SerdeRecord<>(resolverMetadata, data)));
        } finally {
            DefaultSchemaResolver.currentOperation.remove();
        }
    }

    private byte[] writeRecord(SchemaLookupResult<T> schema, U data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
        out.write(MAGIC_BYTE);
        baseSerde.getIdHandler().writeId(schema.toArtifactReference(), out);
        this.serializeData(schema.getParsedSchema(), data, out);
        return out.toByteArray();
    }

    /**
     * Sets the dead letter queue that receives the records failing a contract rule with
     * {@code onFailure: DLQ}.
     */
    public void setDeadLetterQueue(DeadLetterQueue deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public BaseSerde<T, U> getSerdeConfigurer() {
        return baseSerde;
    }

    /**
     * Applies the WRITE-mode contract rules to a record, when contract rules are enabled, and returns the
     * record to serialize: the output of the TRANSFORM rules if any transformed it, otherwise the record.
     *
     * @throws ContractRuleViolationException if a rule fails and fail-on-error is enabled, or a rule with
     *         {@code onFailure: DLQ} fails (after sending the record to the dead letter queue)
     */
    public U applyContractRulesForWrite(String topic, SchemaLookupResult<T> schema, U data) {
        if (!contractRulesEnabled) {
            return data;
        }
        RuleExecutionResult result;
        try {
            var ref = schema.toArtifactReference();
            if (ref.getArtifactId() == null || ref.getArtifactId().isEmpty()) {
                return data;
            }
            List<RuleDefinition> rules = ruleLoader.domainRules(ref.getGroupId(), ref.getArtifactId(),
                    ref.getVersion());
            if (rules.isEmpty()) {
                return data;
            }
            result = ruleEngine.execute(rules, "WRITE", toRuleRecord(data));
        } catch (RuntimeException e) {
            if (contractRulesFailOnError) {
                throw e;
            }
            LOG.warning("Contract rule execution failed: " + e.getMessage());
            return data;
        }
        if (!result.isPassed()) {
            if (ContractRuleOutcomes.hasDeadLetterViolation(result)) {
                ContractRuleOutcomes.requireDeadLetterQueue(deadLetterQueue, result);
                try {
                    deadLetterQueue.send(topic, writeRecord(schema, data), result.getViolations());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                throw new ContractRuleViolationException("WRITE", result.getViolations(), deadLetterQueue.topic());
            }
            ContractRuleViolationException violation = new ContractRuleViolationException("WRITE",
                    result.getViolations(), null);
            if (contractRulesFailOnError) {
                throw violation;
            }
            LOG.warning(violation.getMessage());
        }
        if (result.getTransformedRecord() == null || !supportsTransformResult(data)) {
            return data;
        }
        return fromRuleRecord(result.getTransformedRecord(), schema.getParsedSchema(), data);
    }

    static List<RuleDefinition> toRuleDefinitions(ContractRuleSet ruleset) {
        List<RuleDefinition> result = new ArrayList<>();
        if (ruleset.getDomainRules() != null) {
            for (ContractRule r : ruleset.getDomainRules()) {
                result.add(toRuleDefinition(r));
            }
        }
        if (ruleset.getMigrationRules() != null) {
            for (ContractRule r : ruleset.getMigrationRules()) {
                result.add(toRuleDefinition(r));
            }
        }
        return result;
    }

    static RuleDefinition toRuleDefinition(ContractRule r) {
        return ContractRuleLoader.toRuleDefinition(r, 0);
    }

    /**
     * Converts a record to the map the contract rule engine works on. The default handles maps and
     * records whose {@code toString()} is JSON; formats override it to convert faithfully.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> toRuleRecord(U data) {
        if (data == null) {
            return Map.of();
        }
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        try {
            return new ObjectMapper().readValue(data.toString(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Whether {@link #fromRuleRecord} can turn the output of TRANSFORM rules back into a record like
     * {@code data}. When it cannot, the rules still run (and can fail the write) but the original record
     * is serialized.
     */
    protected boolean supportsTransformResult(U data) {
        return data instanceof Map;
    }

    /**
     * Builds the record to serialize from the output of the TRANSFORM rules.
     *
     * @param record the transformed record
     * @param schema the schema of the record
     * @param original the record as given to the serializer
     */
    @SuppressWarnings("unchecked")
    protected U fromRuleRecord(Map<String, Object> record, ParsedSchema<T> schema, U original) {
        if (original instanceof Map) {
            return (U) record;
        }
        throw new UnsupportedOperationException("Transform results are not supported by "
                + getClass().getSimpleName());
    }

    @Override
    public void close() {
        this.baseSerde.close();
    }
}
