package io.apicurio.registry.agents.signature;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The JSON Canonicalization Scheme (JCS) of RFC 8785: object properties sorted by their UTF-16 code
 * units, no whitespace, strings escaped and numbers formatted as ECMAScript's {@code JSON.stringify}
 * does, encoded in UTF-8.
 * <p>
 * Input that RFC 8785 does not accept is refused with a {@link CanonicalizationException}: duplicate
 * property names (when parsing text), lone surrogates, NaN and infinite numbers, and nesting deeper than
 * {@link #MAX_DEPTH}.
 */
public final class JsonCanonicalizer {

    /** Containers nested deeper than this are refused, so hostile input cannot exhaust the stack. */
    public static final int MAX_DEPTH = 64;

    private static final ObjectMapper STRICT_MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();

    private JsonCanonicalizer() {
    }

    /**
     * Parses JSON text, refusing duplicate property names, which different parsers resolve differently.
     */
    public static JsonNode parseStrict(String json) {
        try {
            JsonNode node = STRICT_MAPPER.readTree(json);
            if (node == null || node.isMissingNode()) {
                throw new CanonicalizationException("no JSON value");
            }
            return node;
        } catch (IOException e) {
            throw new CanonicalizationException("not valid JSON without duplicate property names");
        }
    }

    /**
     * @return the canonical form of the value, as UTF-8 bytes
     */
    public static byte[] canonicalize(JsonNode node) {
        return canonicalizeToString(node).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return the canonical form of the value, before UTF-8 encoding
     */
    public static String canonicalizeToString(JsonNode node) {
        StringBuilder out = new StringBuilder();
        write(node, out, 1);
        return out.toString();
    }

    private static void write(JsonNode node, StringBuilder out, int depth) {
        if (node.isContainerNode() && depth > MAX_DEPTH) {
            throw new CanonicalizationException("nesting deeper than " + MAX_DEPTH);
        }
        if (node.isObject()) {
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            fields.forEachRemaining(entries::add);
            // String.compareTo compares UTF-16 code units as unsigned values, as RFC 8785 requires.
            entries.sort(Map.Entry.comparingByKey());
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, JsonNode> entry : entries) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(entry.getKey(), out);
                out.append(':');
                write(entry.getValue(), out, depth + 1);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(node.get(i), out, depth + 1);
            }
            out.append(']');
        } else if (node.isTextual()) {
            writeString(node.textValue(), out);
        } else if (node.isNumber()) {
            out.append(formatNumber(node.doubleValue()));
        } else if (node.isBoolean()) {
            out.append(node.booleanValue() ? "true" : "false");
        } else if (node.isNull()) {
            out.append("null");
        } else {
            throw new CanonicalizationException("unsupported JSON value");
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new CanonicalizationException("lone surrogate in a string");
                }
                out.append(c).append(value.charAt(++i));
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                throw new CanonicalizationException("lone surrogate in a string");
            }
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    /**
     * Formats a number as ECMAScript's {@code Number.prototype.toString} does (ECMA-262, section
     * Number::toString, including "Note 2"): the shortest decimal that reads back as the same double,
     * the closest one when several have that length, and the even one on a tie.
     */
    public static String formatNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new CanonicalizationException("NaN and infinite numbers are not allowed");
        }
        if (value == 0) {
            return "0";
        }
        if (value < 0) {
            return "-" + formatNumber(-value);
        }
        BigDecimal shortest = shortestDecimal(value);
        String digits = shortest.unscaledValue().toString();
        // Strip trailing zeros from the digits: they belong to the exponent.
        int k = digits.length();
        while (k > 1 && digits.charAt(k - 1) == '0') {
            k--;
        }
        int trailingZeros = digits.length() - k;
        digits = digits.substring(0, k);
        // value = digits * 10^(n - k)
        int n = k - shortest.scale() + trailingZeros;

        StringBuilder out = new StringBuilder();
        if (k <= n && n <= 21) {
            out.append(digits);
            out.append("0".repeat(n - k));
        } else if (0 < n && n <= 21) {
            out.append(digits, 0, n).append('.').append(digits, n, k);
        } else if (-6 < n && n <= 0) {
            out.append("0.").append("0".repeat(-n)).append(digits);
        } else {
            int exponent = n - 1;
            out.append(digits.charAt(0));
            if (k > 1) {
                out.append('.').append(digits, 1, k);
            }
            out.append('e').append(exponent < 0 ? '-' : '+').append(Math.abs(exponent));
        }
        return out.toString();
    }

    private static BigDecimal shortestDecimal(double value) {
        BigDecimal exact = new BigDecimal(value);
        for (int precision = 1; precision <= 17; precision++) {
            BigDecimal best = null;
            // The even rounding first, so it wins a tie in distance.
            for (RoundingMode mode : new RoundingMode[] { RoundingMode.HALF_EVEN, RoundingMode.FLOOR,
                    RoundingMode.CEILING }) {
                BigDecimal candidate = exact.round(new MathContext(precision, mode));
                if (candidate.doubleValue() != value) {
                    continue;
                }
                if (best == null || candidate.subtract(exact).abs().compareTo(best.subtract(exact).abs()) < 0) {
                    best = candidate;
                }
            }
            if (best != null) {
                return best;
            }
        }
        // Seventeen significant digits always identify a double.
        throw new IllegalStateException("No decimal representation found for " + value);
    }

    /**
     * Input that RFC 8785 cannot canonicalize. The message describes the problem, never the content.
     */
    public static class CanonicalizationException extends RuntimeException {

        private static final long serialVersionUID = -2913436158254613097L;

        public CanonicalizationException(String message) {
            super(message);
        }
    }
}
