package io.apicurio.registry.agents.signature;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RFC 8785 test vectors: the number serialization samples of Appendix B and the examples of sections
 * 3.2.2 to 3.2.4.
 */
class JsonCanonicalizerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "0000000000000000, 0",
            "8000000000000000, 0",
            "0000000000000001, 5e-324",
            "8000000000000001, -5e-324",
            "7fefffffffffffff, 1.7976931348623157e+308",
            "ffefffffffffffff, -1.7976931348623157e+308",
            "4340000000000000, 9007199254740992",
            "c340000000000000, -9007199254740992",
            "4430000000000000, 295147905179352830000",
            "44b52d02c7e14af5, 9.999999999999997e+22",
            "44b52d02c7e14af6, 1e+23",
            "44b52d02c7e14af7, 1.0000000000000001e+23",
            "444b1ae4d6e2ef4e, 999999999999999700000",
            "444b1ae4d6e2ef4f, 999999999999999900000",
            "444b1ae4d6e2ef50, 1e+21",
            "3eb0c6f7a0b5ed8c, 9.999999999999997e-7",
            "3eb0c6f7a0b5ed8d, 0.000001",
            "41b3de4355555553, 333333333.3333332",
            "41b3de4355555554, 333333333.33333325",
            "41b3de4355555555, 333333333.3333333",
            "41b3de4355555556, 333333333.3333334",
            "41b3de4355555557, 333333333.33333343",
            "becbf647612f3696, -0.0000033333333333333333",
            "43143ff3c1cb0959, 1424953923781206.2"
    })
    void appendixBNumberSamples(String ieee754Hex, String expected) {
        double value = Double.longBitsToDouble(Long.parseUnsignedLong(ieee754Hex, 16));
        assertEquals(expected, JsonCanonicalizer.formatNumber(value));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({ "7fffffffffffffff", "7ff0000000000000", "fff0000000000000" })
    void appendixBRefusesNaNAndInfinity(String ieee754Hex) {
        double value = Double.longBitsToDouble(Long.parseUnsignedLong(ieee754Hex, 16));
        assertThrows(JsonCanonicalizer.CanonicalizationException.class, () -> JsonCanonicalizer.formatNumber(value));
    }

    @Test
    void section323SampleCanonicalForm() {
        String input = "{\n  \"numbers\": [333333333.33333329, 1E30, 4.50,\n"
                + "              2e-3, 0.000000000000000000000000001],\n"
                + "  \"string\": \"\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\\"\\/\",\n"
                + "  \"literals\": [null, true, false]\n}";

        String canonical = JsonCanonicalizer.canonicalizeToString(JsonCanonicalizer.parseStrict(input));

        assertEquals("{\"literals\":[null,true,false],\"numbers\":[333333333.3333333,1e+30,4.5,0.002,1e-27],"
                + "\"string\":\"\u20ac$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"}", canonical);
    }

    @Test
    void section324Utf8Bytes() {
        String input = "{\"numbers\":[333333333.33333329,1E30,4.50,2e-3,0.000000000000000000000000001],"
                + "\"string\":\"\\u20ac$\\u000F\\u000aA'\\u0042\\u0022\\u005c\\\\\\\"\\/\","
                + "\"literals\":[null,true,false]}";
        byte[] expected = HexFormat.of().parseHex(("7b 22 6c 69 74 65 72 61 6c 73 22 3a 5b 6e 75 6c 6c 2c 74 72"
                + " 75 65 2c 66 61 6c 73 65 5d 2c 22 6e 75 6d 62 65 72 73 22 3a"
                + " 5b 33 33 33 33 33 33 33 33 33 2e 33 33 33 33 33 33 33 2c 31"
                + " 65 2b 33 30 2c 34 2e 35 2c 30 2e 30 30 32 2c 31 65 2d 32 37"
                + " 5d 2c 22 73 74 72 69 6e 67 22 3a 22 e2 82 ac 24 5c 75 30 30"
                + " 30 66 5c 6e 41 27 42 5c 22 5c 5c 5c 5c 5c 22 2f 22 7d").replace(" ", ""));

        assertArrayEquals(expected, JsonCanonicalizer.canonicalize(JsonCanonicalizer.parseStrict(input)));
    }

    @Test
    void section323PropertySortingByUtf16CodeUnits() {
        String input = "{\"\\u20ac\": \"Euro Sign\", \"\\r\": \"Carriage Return\","
                + " \"\\ufb33\": \"Hebrew Letter Dalet With Dagesh\", \"1\": \"One\","
                + " \"\\ud83d\\ude00\": \"Emoji: Grinning Face\", \"\\u0080\": \"Control\","
                + " \"\\u00f6\": \"Latin Small Letter O With Diaeresis\"}";

        JsonNode canonical = JsonCanonicalizer.parseStrict(
                JsonCanonicalizer.canonicalizeToString(JsonCanonicalizer.parseStrict(input)));
        List<String> order = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = canonical.fields();
        fields.forEachRemaining(field -> order.add(field.getValue().textValue()));

        assertEquals(List.of("Carriage Return", "One", "Control", "Latin Small Letter O With Diaeresis",
                "Euro Sign", "Emoji: Grinning Face", "Hebrew Letter Dalet With Dagesh"), order);
    }

    @Test
    void escapesControlCharactersInLowercaseHex() {
        String canonical = JsonCanonicalizer.canonicalizeToString(
                JsonCanonicalizer.parseStrict("{\"k\":\"\\u0001\\u001F\\b\\t\\f\\r\\u007f\"}"));

        assertEquals("{\"k\":\"\\u0001\\u001f\\b\\t\\f\\r\u007f\"}", canonical);
    }

    @Test
    void refusesLoneSurrogates() {
        JsonNode node = JsonCanonicalizer.parseStrict("{\"k\":\"\\udead\"}");

        assertThrows(JsonCanonicalizer.CanonicalizationException.class, () -> JsonCanonicalizer.canonicalize(node));
    }

    @Test
    void refusesDuplicatePropertyNames() {
        assertThrows(JsonCanonicalizer.CanonicalizationException.class,
                () -> JsonCanonicalizer.parseStrict("{\"name\":\"a\",\"name\":\"b\"}"));
    }

    @Test
    void refusesExcessiveNesting() {
        String deep = "[".repeat(JsonCanonicalizer.MAX_DEPTH + 1) + "]".repeat(JsonCanonicalizer.MAX_DEPTH + 1);
        JsonNode node = JsonCanonicalizer.parseStrict(deep);

        assertThrows(JsonCanonicalizer.CanonicalizationException.class, () -> JsonCanonicalizer.canonicalize(node));
    }

    @Test
    void largeIntegersAreSerializedAsDoubles() {
        String canonical = JsonCanonicalizer.canonicalizeToString(
                JsonCanonicalizer.parseStrict("[9007199254740993, 295147905179352825856, -0, -0.0, 1.5e300]"));

        assertEquals("[9007199254740992,295147905179352830000,0,0,1.5e+300]", canonical);
    }

    @Test
    void canonicalFormIsUtf8Encoded() {
        byte[] canonical = JsonCanonicalizer.canonicalize(JsonCanonicalizer.parseStrict("{\"k\":\"\u00e9\"}"));

        assertArrayEquals("{\"k\":\"\u00e9\"}".getBytes(StandardCharsets.UTF_8), canonical);
    }
}
