package io.apicurio.registry.serde;

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
    private final SerDesTracer tracer = new SerDesTracer();

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
    private ContractRulesetCache rulesetCache;

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
            rulesetCache = new ContractRulesetCache(ttl);
        }
    }

    public byte[] serializeData(String topic, U data) {
        if (data == null) {
            return null;
        }
        return tracer.traceSerialize(topic, span -> {
            try {
                SchemaLookupResult<T> schema = null;
                SchemaCacheKey cacheKey = null;

                Object schemaKey = getSchemaCacheKey(data);
                if (schemaKey != null) {
                    cacheKey = new SchemaCacheKey(topic, schemaKey);
                    schema = fastPathCache.get(cacheKey);
                }

                boolean cacheHit = schema != null;

                if (schema == null) {
                    DefaultSchemaResolver.currentOperation.set("SERIALIZE");
                    try {
                        SerdeMetadata resolverMetadata = new SerdeMetadata(topic, baseSerde.isKey());
                        schema = baseSerde.getSchemaResolver()
                                .resolveSchema(new SerdeRecord<>(resolverMetadata, data));
                    } finally {
                        DefaultSchemaResolver.currentOperation.remove();
                    }

                    if (cacheKey != null) {
                        fastPathCache.put(cacheKey, schema);
                    }
                }

                SerDesTracer.setSchemaAttributes(span, schema, cacheHit);

                if (contractRulesEnabled) {
                    executeContractRulesForWrite(schema, data);
                }

                ByteArrayOutputStream out = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
                out.write(MAGIC_BYTE);
                baseSerde.getIdHandler().writeId(schema.toArtifactReference(), out);
                this.serializeData(schema.getParsedSchema(), data, out);

                byte[] result = out.toByteArray();
                span.setAttribute(SerDesAttributes.DATA_SIZE, (long) result.length);
                return result;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public BaseSerde<T, U> getSerdeConfigurer() {
        return baseSerde;
    }

    @SuppressWarnings("unchecked")
    private void executeContractRulesForWrite(SchemaLookupResult<T> schema, U data) {
        try {
            var ref = schema.toArtifactReference();
            if (ref.getArtifactId() == null || ref.getArtifactId().isEmpty()) {
                return;
            }
            List<RuleDefinition> rules = loadRules(ref.getGroupId(), ref.getArtifactId());
            if (rules.isEmpty()) {
                return;
            }
            Map<String, Object> recordMap = dataToMap(data);
            RuleExecutionResult result = ruleEngine.execute(rules, "WRITE", recordMap);
            if (!result.isPassed()) {
                String msg = "Contract rule validation failed (WRITE): " + result.getViolations();
                if (contractRulesFailOnError) {
                    throw new RuntimeException(msg);
                }
                LOG.warning(msg);
            }
        } catch (RuntimeException e) {
            if (contractRulesFailOnError) {
                throw e;
            }
            LOG.warning("Contract rule execution failed: " + e.getMessage());
        }
    }

    private List<RuleDefinition> loadRules(String groupId, String artifactId) {
        ContractRuleSet cached = rulesetCache.get(groupId, artifactId, null);
        if (cached != null) {
            return toRuleDefinitions(cached);
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
        return toRuleDefinitions(ruleset);
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
        RuleDefinition def = new RuleDefinition();
        def.setName(r.getName());
        def.setKind(r.getKind() != null ? r.getKind().getValue() : null);
        def.setType(r.getType());
        def.setMode(r.getMode() != null ? r.getMode().getValue() : null);
        def.setExpr(r.getExpr());
        def.setOnFailure(r.getOnFailure() != null ? r.getOnFailure().getValue() : null);
        def.setDisabled(r.getDisabled() != null && r.getDisabled());
        def.setOrderIndex(0);
        return def;
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
            String json = data.toString();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @Override
    public void close() {
        this.baseSerde.close();
    }
}
