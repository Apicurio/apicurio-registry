package io.apicurio.registry.serde.avro;

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericEnumSymbol;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.util.Utf8;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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
        Object record = fromPlain(map, schema, "");
        if (!GenericData.get().validate(schema, record)) {
            throw new AvroRuntimeException("record does not conform to schema " + schema.getFullName());
        }
        return (GenericRecord) record;
    }

    private static Object fromPlain(Object value, Schema schema, String path) {
        switch (schema.getType()) {
            case RECORD:
                return toRecord(value, schema, path);
            case UNION:
                return toUnion(value, schema, path);
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
                    array.add(fromPlain(item, schema.getElementType(), path + "[" + i++ + "]"));
                }
                return array;
            case MAP:
                if (!(value instanceof Map)) {
                    throw mismatch(path, schema, value);
                }
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    map.put(key, fromPlain(entry.getValue(), schema.getValueType(), path + "." + key));
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
                if (!isIntegral(value) || ((Number) value).longValue() != ((Number) value).intValue()) {
                    throw mismatch(path, schema, value);
                }
                return ((Number) value).intValue();
            case LONG:
                if (!isIntegral(value)) {
                    throw mismatch(path, schema, value);
                }
                return ((Number) value).longValue();
            case FLOAT:
                if (!(value instanceof Number)) {
                    throw mismatch(path, schema, value);
                }
                return ((Number) value).floatValue();
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

    private static GenericRecord toRecord(Object value, Schema schema, String path) {
        if (!(value instanceof Map)) {
            throw mismatch(path, schema, value);
        }
        Map<?, ?> map = (Map<?, ?>) value;
        GenericData.Record record = new GenericData.Record(schema);
        for (Schema.Field field : schema.getFields()) {
            String fieldPath = path.isEmpty() ? field.name() : path + "." + field.name();
            if (map.containsKey(field.name())) {
                record.put(field.pos(), fromPlain(map.get(field.name()), field.schema(), fieldPath));
            } else if (field.hasDefaultValue()) {
                record.put(field.pos(), GenericData.get().deepCopy(field.schema(),
                        GenericData.get().getDefaultValue(field)));
            } else {
                throw new AvroRuntimeException("missing required field '" + fieldPath + "'");
            }
        }
        return record;
    }

    private static Object toUnion(Object value, Schema schema, String path) {
        AvroRuntimeException last = null;
        for (Schema branch : schema.getTypes()) {
            try {
                return fromPlain(value, branch, path);
            } catch (AvroRuntimeException e) {
                last = e;
            }
        }
        throw last != null ? last : mismatch(path, schema, value);
    }

    private static boolean isIntegral(Object value) {
        if (!(value instanceof Number)) {
            return false;
        }
        double d = ((Number) value).doubleValue();
        return d == Math.rint(d) && !Double.isInfinite(d);
    }

    private static byte[] toBytes(Object value, Schema schema, String path) {
        if (value instanceof CharSequence) {
            return value.toString().getBytes(StandardCharsets.ISO_8859_1);
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
