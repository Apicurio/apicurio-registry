package io.apicurio.registry.serde;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.contracts.rules.MigrationExecutor;
import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.contracts.rules.RuleExecutionEngine;
import io.apicurio.registry.contracts.rules.RuleExecutionResult;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.ContractRulesetCache;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.config.SerdeDeserializerConfig;
import io.apicurio.registry.serde.data.SerdeMetadata;
import io.apicurio.registry.serde.data.SerdeRecord;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import io.apicurio.registry.serde.tracing.SerDesAttributes;
import io.apicurio.registry.serde.tracing.SerDesTracer;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.apicurio.registry.serde.BaseSerde.getByteBuffer;

public abstract class AbstractDeserializer<T, U> implements AutoCloseable {

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(
            AbstractDeserializer.class.getName());
    private static final SerDesTracer tracer = new SerDesTracer();

    /**
     * Cache key that distinguishes between contentId and globalId to avoid collisions.
     * contentId=5 and globalId=5 could refer to different schemas, so we need to differentiate.
     */
    private record SchemaCacheKey(boolean isContentId, long id) {}

    /**
     * Fast-path cache: maps schema ID (contentId or globalId) directly to resolved schema.
     * This bypasses the full resolution flow after the first deserialization for a given schema.
     */
    private final ConcurrentHashMap<SchemaCacheKey, SchemaLookupResult<T>> fastPathCache = new ConcurrentHashMap<>();

    private FallbackArtifactProvider fallbackArtifactProvider;
    private final BaseSerde<T, U> baseSerde;
    private boolean contractRulesEnabled = false;
    private boolean contractRulesFailOnError = true;
    private boolean migrationEnabled = false;
    private String migrationTargetVersion;
    private RuleExecutionEngine ruleEngine;
    private ContractRulesetCache rulesetCache;
    private ContractRuleLoader ruleLoader;
    private ContractRuleCoordinatesResolver coordinatesResolver;
    private DeadLetterQueue deadLetterQueue;
    private final AtomicBoolean unsupportedMigrationWarned = new AtomicBoolean();

    public AbstractDeserializer() {
        this.baseSerde = new BaseSerde<>();
    }

    public AbstractDeserializer(RegistryClientFacade clientFacade) {
        this.baseSerde = new BaseSerde<>(clientFacade);
    }

    public AbstractDeserializer(SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(schemaResolver);
    }

    public AbstractDeserializer(RegistryClientFacade clientFacade, SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(clientFacade, schemaResolver);
    }

    public AbstractDeserializer(RegistryClientFacade clientFacade, ArtifactReferenceResolverStrategy<T, U> strategy,
                                SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(clientFacade, strategy, schemaResolver);
    }

    public BaseSerde<T, U> getSerdeConfigurer() {
        return baseSerde;
    }

