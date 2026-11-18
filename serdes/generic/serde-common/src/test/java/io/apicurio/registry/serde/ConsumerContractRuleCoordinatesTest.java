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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer-side contract rules for records that carry only a global ID or a content ID: the deserializer must
 * find the coordinates of the schema version to load its rules.
 */
class ConsumerContractRuleCoordinatesTest {

    private static final String TOPIC = "orders";
    private static final long CONTENT_ID = 7;
    private static final long GLOBAL_ID = 42;

    /** Calls made to the facade, as "method(args)". */
    private final List<String> calls = new ArrayList<>();

    /** Coordinates the fake registry knows, or null for "not found". */
    private RegistryVersionCoordinates coordinates = RegistryVersionCoordinates.create(GLOBAL_ID, CONTENT_ID,
            "default", TOPIC + "-value", "3");

    private RegistryClientFacade facade() {
        return (RegistryClientFacade) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{RegistryClientFacade.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("getVersionCoordinatesByGlobalId")
                            || name.equals("getVersionCoordinatesByContentId")) {
                        calls.add(name + List.of(args));
                    }
                    return switch (name) {
                        case "getSchemaByContentId", "getSchemaByGlobalId" -> "{}";
                        case "getReferencesByContentId", "getReferencesByGlobalId" -> List.of();
                        case "getVersionCoordinatesByGlobalId", "getVersionCoordinatesByContentId" -> coordinates;
                        case "getGlobalContractRuleset", "getVersionContractRuleset" -> null;
                        case "getContractRuleset" -> args[1].equals(TOPIC + "-value") ? readRule() : null;
                        case "toString" -> "fake facade";
                        default -> throw new UnsupportedOperationException(name);
                    };
                });
    }

    private static ContractRuleSet readRule() {
        ContractRule rule = new ContractRule();
        rule.setName("positive-amount");
        rule.setKind(ContractRuleKind.CONDITION);
        rule.setType("CEL");
        rule.setMode(ContractRuleMode.READ);
        rule.setExpr("totalAmount > 0");
        rule.setOnFailure(ContractRuleOnFailure.ERROR);
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(rule));
        return ruleset;
    }

    /** Returns a fixed record for any payload; the schema is irrelevant. */
    private static final class MapDeserializer extends AbstractDeserializer<String, Map<String, Object>> {

        private final Map<String, Object> record;

        MapDeserializer(RegistryClientFacade facade, Map<String, Object> record) {
            super(facade, new DefaultSchemaResolver<>());
            this.record = record;
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

    private MapDeserializer deserializer(Map<String, Object> record, boolean useGlobalId, boolean failOnError) {
        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, "http://localhost:1/apis/registry/v3");
        config.put(SerdeConfig.CONTRACT_RULES_ENABLED, "true");
        config.put(SerdeConfig.CONTRACT_RULES_FAIL_ON_ERROR, String.valueOf(failOnError));
        if (useGlobalId) {
            config.put(SerdeConfig.USE_ID, "globalId");
        }
        MapDeserializer deserializer = new MapDeserializer(facade(), record);
        deserializer.configure(new SerdeConfig(config), false);
        return deserializer;
    }

    private static byte[] payload(long id) {
        return ByteBuffer.allocate(6).put((byte) 0).putInt((int) id).put((byte) 1).array();
    }

    private static Map<String, Object> order(double amount) {
        return Map.of("orderId", "ORD-1", "totalAmount", amount);
    }

    @Test
    void testGlobalIdRecordIsCheckedAgainstTheRulesOfItsVersion() {
        MapDeserializer deserializer = deserializer(order(-5.0), true, true);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> deserializer.deserializeData(TOPIC, payload(GLOBAL_ID)));
        assertTrue(e.getMessage().startsWith("Contract rule validation failed (READ)"), e.getMessage());

        // The coordinates are looked up once per global ID
        assertThrows(RuntimeException.class, () -> deserializer.deserializeData(TOPIC, payload(GLOBAL_ID)));
        assertEquals(List.of("getVersionCoordinatesByGlobalId[42]"), calls);
    }

    @Test
    void testContentIdRecordIsLookedUpInTheArtifactNamedByTheStrategy() {
        Map<String, Object> valid = order(10.0);
        MapDeserializer deserializer = deserializer(valid, false, true);

        assertSame(valid, deserializer.deserializeData(TOPIC, payload(CONTENT_ID)));
        assertSame(valid, deserializer.deserializeData(TOPIC, payload(CONTENT_ID)));
        // TopicIdStrategy (the default) names the artifact "<topic>-value"; looked up once per content ID
        assertEquals(List.of("getVersionCoordinatesByContentId[7, default, orders-value]"), calls);

        MapDeserializer rejecting = deserializer(order(-5.0), false, true);
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> rejecting.deserializeData(TOPIC, payload(CONTENT_ID)));
        assertTrue(e.getMessage().startsWith("Contract rule validation failed (READ)"), e.getMessage());
    }

    @Test
    void testUnknownVersionFailsWhenFailOnErrorIsSet() {
        coordinates = null;
        MapDeserializer deserializer = deserializer(order(-5.0), false, true);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> deserializer.deserializeData(TOPIC, payload(CONTENT_ID)));
        assertEquals("Consumer-side contract rules cannot be applied: the schema version of the record "
                + "could not be determined (content ID 7)", e.getMessage());
    }

    @Test
    void testUnknownVersionDeliversTheRecordWithoutRulesWhenFailOnErrorIsNotSet() {
        coordinates = null;
        Map<String, Object> invalid = order(-5.0);
        MapDeserializer deserializer = deserializer(invalid, true, false);

        assertSame(invalid, deserializer.deserializeData(TOPIC, payload(GLOBAL_ID)));
        assertSame(invalid, deserializer.deserializeData(TOPIC, payload(GLOBAL_ID)));
        // "Not found" is cached too: one lookup, one warning
        assertEquals(List.of("getVersionCoordinatesByGlobalId[42]"), calls);
    }

    @Test
    void testNoLookupWhenContractRulesAreDisabled() {
        Map<String, Object> invalid = order(-5.0);
        MapDeserializer deserializer = new MapDeserializer(facade(), invalid);
        deserializer.configure(new SerdeConfig(new HashMap<>(Map.of(SerdeConfig.REGISTRY_URL,
                "http://localhost:1/apis/registry/v3"))), false);

        assertSame(invalid, deserializer.deserializeData(TOPIC, payload(CONTENT_ID)));
        assertEquals(List.of(), calls);
    }

    @Test
    void testResolverUsesCoordinatesAlreadyKnownWithoutLookup() {
        ContractRuleCoordinatesResolver resolver = new ContractRuleCoordinatesResolver(this::facade);
        ArtifactReference known = ArtifactReference.builder().groupId("g").artifactId("a").version("1").build();

        assertSame(known, resolver.resolve(known, () -> null).orElseThrow());
        assertEquals(List.of(), calls);
    }

    @Test
    void testResolverSkipsContentIdWhenTheStrategyNamesNoArtifact() {
        ContractRuleCoordinatesResolver resolver = new ContractRuleCoordinatesResolver(this::facade);

        assertTrue(resolver.resolve(ArtifactReference.builder().contentId(CONTENT_ID).build(),
                () -> null).isEmpty());
        assertEquals(List.of(), calls);
    }
}
