package io.apicurio.registry.serde.avro;

import io.apicurio.registry.contracts.rules.RuleDefinition;
import io.apicurio.registry.contracts.rules.RuleExecutionEngine;
import io.apicurio.registry.contracts.rules.RuleExecutionResult;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroRuleRecordConverterTest {

    private static final String V1 = "{\"type\":\"record\",\"name\":\"Order\",\"namespace\":\"com.example\","
            + "\"fields\":["
            + "{\"name\":\"id\",\"type\":\"string\"},"
            + "{\"name\":\"quantity\",\"type\":\"int\"},"
            + "{\"name\":\"amount\",\"type\":\"double\"},"
            + "{\"name\":\"customer\",\"type\":{\"type\":\"record\",\"name\":\"Customer\",\"fields\":["
            + "  {\"name\":\"email\",\"type\":\"string\"},"
            + "  {\"name\":\"vip\",\"type\":\"boolean\"}]}},"
            + "{\"name\":\"tags\",\"type\":{\"type\":\"array\",\"items\":\"string\"}},"
            + "{\"name\":\"attributes\",\"type\":{\"type\":\"map\",\"values\":\"long\"}},"
            + "{\"name\":\"note\",\"type\":[\"null\",\"string\"],\"default\":null},"
            + "{\"name\":\"status\",\"type\":{\"type\":\"enum\",\"name\":\"Status\",\"symbols\":[\"NEW\",\"PAID\"]}},"
            + "{\"name\":\"day\",\"type\":{\"type\":\"int\",\"logicalType\":\"date\"}},"
            + "{\"name\":\"price\",\"type\":{\"type\":\"bytes\",\"logicalType\":\"decimal\","
            + "\"precision\":9,\"scale\":2}},"
            + "{\"name\":\"code\",\"type\":{\"type\":\"fixed\",\"name\":\"Code\",\"size\":2}}"
            + "]}";

    /** Version 2 drops note and adds currency with a default. */
    private static final String V2 = V1
            .replace("{\"name\":\"note\",\"type\":[\"null\",\"string\"],\"default\":null},",
                    "{\"name\":\"currency\",\"type\":\"string\",\"default\":\"EUR\"},");

    private final Schema v1 = new Schema.Parser().parse(V1);

    private GenericRecord order(String note) {
        GenericRecord customer = new GenericData.Record(v1.getField("customer").schema());
        customer.put("email", "a@example.com");
        customer.put("vip", true);
        GenericRecord order = new GenericData.Record(v1);
        order.put("id", "A1");
        order.put("quantity", 3);
        order.put("amount", 99.99);
        order.put("customer", customer);
        order.put("tags", new GenericData.Array<>(v1.getField("tags").schema(), List.of("x", "y")));
        order.put("attributes", Map.of("weight", 7L));
        order.put("note", note);
        order.put("status", new GenericData.EnumSymbol(v1.getField("status").schema(), "PAID"));
        order.put("day", (int) LocalDate.of(2026, 11, 18).toEpochDay());
        order.put("price", ByteBuffer.wrap(BigInteger.valueOf(12345).toByteArray()));
        order.put("code", new GenericData.Fixed(v1.getField("code").schema(), new byte[] { 1, (byte) 200 }));
        return order;
    }

    @Test
    void toMapConvertsEveryTypeToPlainValues() {
        Map<String, Object> map = AvroRuleRecordConverter.toMap(order("fragile"));

        assertEquals("A1", map.get("id"));
        assertEquals(3, map.get("quantity"));
        assertEquals(99.99, map.get("amount"));
        assertEquals(Map.of("email", "a@example.com", "vip", true), map.get("customer"));
        assertEquals(List.of("x", "y"), map.get("tags"));
        assertEquals(Map.of("weight", 7L), map.get("attributes"));
        assertEquals("fragile", map.get("note"));
        assertEquals("PAID", map.get("status"));
        assertEquals((int) LocalDate.of(2026, 11, 18).toEpochDay(), map.get("day"));
        assertEquals("\u0001\u00c8", map.get("code"));
        assertArrayEquals(BigInteger.valueOf(12345).toByteArray(),
                ((String) map.get("price")).getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void roundTripToTheSameSchemaGivesAnEqualRecord() {
        GenericRecord original = order(null);

        GenericRecord copy = AvroRuleRecordConverter.fromMap(AvroRuleRecordConverter.toMap(original), v1);

        assertEquals(original, copy);
        assertNull(copy.get("note"));
        assertEquals(new BigDecimal("123.45"), new Conversions.DecimalConversion()
                .fromBytes((ByteBuffer) copy.get("price"), v1.getField("price").schema(),
                        v1.getField("price").schema().getLogicalType()));
    }

    @Test
    void fromMapBuildsTheTargetSchemaWithDefaultsAndDropsRemovedFields() {
        Schema v2 = new Schema.Parser().parse(V2);

        GenericRecord migrated = AvroRuleRecordConverter.fromMap(
                AvroRuleRecordConverter.toMap(order("dropped")), v2);

        assertEquals(v2, migrated.getSchema());
        assertEquals("EUR", migrated.get("currency").toString());
        assertFalse(migrated.hasField("note"));
        assertEquals("A1", migrated.get("id").toString());
        assertEquals("a@example.com", ((GenericRecord) migrated.get("customer")).get("email").toString());
        assertEquals("PAID", migrated.get("status").toString());
        assertTrue(GenericData.get().validate(v2, migrated));
    }

    @Test
    void fromMapAcceptsTheOutputOfAJsonataUpgradeRule() {
        Schema v2 = new Schema.Parser().parse(V2);
        RuleDefinition rule = new RuleDefinition();
        rule.setName("default-currency");
        rule.setKind("TRANSFORM");
        rule.setType("JSONATA");
        rule.setMode("UPGRADE");
        rule.setExpr("$merge([$, {\"currency\": \"USD\", \"quantity\": quantity + 1}])");

        RuleExecutionResult result = RuleExecutionEngine.createStandalone().execute(List.of(rule), "UPGRADE",
                AvroRuleRecordConverter.toMap(order(null)));
        GenericRecord migrated = AvroRuleRecordConverter.fromMap(result.getTransformedRecord(), v2);

        assertEquals("USD", migrated.get("currency").toString());
        assertEquals(4, migrated.get("quantity"));
        assertEquals(99.99, migrated.get("amount"));
        assertEquals(7L, ((Map<?, ?>) migrated.get("attributes")).get("weight"));
        assertEquals(List.of(new Utf8("x"), new Utf8("y")), migrated.get("tags"));
    }

    @Test
    void fromMapRejectsRecordsThatDoNotMatchTheSchema() {
        Map<String, Object> wrongType = new LinkedHashMap<>(AvroRuleRecordConverter.toMap(order(null)));
        wrongType.put("quantity", "three");
        AvroRuntimeException e = assertThrows(AvroRuntimeException.class,
                () -> AvroRuleRecordConverter.fromMap(wrongType, v1));
        assertEquals("field 'quantity': String does not match Avro type int", e.getMessage());

        Map<String, Object> missing = new LinkedHashMap<>(AvroRuleRecordConverter.toMap(order(null)));
        missing.remove("id");
        e = assertThrows(AvroRuntimeException.class, () -> AvroRuleRecordConverter.fromMap(missing, v1));
        assertEquals("missing required field 'id'", e.getMessage());

        Map<String, Object> badEnum = new LinkedHashMap<>(AvroRuleRecordConverter.toMap(order(null)));
        badEnum.put("status", "SHIPPED");
        e = assertThrows(AvroRuntimeException.class, () -> AvroRuleRecordConverter.fromMap(badEnum, v1));
        assertEquals("field 'status': String does not match Avro type enum", e.getMessage());
    }

    private static Schema parse(String json) {
        return new Schema.Parser().parse(json);
    }

    @Test
    void integersAreConvertedExactlyOrRejected() {
        Schema schema = parse("{\"type\":\"record\",\"name\":\"N\",\"fields\":["
                + "{\"name\":\"i\",\"type\":\"int\"},{\"name\":\"l\",\"type\":\"long\"}]}");
        GenericRecord ok = AvroRuleRecordConverter.fromMap(Map.of("i", 5.0, "l", (double) (1L << 52)), schema);
        assertEquals(5, ok.get("i"));
        assertEquals(1L << 52, ok.get("l"));
        // Too large for a long (would saturate to Long.MAX_VALUE), too large for an int, fractional
        for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of("i", 1, "l", 1e20),
                Map.of("i", 3_000_000_000L, "l", 1L), Map.of("i", 1, "l", 2.5))) {
            assertThrows(AvroRuntimeException.class, () -> AvroRuleRecordConverter.fromMap(bad, schema),
                    bad.toString());
        }
    }

    @Test
    void unionKeepsTheBranchThatRepresentsTheValueExactly() {
        Schema schema = parse("{\"type\":\"record\",\"name\":\"U\",\"fields\":[{\"name\":\"v\",\"type\":["
                + "{\"type\":\"record\",\"name\":\"A\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]},"
                + "{\"type\":\"record\",\"name\":\"B\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},"
                + "{\"name\":\"detail\",\"type\":\"string\"}]}]}]}");
        GenericRecord b = new GenericData.Record(schema.getField("v").schema().getTypes().get(1));
        b.put("id", "1");
        b.put("detail", "kept");
        GenericRecord original = new GenericData.Record(schema);
        original.put("v", b);

        GenericRecord result = AvroRuleRecordConverter.fromMap(AvroRuleRecordConverter.toMap(original), schema);
        GenericRecord v = (GenericRecord) result.get("v");
        assertEquals("B", v.getSchema().getName());
        assertEquals("kept", v.get("detail").toString());
    }

    @Test
    void unionValueMatchingSeveralRecordBranchesIsRejected() {
        Schema schema = parse("{\"type\":\"record\",\"name\":\"U\",\"fields\":[{\"name\":\"v\",\"type\":["
                + "{\"type\":\"record\",\"name\":\"A\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]},"
                + "{\"type\":\"record\",\"name\":\"C\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}]}]}");
        AvroRuntimeException e = assertThrows(AvroRuntimeException.class,
                () -> AvroRuleRecordConverter.fromMap(Map.of("v", Map.of("id", "1")), schema));
        assertTrue(e.getMessage().contains("several union branches"), e.getMessage());
    }

    @Test
    void unionPrefersTheBranchWithoutPrecisionLoss() {
        Schema schema = parse("{\"type\":\"record\",\"name\":\"F\",\"fields\":"
                + "[{\"name\":\"x\",\"type\":[\"float\",\"double\"]}]}");
        assertEquals(0.1d, AvroRuleRecordConverter.fromMap(Map.of("x", 0.1d), schema).get("x"));
        assertEquals(0.5f, AvroRuleRecordConverter.fromMap(Map.of("x", 0.5d), schema).get("x"));
    }

    @Test
    void bytesRejectCharactersThatAreNotBytes() {
        Schema schema = parse("{\"type\":\"record\",\"name\":\"Bt\",\"fields\":"
                + "[{\"name\":\"b\",\"type\":\"bytes\"}]}");
        assertEquals(ByteBuffer.wrap(new byte[] {(byte) 0xE9}),
                AvroRuleRecordConverter.fromMap(Map.of("b", "\u00e9"), schema).get("b"));
        AvroRuntimeException e = assertThrows(AvroRuntimeException.class,
                () -> AvroRuleRecordConverter.fromMap(Map.of("b", "\u20ac"), schema));
        assertTrue(e.getMessage().contains("U+20AC"), e.getMessage());
    }
}
