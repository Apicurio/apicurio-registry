package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.client.RegistryVersionCoordinates;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.models.ContractRule;
import io.apicurio.registry.rest.client.models.ContractRuleKind;
import io.apicurio.registry.rest.client.models.ContractRuleMode;
import io.apicurio.registry.rest.client.models.ContractRuleOnFailure;
import io.apicurio.registry.rest.client.models.ContractRuleSet;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The deserializer returns the record produced by the migration rules, built by the format's
 * {@link AbstractDeserializer#fromRuleRecord} against the schema of the target version.
 */
class ConsumerMigrationResultTest {

    private static final String TOPIC = "orders";
    private static final String ARTIFACT = TOPIC + "-value";
    private static final long GLOBAL_ID = 42;
    private static final long TARGET_GLOBAL_ID = 43;

    /** Facade calls other than ID-based schema lookups, as "method(args)". */
    private final List<String> calls = new ArrayList<>();
    private ContractRuleSet v2Ruleset = upgradeRule("$merge([$, {\"currency\": \"USD\"}])");

    private RegistryClientFacade facade() {
        return (RegistryClientFacade) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RegistryClientFacade.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getVersionCoordinatesByGAV") || name.equals("getArtifactVersions")) {
                        calls.add(name + List.of(args));
                    }
                    return switch (name) {
                        case "getSchemaByGlobalId" -> args[0].equals(TARGET_GLOBAL_ID) ? "schema-v2" : "schema-v1";
                        case "getReferencesByGlobalId" -> List.of();
                        case "getVersionCoordinatesByGlobalId" -> RegistryVersionCoordinates.create(GLOBAL_ID, 7L,
                                "default", ARTIFACT, "1");
                        case "getVersionCoordinatesByGAV" -> RegistryVersionCoordinates.create(TARGET_GLOBAL_ID, 8L,
                                "default", ARTIFACT, "2");
                        case "getArtifactVersions" -> List.of("1", "2");
                        case "getGlobalContractRuleset", "getContractRuleset" -> null;
                        case "getVersionContractRuleset" -> args[2].equals("2") ? v2Ruleset : null;
                        case "toString" -> "fake facade";
                        default -> throw new UnsupportedOperationException(name);
                    };
                });
    }

    private static ContractRuleSet upgradeRule(String expr) {
        ContractRule rule = new ContractRule();
        rule.setName("default-currency");
        rule.setKind(ContractRuleKind.TRANSFORM);
        rule.setType("JSONATA");
        rule.setMode(ContractRuleMode.UPGRADE);
        rule.setExpr(expr);
        rule.setOnFailure(ContractRuleOnFailure.ERROR);
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setMigrationRules(List.of(rule));
        return ruleset;
    }

    /**
     * Reads a fixed record. A migrated record is returned as a copy tagged with the target schema
     * under "_schema", or rejected if the migration produced a "reject" entry.
     */
    private static final class TaggingDeserializer extends AbstractDeserializer<String, Map<String, Object>> {

        private final Map<String, Object> record;
        private final boolean supported;

        TaggingDeserializer(RegistryClientFacade facade, Map<String, Object> record, boolean supported) {
            super(facade, new DefaultSchemaResolver<>());
            this.record = record;
            this.supported = supported;
        }

        @Override
        protected boolean supportsMigrationResult(Map<String, Object> data) {
            return supported;
        }

        @Override
        protected Map<String, Object> fromRuleRecord(Map<String, Object> migrated,
                ParsedSchema<String> targetSchema, Map<String, Object> original) {
            if (migrated.containsKey("reject")) {
                throw new IllegalArgumentException("field 'reject' is not in " + targetSchema.getParsedSchema());
            }
            Map<String, Object> result = new LinkedHashMap<>(migrated);
            result.put("_schema", targetSchema.getParsedSchema());
            return result;
        }

        @Override
        public SchemaParser<String, Map<String, Object>> schemaParser() {
            return new SchemaParser<>() {
                @Override
                public String artifactType() {
                    return "JSON";
                }

                @Override
                public String parseSchema(byte[] rawSchema, Map<String, ParsedSchema<String>> resolvedReferences) {
                    return new String(rawSchema);
                }

                @Override
                public ParsedSchema<String> getSchemaFromData(Record<Map<String, Object>> data) {
                    return null;
                }

                @Override
                public ParsedSchema<String> getSchemaFromData(Record<Map<String, Object>> data,
                        boolean dereference) {
                    return null;
                }
            };
        }

        @Override
        protected Map<String, Object> readData(ParsedSchema<String> schema, ByteBuffer buffer, int start,
                int length) {
            return record;
        }
    }

    private TaggingDeserializer deserializer(String targetVersion, boolean supported, boolean failOnError) {
        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, "http://localhost:1/apis/registry/v3");
        config.put(SerdeConfig.USE_ID, "globalId");
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, String.valueOf(failOnError));
        config.put(SerdeConfig.CONTRACT_RULES_MIGRATION_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_MIGRATION_TARGET_VERSION, targetVersion);
        TaggingDeserializer deserializer = new TaggingDeserializer(facade(), ORDER, supported);
        deserializer.configure(new SerdeConfig(config), false);
        return deserializer;
    }

    private static final Map<String, Object> ORDER = Map.of("orderId", "ORD-1", "totalAmount", 50.0);

    private static byte[] payload() {
        return ByteBuffer.allocate(6).put((byte) 0).putInt((int) GLOBAL_ID).put((byte) 1).array();
    }

    @Test
    void testMigratedRecordIsReturnedWithTheTargetSchema() {
        TaggingDeserializer deserializer = deserializer("2", true, true);

        Map<String, Object> read = deserializer.deserializeData(TOPIC, payload());
        assertEquals(Map.of("orderId", "ORD-1", "totalAmount", 50.0, "currency", "USD", "_schema", "schema-v2"),
                read);

        // The headerless path returns it too, and the target schema is resolved only once
        Map<String, Object> again = deserializer.readData(TOPIC, new byte[] { 1 },
                ArtifactReference.builder().globalId(GLOBAL_ID).build());
        assertEquals("USD", again.get("currency"));
        assertEquals(List.of("getArtifactVersions[default, orders-value]",
                "getVersionCoordinatesByGAV[default, orders-value, 2]"), calls);
    }

    @Test
    void testRecordAlreadyAtTheTargetVersionIsReturnedUntouched() {
        assertSame(ORDER, deserializer("1", true, true).deserializeData(TOPIC, payload()));
        assertEquals(List.of(), calls);
    }

    @Test
    void testRecordIsReturnedUntouchedWhenNoMigrationRuleIsOnThePath() {
        v2Ruleset = null;
        assertSame(ORDER, deserializer("2", true, true).deserializeData(TOPIC, payload()));
        assertEquals(List.of("getArtifactVersions[default, orders-value]"), calls);
    }

    @Test
    void testFormatsWithoutMigrationResultsReturnTheOriginalRecord() {
        TaggingDeserializer deserializer = deserializer("2", false, true);
        assertSame(ORDER, deserializer.deserializeData(TOPIC, payload()));
        assertSame(ORDER, deserializer.deserializeData(TOPIC, payload()));
    }

    @Test
    void testMigratedRecordThatDoesNotFitTheTargetSchemaFailsTheRead() {
        v2Ruleset = upgradeRule("$merge([$, {\"reject\": true}])");
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> deserializer("2", true, true).deserializeData(TOPIC, payload()));
        assertEquals("Migration transform failed (1 -> 2): the migrated record does not match the target "
                + "schema: field 'reject' is not in schema-v2", e.getMessage());

        assertSame(ORDER, deserializer("2", true, false).deserializeData(TOPIC, payload()));
    }
}