    public void configure(SerdeConfig config, boolean isKey) {
        baseSerde.configure(config, isKey, schemaParser());
        configureDeserialization(config, isKey);

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
            rulesetCache = new ContractRulesetCache(ttl);
            ruleLoader = new ContractRuleLoader(rulesetCache, baseSerde::getClientFacade);
            coordinatesResolver = new ContractRuleCoordinatesResolver(baseSerde::getClientFacade,
                    Duration.ofSeconds(ttl));
            Object migEnabled = config.originals().get(SerdeConfig.CONTRACT_RULES_MIGRATION_ENABLED);
            if (migEnabled != null) {
                migrationEnabled = Boolean.parseBoolean(migEnabled.toString());
            }
            migrationTargetVersion = (String) config.originals().get(
                    SerdeConfig.CONTRACT_RULES_MIGRATION_TARGET_VERSION);
        }
    }

    private void configureDeserialization(SerdeConfig config, boolean isKey) {
        SerdeDeserializerConfig deserializerConfig = new SerdeDeserializerConfig(config.originals());

        Object fallbackProvider = deserializerConfig.getFallbackArtifactProvider();
        Utils.instantiate(FallbackArtifactProvider.class, fallbackProvider,
                this::setFallbackArtifactProvider);
        fallbackArtifactProvider.configure(config.originals(), isKey);

        if (fallbackArtifactProvider instanceof DefaultFallbackArtifactProvider) {
            if (!((DefaultFallbackArtifactProvider) fallbackArtifactProvider).isConfigured()) {
                // it's not configured, just remove it so it's not executed
                fallbackArtifactProvider = null;
            }
        }
    }

    public abstract SchemaParser<T, U> schemaParser();

    public U deserializeData(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        return tracer.traceDeserialize(topic, span -> {
            span.setAttribute(SerDesAttributes.DATA_SIZE, (long) data.length);

            ByteBuffer buffer = getByteBuffer(data);
            ArtifactReference artifactReference = baseSerde.getIdHandler().readId(buffer);

            SchemaCacheKey cacheKey = getCacheKey(artifactReference);
            boolean cacheHit = cacheKey != null && fastPathCache.containsKey(cacheKey);

            SchemaLookupResult<T> schema = resolve(topic, data, artifactReference);
            SerDesTracer.setSchemaAttributes(span, schema, cacheHit);

            int length = buffer.limit() - 1 - baseSerde.getIdHandler().idSize(artifactReference, buffer);
            int start = buffer.position() + buffer.arrayOffset();

            U result = readData(schema.getParsedSchema(), buffer, start, length);

            if (contractRulesEnabled) {
                return executeContractRulesForRead(topic, data, artifactReference, schema, result);
            }

            return result;
        });
    }

    protected abstract U readData(ParsedSchema<T> schema, ByteBuffer buffer, int start, int length);

    /**
     * Deserializes a payload without an ID prefix, whose schema is given by {@code artifactReference}
     * (from the record headers, for example). Traced and checked against contract rules exactly as
     * {@link #deserializeData(String, byte[])} is.
     */
    public U readData(String topic, byte[] data, ArtifactReference artifactReference) {
        return tracer.traceDeserialize(topic, span -> {
            span.setAttribute(SerDesAttributes.DATA_SIZE, (long) data.length);

            SchemaCacheKey cacheKey = getCacheKey(artifactReference);
            boolean cacheHit = cacheKey != null && fastPathCache.containsKey(cacheKey);

            SchemaLookupResult<T> schema = resolve(topic, data, artifactReference);
            SerDesTracer.setSchemaAttributes(span, schema, cacheHit);

            ByteBuffer buffer = ByteBuffer.wrap(data);
            int length = buffer.limit();
            int start = buffer.position();

            U result = readData(schema.getParsedSchema(), buffer, start, length);

            if (contractRulesEnabled) {
                return executeContractRulesForRead(topic, data, artifactReference, schema, result);
            }

            return result;
        });
    }

    protected SchemaLookupResult<T> resolve(String topic, byte[] data, ArtifactReference artifactReference) {
        DefaultSchemaResolver.currentOperation.set("DESERIALIZE");
        try {
            return doResolve(topic, data, artifactReference);
        } finally {
            DefaultSchemaResolver.currentOperation.remove();
        }
    }

    private SchemaLookupResult<T> doResolve(String topic, byte[] data, ArtifactReference artifactReference) {
        // Fast path: check cache using contentId or globalId
        SchemaCacheKey cacheKey = getCacheKey(artifactReference);
        if (cacheKey != null) {
            SchemaLookupResult<T> cached = fastPathCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        // Slow path: full resolution
        try {
            SchemaLookupResult<T> result = tracer.traceSchemaResolve(topic, "deserialize",
                    span -> baseSerde.getSchemaResolver().resolveSchemaByArtifactReference(artifactReference));
            // Cache the result if we have a valid cache key
            if (cacheKey != null) {
                fastPathCache.put(cacheKey, result);
            }
            return result;
        } catch (RuntimeException e) {
            if (getFallbackArtifactProvider() == null) {
                throw e;
            } else {
                try {
                    ArtifactReference fallbackReference = getFallbackArtifactProvider().get(topic, data);
                    SchemaLookupResult<T> result = tracer.traceSchemaResolve(topic, "deserialize",
                            span -> baseSerde.getSchemaResolver().resolveSchemaByArtifactReference(fallbackReference));
                    // Cache using fallback reference key
                    SchemaCacheKey fallbackKey = getCacheKey(fallbackReference);
                    if (fallbackKey != null) {
                        fastPathCache.put(fallbackKey, result);
                    }
                    return result;
                } catch (RuntimeException fe) {
                    fe.addSuppressed(e);
                    throw fe;
                }
            }
        }
    }

    /**
     * Gets the cache key from an artifact reference.
     * Uses a composite key that distinguishes between contentId and globalId to avoid collisions.
     * Returns null if the reference is null or has no usable identifiers.
     */
    private SchemaCacheKey getCacheKey(ArtifactReference reference) {
        if (reference == null) {
            return null;
        }
        if (reference.getContentId() != null) {
            return new SchemaCacheKey(true, reference.getContentId());
        }
        if (reference.getGlobalId() != null) {
            return new SchemaCacheKey(false, reference.getGlobalId());
        }
        return null;
    }

    public FallbackArtifactProvider getFallbackArtifactProvider() {
        return this.fallbackArtifactProvider;
    }

    public void setFallbackArtifactProvider(FallbackArtifactProvider fallbackArtifactProvider) {
        this.fallbackArtifactProvider = fallbackArtifactProvider;
    }

    /**
     * Applies the consumer-side contract rules to a deserialized record.
     *
     * @return the record to hand to the caller: the migrated record if a migration ran and its result
     *         could be applied, otherwise {@code data} itself
     */
    private U executeContractRulesForRead(String topic, byte[] payload, ArtifactReference recordReference,
            SchemaLookupResult<T> schema, U data) {
        try {
            var resolved = coordinatesResolver.resolve(readReference(recordReference, schema),
                    () -> baseSerde.getSchemaResolver().resolveArtifactReference(
                            new SerdeRecord<>(new SerdeMetadata(topic, baseSerde.isKey()), data),
                            schema.getParsedSchema()));
            if (resolved.isEmpty()) {
                if (contractRulesFailOnError) {
                    throw new IllegalStateException("Consumer-side contract rules cannot be applied: the schema "
                            + "version of the record could not be determined (" + describe(recordReference) + ")");
                }
                return data;
            }
            var ref = resolved.get();
            // Domain rule validation
            List<RuleDefinition> rules = ruleLoader.domainRules(ref.getGroupId(), ref.getArtifactId(),
                    ref.getVersion());
            U result = data;
            if (!rules.isEmpty()) {
                result = applyReadRules(topic, payload, rules, schema, data);
            }

            // Migration transforms
            if (migrationEnabled && migrationTargetVersion != null) {
                String recordVersion = ref.getVersion();
                if (recordVersion != null && !recordVersion.equals(migrationTargetVersion)) {
                    return executeMigration(topic, payload, ref.getGroupId(), ref.getArtifactId(),
                            recordVersion, migrationTargetVersion, result);
                }
            }
            return result;
        } catch (ContractRuleViolationException e) {
            throw e;
        } catch (RuntimeException e) {
            if (contractRulesFailOnError) {
                throw e;
            }
            LOG.warning("Contract rule execution failed: " + e.getMessage());
        }
        return data;
    }

    /**
     * The coordinates of the schema if the resolver knows them; otherwise the coordinates the record
     * carries (headers can give group, artifact and version with a content hash); otherwise the IDs the
     * record was read with.
     */
    private static ArtifactReference readReference(ArtifactReference recordReference,
            SchemaLookupResult<?> schema) {
        if (schema.getArtifactId() != null && !schema.getArtifactId().isEmpty()) {
            return schema.toArtifactReference();
        }
        if (recordReference != null && recordReference.getArtifactId() != null
                && !recordReference.getArtifactId().isEmpty() && recordReference.getVersion() != null) {
            return recordReference;
        }
        Long globalId = recordReference != null ? recordReference.getGlobalId() : null;
        Long contentId = recordReference != null ? recordReference.getContentId() : null;
        return ArtifactReference.builder()
                .globalId(globalId != null ? globalId : schema.getGlobalId())
                .contentId(contentId != null ? contentId : schema.getContentId())
                .build();
    }

    private static String describe(ArtifactReference reference) {
        if (reference == null) {
            return "no schema reference";
        }
        if (reference.getGlobalId() != null) {
            return "global ID " + reference.getGlobalId();
        }
        if (reference.getContentId() != null) {
            return "content ID " + reference.getContentId();
        }
        return "no global ID or content ID";
    }

    /**
     * Runs the READ rules. A failed rule with {@code onFailure: DLQ} sends the record to the dead letter
     * queue; the output of TRANSFORM rules is returned when the format can build a record from it.
     */
    private U applyReadRules(String topic, byte[] payload, List<RuleDefinition> rules, SchemaLookupResult<T> schema,
            U data) {
        RuleExecutionResult result = ruleEngine.execute(rules, "READ", toRuleRecord(data));
        checkOutcome(topic, payload, "READ", result);
        if (result.getTransformedRecord() == null || !supportsTransformResult(data)) {
            return data;
        }
        return fromRuleRecord(result.getTransformedRecord(), schema.getParsedSchema(), data);
    }

    /**
     * Routes the record to the dead letter queue when a failed rule asks for it, otherwise fails or logs
     * according to fail-on-error.
     */
    private void checkOutcome(String topic, byte[] payload, String mode, RuleExecutionResult outcome) {
        if (outcome.isPassed()) {
            return;
        }
        if (ContractRuleOutcomes.hasDeadLetterViolation(outcome)) {
            ContractRuleOutcomes.requireDeadLetterQueue(deadLetterQueue, outcome);
            deadLetterQueue.send(topic, payload, outcome.getViolations());
            throw new ContractRuleViolationException(mode, outcome.getViolations(), deadLetterQueue.topic());
        }
        ContractRuleViolationException violation = new ContractRuleViolationException(mode,
                outcome.getViolations(), null);
        if (contractRulesFailOnError) {
            throw violation;
        }
        LOG.warning(violation.getMessage());
    }

    /**
     * Sets the dead letter queue that receives the records failing a contract rule with
     * {@code onFailure: DLQ}.
     */
    public void setDeadLetterQueue(DeadLetterQueue deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    private U executeMigration(String topic, byte[] payload, String groupId, String artifactId,
            String fromVersion, String toVersion, U data) {
        List<String> allVersions = rulesetCache.getVersionList(groupId, artifactId);
        if (allVersions == null) {
            var facade = baseSerde.getClientFacade();
            if (facade == null) {
                return data;
            }
            allVersions = facade.getArtifactVersions(groupId, artifactId);
            if (allVersions.isEmpty()) {
                return data;
            }
            rulesetCache.putVersionList(groupId, artifactId, allVersions);
        }

        Map<String, Object> recordMap = toRuleRecord(data);
        MigrationExecutor migrationExecutor = new MigrationExecutor(ruleEngine);

        RuleExecutionResult result = migrationExecutor.execute(
                allVersions, fromVersion, toVersion, recordMap,
                (version, mode) -> ruleLoader.migrationRules(groupId, artifactId, version));

        if (!result.isPassed() && ContractRuleOutcomes.hasDeadLetterViolation(result)) {
            checkOutcome(topic, payload, "MIGRATION " + fromVersion + " -> " + toVersion, result);
        }
        if (!result.isPassed()) {
            String msg = "Migration transform failed (" + fromVersion + " -> " + toVersion + "): "
                    + result.getViolations();
            if (contractRulesFailOnError) {
                throw new RuntimeException(msg);
            }
            LOG.warning(msg);
            return data;
        }
        if (result.getExecutedRules() == 0 || result.getTransformedRecord() == null) {
            return data;
        }
        if (!supportsMigrationResult(data)) {
            if (unsupportedMigrationWarned.compareAndSet(false, true)) {
                LOG.warning("Migration rules ran (" + fromVersion + " -> " + toVersion + ") but "
                        + getClass().getSimpleName() + " cannot return a migrated "
                        + data.getClass().getName() + " yet; records are returned unmigrated");
            }
            return data;
        }

        ParsedSchema<T> targetSchema = baseSerde.getSchemaResolver().resolveSchemaByArtifactReference(
                ArtifactReference.builder().groupId(groupId).artifactId(artifactId).version(toVersion).build())
                .getParsedSchema();
        try {
            return fromRuleRecord(result.getTransformedRecord(), targetSchema, data);
        } catch (RuntimeException e) {
            String msg = "Migration transform failed (" + fromVersion + " -> " + toVersion
                    + "): the migrated record does not match the target schema: " + e.getMessage();
            if (contractRulesFailOnError) {
                throw new RuntimeException(msg, e);
            }
            LOG.warning(msg);
            return data;
        }
    }

    /**
     * Converts a deserialized record to the map the contract rule engine works on. The default handles
     * maps and records whose {@code toString()} is JSON; formats override it to convert faithfully.
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
     * Whether {@link #fromRuleRecord} can turn a migrated rule record back into a record like
     * {@code data}. When it cannot, migration rules still run (and can fail the read) but the
     * original record is returned.
     */
    protected boolean supportsMigrationResult(U data) {
        return false;
    }

    /**
     * Whether {@link #fromRuleRecord} can turn the output of READ TRANSFORM rules, which keeps the
     * record's own schema version, back into a record like {@code data}. Defaults to
     * {@link #supportsMigrationResult}.
     */
    protected boolean supportsTransformResult(U data) {
        return supportsMigrationResult(data);
    }

    /**
     * Builds the record to return to the caller from a migrated rule record.
     *
     * @param record the migrated record
     * @param targetSchema the schema of the migration target version
     * @param original the record as it was deserialized
     * @throws RuntimeException if the migrated record does not conform to the target schema
     */
    protected U fromRuleRecord(Map<String, Object> record, ParsedSchema<T> targetSchema, U original) {
        throw new UnsupportedOperationException("Migration results are not supported by "
                + getClass().getSimpleName());
    }

    @Override
    public void close() {
        this.baseSerde.close();
    }
}
