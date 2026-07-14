package io.apicurio.registry.serde;

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
import io.apicurio.registry.rest.client.models.ContractRuleSet;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.config.SerdeDeserializerConfig;
import io.apicurio.registry.serde.fallback.DefaultFallbackArtifactProvider;
import io.apicurio.registry.serde.fallback.FallbackArtifactProvider;
import io.apicurio.registry.serde.tracing.SerDesAttributes;
import io.apicurio.registry.serde.tracing.SerDesTracer;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
                executeContractRulesForRead(schema, result);
            }

            return result;
        });
    }

    protected abstract U readData(ParsedSchema<T> schema, ByteBuffer buffer, int start, int length);

    public U readData(String topic, byte[] data, ArtifactReference artifactReference) {
        SchemaLookupResult<T> schema = resolve(topic, data, artifactReference);

        ByteBuffer buffer = ByteBuffer.wrap(data);
        int length = buffer.limit();
        int start = buffer.position();

        return readData(schema.getParsedSchema(), buffer, start, length);
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
            SchemaLookupResult<T> result = baseSerde.getSchemaResolver().resolveSchemaByArtifactReference(artifactReference);
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
                    SchemaLookupResult<T> result = baseSerde.getSchemaResolver().resolveSchemaByArtifactReference(fallbackReference);
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

    @SuppressWarnings("unchecked")
    private void executeContractRulesForRead(SchemaLookupResult<T> schema, U data) {
        try {
            var ref = schema.toArtifactReference();
            if (ref.getArtifactId() == null || ref.getArtifactId().isEmpty()) {
                return;
            }

            // Domain rule validation
            List<RuleDefinition> rules = loadRules(ref.getGroupId(), ref.getArtifactId());
            if (!rules.isEmpty()) {
                Map<String, Object> recordMap = dataToMap(data);
                RuleExecutionResult result = ruleEngine.execute(rules, "READ", recordMap);
                if (!result.isPassed()) {
                    String msg = "Contract rule validation failed (READ): " + result.getViolations();
                    if (contractRulesFailOnError) {
                        throw new RuntimeException(msg);
                    }
                    LOG.warning(msg);
                }
            }

            // Migration transforms
            if (migrationEnabled && migrationTargetVersion != null) {
                String recordVersion = ref.getVersion();
                if (recordVersion != null && !recordVersion.equals(migrationTargetVersion)) {
                    executeMigration(ref.getGroupId(), ref.getArtifactId(),
                            recordVersion, migrationTargetVersion, data);
                }
            }
        } catch (RuntimeException e) {
            if (contractRulesFailOnError) {
                throw e;
            }
            LOG.warning("Contract rule execution failed: " + e.getMessage());
        }
    }

    private void executeMigration(String groupId, String artifactId,
            String fromVersion, String toVersion, U data) {
        List<String> allVersions = rulesetCache.getVersionList(groupId, artifactId);
        if (allVersions == null) {
            var facade = baseSerde.getClientFacade();
            if (facade == null) {
                return;
            }
            allVersions = facade.getArtifactVersions(groupId, artifactId);
            if (allVersions.isEmpty()) {
                return;
            }
            rulesetCache.putVersionList(groupId, artifactId, allVersions);
        }

        Map<String, Object> recordMap = dataToMap(data);
        MigrationExecutor migrationExecutor = new MigrationExecutor(ruleEngine);

        RuleExecutionResult result = migrationExecutor.execute(
                allVersions, fromVersion, toVersion, recordMap,
                (version, mode) -> loadMigrationRulesForVersion(
                        groupId, artifactId, version));

        if (!result.isPassed()) {
            String msg = "Migration transform failed (" + fromVersion + " -> " + toVersion + "): "
                    + result.getViolations();
            if (contractRulesFailOnError) {
                throw new RuntimeException(msg);
            }
            LOG.warning(msg);
        }
    }

    private List<RuleDefinition> loadMigrationRulesForVersion(String groupId,
            String artifactId, String version) {
        ContractRuleSet versionRules = rulesetCache.get(groupId, artifactId, version);
        if (versionRules == null) {
            var facade = baseSerde.getClientFacade();
            if (facade == null) {
                return List.of();
            }
            versionRules = facade.getVersionContractRuleset(groupId, artifactId, version);
            if (versionRules == null) {
                return List.of();
            }
            rulesetCache.put(groupId, artifactId, version, versionRules);
        }

        List<RuleDefinition> migrationRules = new java.util.ArrayList<>();
        if (versionRules.getMigrationRules() != null) {
            for (var r : versionRules.getMigrationRules()) {
                migrationRules.add(AbstractSerializer.toRuleDefinition(r));
            }
        }
        return migrationRules;
    }

    private List<RuleDefinition> loadRules(String groupId, String artifactId) {
        ContractRuleSet cached = rulesetCache.get(groupId, artifactId, null);
        if (cached != null) {
            return AbstractSerializer.toRuleDefinitions(cached);
        }
        var facade = baseSerde.getClientFacade();
        if (facade == null) {
            return List.of();
        }
        ContractRuleSet ruleset = facade.getContractRuleset(groupId, artifactId);
        if (ruleset == null) {
            return List.of();
        }
        rulesetCache.put(groupId, artifactId, null, ruleset);
        return AbstractSerializer.toRuleDefinitions(ruleset);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataToMap(U data) {
        if (data == null) {
            return Map.of();
        }
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(data.toString(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public void close() {
        this.baseSerde.close();
    }
}
