package io.apicurio.registry.content.normalize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.normalization.ContentNormalizer;
import org.apache.avro.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AvroContentNormalizer implements ContentNormalizer {

    private static final ObjectMapper jsonMapperWithOrderedProps =
            JsonMapper.builder()
                    .nodeFactory(new SortingNodeFactory(false))
                    .build();

    static class SortingNodeFactory extends JsonNodeFactory {
        public SortingNodeFactory(boolean bigDecimalExact) {
            super(bigDecimalExact);
        }

        @Override
        public ObjectNode objectNode() {
            return new ObjectNode(this, new TreeMap<>());
        }
    }

    @Override
    public ContentHandle normalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        return ContentHandle.create(toNormalizedString(content, resolvedReferences));
    }

    protected static String toNormalizedString(ContentHandle schema, Map<String, ContentHandle> resolvedReferences) {
        try {
            Map<String, String> env = new HashMap<>();
            Schema.Parser parser = new Schema.Parser();
            for (ContentHandle resolvedRef : resolvedReferences.values()) {
                Schema schemaRef = parser.parse(resolvedRef.content());
                String fullName = schemaRef.getFullName();
                env.put(fullName, "\"" + fullName + "\"");
            }
            return build(env, parser.parse(schema.content()), new StringBuilder()).toString();
        } catch (IOException e) {
            // Shouldn't happen, b/c StringBuilder can't throw IOException
            throw new RuntimeException(e);
        }
    }

    private static Appendable build(Map<String, String> env, Schema s, Appendable o)
            throws IOException {
        boolean firstTime = true;
        Schema.Type st = s.getType();
        LogicalType lt = s.getLogicalType();
        switch (st) {
            case UNION:
                o.append('[');
                for (Schema b : s.getTypes()) {
                    if (!firstTime) {
                        o.append(',');
                    } else {
                        firstTime = false;
                    }
                    build(env, b, o);
                }
                return o.append(']');

            case ARRAY:
            case MAP:
                o.append("{\"type\":\"").append(st.getName()).append("\"");
                if (st == Schema.Type.ARRAY) {
                    build(env, s.getElementType(), o.append(",\"items\":"));
                } else {
                    build(env, s.getValueType(), o.append(",\"values\":"));
                }
                setSimpleProps(o, s.getObjectProps());
                return o.append("}");

            case ENUM:
            case FIXED:
            case RECORD:
                String name = s.getFullName();
                if (env.get(name) != null) {
                    return o.append(env.get(name));
                }
                String qname = "\"" + name + "\"";
                env.put(name, qname);
                o.append("{\"name\":").append(qname);
                o.append(",\"type\":\"").append(st.getName()).append("\"");
                if (st == Schema.Type.ENUM) {
                    o.append(",\"symbols\":[");
                    for (String enumSymbol : s.getEnumSymbols()) {
                        if (!firstTime) {
                            o.append(',');
                        } else {
                            firstTime = false;
                        }
                        o.append('"').append(enumSymbol).append('"');
                    }
                    o.append("]");
                } else if (st == Schema.Type.FIXED) {
                    o.append(",\"size\":").append(Integer.toString(s.getFixedSize()));
                    lt = s.getLogicalType();
                    // adding the logical property
                    if (lt != null) {
                        setLogicalProps(o, lt);
                    }
                } else { // st == Schema.Type.RECORD
                    o.append(",\"fields\":[");
                    for (Schema.Field f : s.getFields()) {
                        if (!firstTime) {
                            o.append(',');
                        } else {
                            firstTime = false;
                        }
                        o.append("{\"name\":\"").append(f.name()).append("\"");
                        build(env, f.schema(), o.append(",\"type\":"));
                        setFieldProps(o, f);
                        o.append("}");
                    }
                    o.append("]");
                }
                setComplexProps(o, s);
                setSimpleProps(o, s.getObjectProps());
                return o.append("}");

            default: // boolean, bytes, double, float, int, long, null, string
                if (lt != null) {
                    return writeLogicalType(s, lt, o);
                } else {
                    if (s.hasProps()) {
                        o.append("{\"type\":\"").append(st.getName()).append('"');
                        setSimpleProps(o, s.getObjectProps());
                        o.append("}");
                    } else {
                        o.append('"').append(st.getName()).append('"');
                    }
                    return o;
                }
        }
    }

    private static Appendable writeLogicalType(Schema s, LogicalType lt, Appendable o)
            throws IOException {
        o.append("{\"type\":\"").append(s.getType().getName()).append("\"");
        // adding the logical property
        setLogicalProps(o, lt);
        // adding the reserved property
        setSimpleProps(o, s.getObjectProps());
        return o.append("}");
    }

    private static void setLogicalProps(Appendable o, LogicalType lt) throws IOException {
        o.append(",\"").append(LogicalType.LOGICAL_TYPE_PROP)
                .append("\":\"").append(lt.getName()).append("\"");
        if (lt.getName().equals("decimal")) {
            LogicalTypes.Decimal dlt = (LogicalTypes.Decimal) lt;
            o.append(",\"precision\":").append(Integer.toString(dlt.getPrecision()));
            if (dlt.getScale() != 0) {
                o.append(",\"scale\":").append(Integer.toString(dlt.getScale()));
            }
        }
    }

    private static void setSimpleProps(Appendable o, Map<String, Object> schemaProps)
            throws IOException {
        Map<String, Object> sortedProps = new TreeMap<>(schemaProps);
        for (Map.Entry<String, Object> entry : sortedProps.entrySet()) {
            String propKey = entry.getKey();
            String propValue = toJsonNode(entry.getValue()).toString();
            o.append(",\"").append(propKey).append("\":").append(propValue);
        }
    }

    private static void setComplexProps(Appendable o, Schema s) throws IOException {
        if (s.getDoc() != null && !s.getDoc().isEmpty()) {
            o.append(",\"doc\":").append(toJsonNode(s.getDoc()).toString());
        }
        Set<String> aliases = s.getAliases();
        if (!aliases.isEmpty()) {
            o.append(",\"aliases\":").append(toJsonNode(new TreeSet<>(aliases)).toString());
        }
        if (s.getType() == Schema.Type.ENUM && s.getEnumDefault() != null) {
            o.append(",\"default\":").append(toJsonNode(s.getEnumDefault()).toString());
        }
    }

    private static void setFieldProps(Appendable o, Schema.Field f) throws IOException {
        if (f.order() != null) {
            o.append(",\"order\":\"").append(f.order().toString()).append("\"");
        }
        if (f.doc() != null) {
            o.append(",\"doc\":").append(toJsonNode(f.doc()).toString());
        }
        Set<String> aliases = f.aliases();
        if (!aliases.isEmpty()) {
            o.append(",\"aliases\":").append(toJsonNode(new TreeSet<>(aliases)).toString());
        }
        if (f.defaultVal() != null) {
            o.append(",\"default\":").append(toJsonNode(f.defaultVal()).toString());
        }
        setSimpleProps(o, f.getObjectProps());
    }

    static JsonNode toJsonNode(Object datum) {
        if (datum == null) {
            return null;
        }
        try {
            TokenBuffer generator = new TokenBuffer(jsonMapperWithOrderedProps, false);
            genJson(datum, generator);
            return jsonMapperWithOrderedProps.readTree(generator.asParser());
        } catch (IOException e) {
            throw new AvroRuntimeException(e);
        }
    }

    @SuppressWarnings(value = "unchecked")
    static void genJson(Object datum, JsonGenerator generator) throws IOException {
        if (datum == JsonProperties.NULL_VALUE) { // null
            generator.writeNull();
        } else if (datum instanceof Map) { // record, map
            generator.writeStartObject();
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) datum).entrySet()) {
                generator.writeFieldName(entry.getKey().toString());
                genJson(entry.getValue(), generator);
            }
            generator.writeEndObject();
        } else if (datum instanceof Collection) { // array
            generator.writeStartArray();
            for (Object element : (Collection<?>) datum) {
                genJson(element, generator);
            }
            generator.writeEndArray();
        } else if (datum instanceof byte[]) { // bytes, fixed
            generator.writeString(new String((byte[]) datum, StandardCharsets.ISO_8859_1));
        } else if (datum instanceof CharSequence || datum instanceof Enum<?>) { // string, enum
            generator.writeString(datum.toString());
        } else if (datum instanceof Double) { // double
            generator.writeNumber((Double) datum);
        } else if (datum instanceof Float) { // float
            generator.writeNumber((Float) datum);
        } else if (datum instanceof Long) { // long
            generator.writeNumber((Long) datum);
        } else if (datum instanceof Integer) { // int
            generator.writeNumber((Integer) datum);
        } else if (datum instanceof Boolean) { // boolean
            generator.writeBoolean((Boolean) datum);
        } else if (datum instanceof BigInteger) {
            generator.writeNumber((BigInteger) datum);
        } else if (datum instanceof BigDecimal) {
            generator.writeNumber((BigDecimal) datum);
        } else {
            throw new AvroRuntimeException("Unknown datum class: " + datum.getClass());
        }
    }
}
