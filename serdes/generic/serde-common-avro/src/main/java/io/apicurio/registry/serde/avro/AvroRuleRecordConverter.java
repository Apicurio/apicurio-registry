package io.apicurio.registry.serde.avro;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericEnumSymbol;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts Avro generic data to and from the plain map the contract rule engine works on.
 * <p>
 * Records and maps become {@link Map}s, arrays become {@link List}s, enums and strings become
 * {@link String}s ({@link Utf8} when converted back), unions are unwrapped to their value, and bytes and fixed become ISO-8859-1 strings
 * (the representation of Avro's JSON encoding). Logical types keep their underlying representation
 * (a date is an int, a decimal is its bytes), as read by the generic reader.
 */
final class AvroRuleRecordConverter {

    private AvroRuleRecordConverter() {
    }

    static Map<String, Object> toMap(GenericRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Schema.Field field : record.getSchema().getFields()) {
            map.put(field.name(), toPlain(record.get(field.pos())));
        }
        return map;
    }

    private static Object toPlain(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof GenericRecord) {
            return toMap((GenericRecord) value);
        }
        if (value instanceof GenericEnumSymbol || value instanceof CharSequence) {
            return value.toString();
        }
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = ((ByteBuffer) value).duplicate();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
        if (value instanceof GenericFixed) {
            return new String(((GenericFixed) value).bytes(), StandardCharsets.ISO_8859_1);
        }
        if (value instanceof Collection) {
            List<Object> list = new ArrayList<>();
            for (Object item : (Collection<?>) value) {
                list.add(toPlain(item));
            }
            return list;
        }
        if (value instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                map.put(entry.getKey().toString(), toPlain(entry.getValue()));
            }
            return map;
        }
        return value;
    }

    /**
     * Builds a record of {@code schema} from a rule record. Fields missing from the map take their
     * schema default; entries without a field in the schema are dropped.
     *
     * @throws AvroRuntimeException if the map does not conform to the schema
     */
    static GenericRecord fromMap(Map<String, Object> map, Schema schema) {
        Object record = fromPlain(map, schema, "", false);
        if (!GenericData.get().validate(schema, record)) {
            throw new AvroRuntimeException("record does not conform to schema " + schema.getFullName());
        }
        return (GenericRecord) record;
    }

    /**
     * @param strict whether the value must be represented exactly: no map entry without a field, no
     *            precision lost to a float (used to pick a union branch)
     */
    private static Object fromPlain(Object value, Schema schema, String path, boolean strict) {
        switch (schema.getType()) {
            case RECORD:
                return toRecord(value, schema, path, strict);
            case UNION:
                return toUnion(value, schema, path, strict);
            case NULL:
                if (value != null) {
                    throw mismatch(path, schema, value);
                }
                return null;
            case ARRAY:
                if (!(value instanceof Collection)) {
                    throw mismatch(path, schema, value);
                }
                GenericData.Array<Object> array = new GenericData.Array<>(((Collection<?>) value).size(), schema);
                int i = 0;
                for (Object item : (Collection<?>) value) {
                    array.add(fromPlain(item, schema.getElementType(), path + "[" + i++ + "]", strict));
                }
                return array;
            case MAP:
                if (!(value instanceof Map)) {
                    throw mismatch(path, schema, value);
                }
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    map.put(key, fromPlain(entry.getValue(), schema.getValueType(), path + "." + key, strict));
                }
                return map;
            case ENUM:
                if (!(value instanceof CharSequence) || !schema.hasEnumSymbol(value.toString())) {
                    throw mismatch(path, schema, value);
                }
                return new GenericData.EnumSymbol(schema, value.toString());
            case STRING:
                if (!(value instanceof CharSequence)) {
                    throw mismatch(path, schema, value);
                }
                // As the generic reader returns strings
                return new Utf8(value.toString());
            case BYTES:
                return ByteBuffer.wrap(toBytes(value, schema, path));
            case FIXED:
                byte[] bytes = toBytes(value, schema, path);
                if (bytes.length != schema.getFixedSize()) {
                    throw mismatch(path, schema, value);
                }
                return new GenericData.Fixed(schema, bytes);
            case INT:
            case LONG:
                try {
                    BigDecimal integer = exactInteger(value, schema, path);
                    return schema.getType() == Schema.Type.INT ? (Object) integer.intValueExact()
                            : (Object) integer.longValueExact();
                } catch (ArithmeticException e) {
                    throw mismatch(path, schema, value);
                }
            case FLOAT:
                if (!(value instanceof Number)) {
                    throw mismatch(path, schema, value);
                }
                float f = ((Number) value).floatValue();
                if (strict && (double) f != ((Number) value).doubleValue()) {
                    throw mismatch(path, schema, value);
                }
                return f;
            case DOUBLE:
                if (!(value instanceof Number)) {
                    throw mismatch(path, schema, value);
                }
                return ((Number) value).doubleValue();
            case BOOLEAN:
                if (!(value instanceof Boolean)) {
                    throw mismatch(path, schema, value);
                }
                return value;
            default:
                throw mismatch(path, schema, value);
        }
    }

    private static GenericRecord toRecord(Object value, Schema schema, String path, boolean strict) {
        if (!(value instanceof Map)) {
            throw mismatch(path, schema, value);
        }
        Map<?, ?> map = (Map<?, ?>) value;
        if (strict) {
            for (Object key : map.keySet()) {
                if (schema.getField(String.valueOf(key)) == null) {
                    throw new AvroRuntimeException("field '" + (path.isEmpty() ? "<root>" : path) + "': '" + key
                            + "' is not in " + schema.getFullName());
                }
            }
        }
        GenericData.Record record = new GenericData.Record(schema);
        for (Schema.Field field : schema.getFields()) {
            String fieldPath = path.isEmpty() ? field.name() : path + "." + field.name();
            if (map.containsKey(field.name())) {
                record.put(field.pos(), fromPlain(map.get(field.name()), field.schema(), fieldPath, strict));
            } else if (field.hasDefaultValue()) {
                record.put(field.pos(), GenericData.get().deepCopy(field.schema(),
                        GenericData.get().getDefaultValue(field)));
            } else {
                throw new AvroRuntimeException("missing required field '" + fieldPath + "'");
            }
        }
        return record;
    }

    /**
     * The union branch that represents the value: the first that does so exactly, unless several record
     * branches do (ambiguous); otherwise the only one that accepts it with a loss (dropped entries, float
     * precision). The branch is not known from the map, so a value that fits several branches equally is
     * rejected rather than silently converted to one of them.
     */
    private static Object toUnion(Object value, Schema schema, String path, boolean strict) {
        AvroRuntimeException last = null;
        List<Object> exact = new ArrayList<>();
        List<Schema> exactBranches = new ArrayList<>();
        for (Schema branch : schema.getTypes()) {
            try {
                exact.add(fromPlain(value, branch, path, true));
                exactBranches.add(branch);
            } catch (AvroRuntimeException | ArithmeticException e) {
                last = asAvro(e, path, schema, value);
            }
        }
        if (exactBranches.stream().filter(b -> b.getType() == Schema.Type.RECORD).count() > 1) {
            throw ambiguous(path, exactBranches, value);
        }
        if (!exact.isEmpty()) {
            return exact.get(0);
        }
        if (strict) {
            throw last != null ? last : mismatch(path, schema, value);
        }
        List<Object> lossy = new ArrayList<>();
        List<Schema> lossyBranches = new ArrayList<>();
        for (Schema branch : schema.getTypes()) {
            try {
                lossy.add(fromPlain(value, branch, path, false));
                lossyBranches.add(branch);
            } catch (AvroRuntimeException | ArithmeticException e) {
                last = asAvro(e, path, schema, value);
            }
        }
        if (lossy.size() == 1) {
            return lossy.get(0);
        }
        if (lossy.size() > 1) {
            throw ambiguous(path, lossyBranches, value);
        }
        throw last != null ? last : mismatch(path, schema, value);
    }

    private static AvroRuntimeException ambiguous(String path, List<Schema> branches, Object value) {
        return new AvroRuntimeException("field '" + (path.isEmpty() ? "<root>" : path) + "': the value matches "
                + "several union branches (" + branches.stream().map(Schema::getFullName).toList()
                + "), so its branch cannot be determined");
    }

    private static AvroRuntimeException asAvro(RuntimeException e, String path, Schema schema, Object value) {
        return e instanceof AvroRuntimeException avro ? avro : mismatch(path, schema, value);
    }

    /**
     * The value as an exact integer: a fractional value, or one too large for the Avro type (checked by
     * the caller's intValueExact or longValueExact), is rejected rather than truncated or saturated.
     */
    private static BigDecimal exactInteger(Object value, Schema schema, String path) {
        if (!(value instanceof Number)) {
            throw mismatch(path, schema, value);
        }
        BigDecimal decimal;
        if (value instanceof BigDecimal big) {
            decimal = big;
        } else if (value instanceof BigInteger big) {
            decimal = new BigDecimal(big);
        } else if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d != Math.rint(d)) {
                throw mismatch(path, schema, value);
            }
            // An integral double within the long range converts to long exactly; anything outside it is
            // rejected by the caller's range check, so the shortest-repr BigDecimal is precise enough there.
            decimal = Math.abs(d) < 0x1p63 ? BigDecimal.valueOf((long) d) : BigDecimal.valueOf(d);
        } else {
            decimal = BigDecimal.valueOf(((Number) value).longValue());
        }
        if (decimal.signum() != 0 && decimal.stripTrailingZeros().scale() > 0) {
            throw mismatch(path, schema, value);
        }
        return decimal;
    }

    private static byte[] toBytes(Object value, Schema schema, String path) {
        if (value instanceof CharSequence) {
            // Avro's JSON encoding of bytes: one character per byte, so nothing above U+00FF
            String text = value.toString();
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) > 0xFF) {
                    throw new AvroRuntimeException("field '" + (path.isEmpty() ? "<root>" : path)
                            + "': character U+" + String.format(Locale.ROOT, "%04X", (int) text.charAt(i))
                            + " cannot be a byte");
                }
            }
            return text.getBytes(StandardCharsets.ISO_8859_1);
        }
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = ((ByteBuffer) value).duplicate();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        throw mismatch(path, schema, value);
    }

    private static AvroRuntimeException mismatch(String path, Schema schema, Object value) {
        String type = value == null ? "null" : value.getClass().getSimpleName();
        return new AvroRuntimeException("field '" + (path.isEmpty() ? "<root>" : path) + "': " + type
                + " does not match Avro type " + schema.getType().getName());
    }
}
