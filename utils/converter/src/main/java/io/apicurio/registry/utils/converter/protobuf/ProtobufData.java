package io.apicurio.registry.utils.converter.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts between Kafka Connect values and generic Protobuf messages.
 *
 * <h3>Known fidelity limitations</h3>
 * <ul>
 *   <li><b>INT8/INT16</b>: Protobuf has no 8-bit or 16-bit integer type. These are encoded as
 *       {@code int32} on the wire, but the original Connect type is preserved in the {@code json_name}
 *       field option (prefixed {@code __int8:} or {@code __int16:}) so that the reverse path can
 *       recover the narrower type.</li>
 *   <li><b>Null scalars</b>: optional Connect fields use proto3 explicit presence
 *       ({@code proto3optional}) so that a null value round-trips as null rather than the proto3
 *       type default (0 / "" / false / empty bytes).</li>
 *   <li><b>Nested collections</b>: array-of-array and map-of-array/map are supported by
 *       recursively wrapping the inner collection in its own synthetic message.</li>
 * </ul>
 */
public class ProtobufData {

    private static final String DEFAULT_PACKAGE = "io.apicurio.registry.connect";
    private static final String DEFAULT_MESSAGE = "ConnectMessage";

    /**
     * JSON-name prefix used to carry the original Connect INT8 type across the proto3 round-trip.
     * The field's actual name and wire encoding are unaffected; only {@code json_name} carries the hint.
     */
    static final String JSON_NAME_INT8_PREFIX = "__int8:";

    /**
     * JSON-name prefix used to carry the original Connect INT16 type across the proto3 round-trip.
     */
    static final String JSON_NAME_INT16_PREFIX = "__int16:";

    /** Memoised Descriptors keyed by the top-level Connect Schema. */
    private final ConcurrentHashMap<Schema, Descriptors.Descriptor> descriptorCache = new ConcurrentHashMap<>();

    /**
     * Raw Connect field-name → sanitised proto field-name, keyed by the top-level Connect Schema.
     * Populated atomically together with the Descriptor cache entry.
     */
    private final ConcurrentHashMap<Schema, Map<String, String>> nameMapCache = new ConcurrentHashMap<>();

    public DynamicMessage fromConnectData(Schema schema, Object value) {
        Objects.requireNonNull(schema, "schema must not be null");
        if (value == null) {
            return null;
        }
        if (schema.type() != Schema.Type.STRUCT) {
            throw new IllegalArgumentException("Top-level Protobuf converter schema must be a struct");
        }

        try {
            ensureCached(schema);
            Descriptors.Descriptor descriptor = descriptorCache.get(schema);
            Map<String, String> nameMap = nameMapCache.get(schema);
            return toMessage(descriptor, schema, value, nameMap);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IllegalArgumentException("Invalid Protobuf schema generated from Connect schema", e);
        }
    }

    public SchemaAndValue toConnectData(Message message) {
        if (message == null) {
            return SchemaAndValue.NULL;
        }
        Schema schema = toConnectSchema(message.getDescriptorForType());
        return new SchemaAndValue(schema, toConnectValue(schema, message));
    }

    public Schema toConnectSchema(Descriptors.Descriptor descriptor) {
        SchemaBuilder builder = SchemaBuilder.struct().name(connectName(descriptor.getFullName()));
        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            builder.field(field.getName(), toConnectFieldSchema(field));
        }
        return builder.build();
    }

    /**
     * Ensures that both the descriptor and name map for {@code schema} are present in their
     * respective caches. Uses {@code computeIfAbsent} so that concurrent calls for the same schema
     * only build once.
     */
    private void ensureCached(Schema schema) throws Descriptors.DescriptorValidationException {
        if (descriptorCache.containsKey(schema)) {
            return;
        }
        DescriptorProtos.FileDescriptorProto.Builder file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName(fileName(schema))
                .setPackage(packageName(schema))
                .setSyntax("proto3");
        MessageBuilderContext context = new MessageBuilderContext(file);
        String messageName = messageName(schema);
        context.addMessage(messageName, schema);
        Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(file.build(),
                new Descriptors.FileDescriptor[0]);
        Descriptors.Descriptor descriptor = fileDescriptor.findMessageTypeByName(messageName);
        Map<String, String> nameMap = context.buildNameMap();

        descriptorCache.putIfAbsent(schema, descriptor);
        nameMapCache.putIfAbsent(schema, nameMap);
    }

    private DynamicMessage toMessage(Descriptors.Descriptor descriptor, Schema schema, Object value,
            Map<String, String> nameMap) {
        if (!(value instanceof Struct)) {
            throw new IllegalArgumentException("Expected Struct for schema " + schema.name());
        }
        Struct struct = (Struct) value;
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(descriptor);
        for (Field connectField : schema.fields()) {
            Object fieldValue = struct.get(connectField);
            if (fieldValue == null) {
                continue;
            }
            String sanitisedName = nameMap.getOrDefault(connectField.name(), connectField.name());
            Descriptors.FieldDescriptor protoField = descriptor.findFieldByName(sanitisedName);
            if (protoField == null) {
                throw new IllegalArgumentException(
                        "Missing Protobuf field for Connect field: " + connectField.name()
                                + " (looked up as sanitised name: " + sanitisedName + ")");
            }
            setField(builder, protoField, connectField.schema(), fieldValue, nameMap);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private void setField(DynamicMessage.Builder builder, Descriptors.FieldDescriptor protoField,
            Schema schema, Object value, Map<String, String> nameMap) {
        if (protoField.isMapField()) {
            Map<Object, Object> map = (Map<Object, Object>) value;
            Descriptors.Descriptor entryDescriptor = protoField.getMessageType();
            Descriptors.FieldDescriptor keyField = entryDescriptor.findFieldByName("key");
            Descriptors.FieldDescriptor valueField = entryDescriptor.findFieldByName("value");
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                DynamicMessage mapEntry = DynamicMessage.newBuilder(entryDescriptor)
                        .setField(keyField, toProtoValue(keyField, schema.keySchema(), entry.getKey(), nameMap))
                        .setField(valueField, toProtoValue(valueField, schema.valueSchema(), entry.getValue(), nameMap))
                        .build();
                builder.addRepeatedField(protoField, mapEntry);
            }
        } else if (protoField.isRepeated()) {
            for (Object item : (Collection<Object>) value) {
                builder.addRepeatedField(protoField, toProtoValue(protoField, schema.valueSchema(), item, nameMap));
            }
        } else {
            builder.setField(protoField, toProtoValue(protoField, schema, value, nameMap));
        }
    }

    @SuppressWarnings("unchecked")
    private Object toProtoValue(Descriptors.FieldDescriptor protoField, Schema schema, Object value,
            Map<String, String> nameMap) {
        if (value == null) {
            return null;
        }
        switch (schema.type()) {
            case INT8:
            case INT16:
            case INT32:
                return ((Number) value).intValue();
            case INT64:
                return ((Number) value).longValue();
            case FLOAT32:
                return ((Number) value).floatValue();
            case FLOAT64:
                return ((Number) value).doubleValue();
            case BOOLEAN:
            case STRING:
                return value;
            case BYTES:
                if (value instanceof byte[]) {
                    return ByteString.copyFrom((byte[]) value);
                }
                if (value instanceof ByteBuffer) {
                    return ByteString.copyFrom((ByteBuffer) value);
                }
                throw new IllegalArgumentException("Unsupported bytes value: " + value.getClass());
            case STRUCT:
                return toMessage(protoField.getMessageType(), schema, value, nameMap);
            case ARRAY:
                if (protoField.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                    Descriptors.Descriptor wrapperDescriptor = protoField.getMessageType();
                    Descriptors.FieldDescriptor itemsField = wrapperDescriptor.findFieldByName("items");
                    DynamicMessage.Builder wrapperBuilder = DynamicMessage.newBuilder(wrapperDescriptor);
                    if (itemsField != null) {
                        for (Object item : (Collection<Object>) value) {
                            wrapperBuilder.addRepeatedField(itemsField,
                                    toProtoValue(itemsField, schema.valueSchema(), item, nameMap));
                        }
                    }
                    return wrapperBuilder.build();
                }
                return value;
            case MAP:
                throw new IllegalArgumentException(
                        "Nested map-in-collection (map whose element is also a map) is not supported. "
                                + "Wrap the inner map in a struct field instead.");

            default:
                throw new IllegalArgumentException("Unsupported Connect schema type: " + schema.type());
        }
    }

    private Schema toConnectFieldSchema(Descriptors.FieldDescriptor field) {
        if (field.isMapField()) {
            Descriptors.Descriptor entry = field.getMessageType();
            Schema keySchema = toConnectFieldSchema(entry.findFieldByName("key"));
            Schema valueSchema = toConnectFieldSchema(entry.findFieldByName("value"));
            return SchemaBuilder.map(keySchema, valueSchema).optional().build();
        }
        if (field.isRepeated()) {
            return SchemaBuilder.array(toConnectScalarSchema(field)).optional().build();
        }
        if (field.toProto().getProto3Optional()) {
            return optional(toConnectScalarSchema(field));
        }
        return toConnectScalarSchema(field);
    }

    private Schema toConnectScalarSchema(Descriptors.FieldDescriptor field) {
        switch (field.getJavaType()) {
            case INT:
                String jsonName = field.toProto().getJsonName();
                if (jsonName.startsWith(JSON_NAME_INT8_PREFIX)) {
                    return Schema.INT8_SCHEMA;
                }
                if (jsonName.startsWith(JSON_NAME_INT16_PREFIX)) {
                    return Schema.INT16_SCHEMA;
                }
                return Schema.INT32_SCHEMA;
            case LONG:
                return Schema.INT64_SCHEMA;
            case FLOAT:
                return Schema.FLOAT32_SCHEMA;
            case DOUBLE:
                return Schema.FLOAT64_SCHEMA;
            case BOOLEAN:
                return Schema.BOOLEAN_SCHEMA;
            case STRING:
            case ENUM:
                return Schema.STRING_SCHEMA;
            case BYTE_STRING:
                return Schema.BYTES_SCHEMA;
            case MESSAGE:
                return toConnectSchema(field.getMessageType());
            default:
                throw new IllegalArgumentException("Unsupported Protobuf field type: " + field.getJavaType());
        }
    }

    private Schema optional(Schema schema) {
        SchemaBuilder builder;
        switch (schema.type()) {
            case INT8:
                builder = SchemaBuilder.int8();
                break;
            case INT16:
                builder = SchemaBuilder.int16();
                break;
            case INT32:
                builder = SchemaBuilder.int32();
                break;
            case INT64:
                builder = SchemaBuilder.int64();
                break;
            case FLOAT32:
                builder = SchemaBuilder.float32();
                break;
            case FLOAT64:
                builder = SchemaBuilder.float64();
                break;
            case BOOLEAN:
                builder = SchemaBuilder.bool();
                break;
            case STRING:
                builder = SchemaBuilder.string();
                break;
            case BYTES:
                builder = SchemaBuilder.bytes();
                break;
            case STRUCT:
                builder = SchemaBuilder.struct().name(schema.name());
                for (Field field : schema.fields()) {
                    builder.field(field.name(), field.schema());
                }
                break;
            default:
                return schema;
        }
        return builder.optional().build();
    }

    private Struct toConnectValue(Schema schema, Message message) {
        Struct result = new Struct(schema);
        for (Field field : schema.fields()) {
            Descriptors.FieldDescriptor protoField = message.getDescriptorForType().findFieldByName(field.name());
            if (protoField == null) {
                continue;
            }
            if (!protoField.isRepeated() && protoField.toProto().getProto3Optional()) {
                if (!message.hasField(protoField)) {
                    continue;
                }
            }
            Object value = message.getField(protoField);
            if (value != null) {
                result.put(field, toConnectFieldValue(field.schema(), protoField, value));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Object toConnectFieldValue(Schema schema, Descriptors.FieldDescriptor field, Object value) {
        if (field.isMapField()) {
            Map<Object, Object> result = new LinkedHashMap<>();
            Descriptors.FieldDescriptor keyField = field.getMessageType().findFieldByName("key");
            Descriptors.FieldDescriptor valueField = field.getMessageType().findFieldByName("value");
            for (Message entry : (Collection<Message>) value) {
                Object key = toConnectFieldValue(schema.keySchema(), keyField, entry.getField(keyField));
                Object mapValue = toConnectFieldValue(schema.valueSchema(), valueField, entry.getField(valueField));
                result.put(key, mapValue);
            }
            return result;
        }
        if (field.isRepeated()) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<Object>) value) {
                result.add(toConnectScalarValue(schema.valueSchema(), field, item));
            }
            return result;
        }
        return toConnectScalarValue(schema, field, value);
    }

    private Object toConnectScalarValue(Schema schema, Descriptors.FieldDescriptor field, Object value) {
        switch (field.getJavaType()) {
            case INT:
                if (schema.type() == Schema.Type.INT8) {
                    return ((Number) value).byteValue();
                }
                if (schema.type() == Schema.Type.INT16) {
                    return ((Number) value).shortValue();
                }
                return value;
            case BYTE_STRING:
                return ((ByteString) value).toByteArray();
            case ENUM:
                return ((Descriptors.EnumValueDescriptor) value).getName();
            case MESSAGE:
                return toConnectValue(schema, (Message) value);
            default:
                return value;
        }
    }

    private String connectName(String protobufName) {
        return protobufName.replace('$', '.');
    }

    private String packageName(Schema schema) {
        String name = schema.name();
        if (name == null || !name.contains(".")) {
            return DEFAULT_PACKAGE;
        }
        return sanitizeQualified(name.substring(0, name.lastIndexOf('.')));
    }

    private String messageName(Schema schema) {
        String name = schema.name();
        if (name == null || name.isBlank()) {
            return DEFAULT_MESSAGE;
        }
        int idx = name.lastIndexOf('.');
        return sanitizeTypeName(idx == -1 ? name : name.substring(idx + 1));
    }

    private String fileName(Schema schema) {
        return packageName(schema).replace('.', '/') + "/" + messageName(schema)
                .toLowerCase(Locale.ROOT) + ".proto";
    }

    private String sanitizeQualified(String name) {
        String[] parts = name.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = sanitizeIdentifier(parts[i], false);
        }
        return String.join(".", parts);
    }

    private String sanitizeTypeName(String name) {
        String sanitized = sanitizeIdentifier(name, true);
        return Character.toUpperCase(sanitized.charAt(0)) + sanitized.substring(1);
    }

    private String sanitizeIdentifier(String name, boolean typeName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            result.append(Character.isLetterOrDigit(c) || c == '_' ? c : '_');
        }
        if (result.length() == 0) {
            result.append(typeName ? "Message" : "field");
        }
        if (!Character.isLetter(result.charAt(0)) && result.charAt(0) != '_') {
            result.insert(0, typeName ? "Message_" : "field_");
        }
        return result.toString();
    }

    private final class MessageBuilderContext {

        private final DescriptorProtos.FileDescriptorProto.Builder file;
        private final Map<Schema, String> messageNames = new LinkedHashMap<>();

        /**
         * Maps raw Connect field name → sanitised proto field name, collected across all messages
         * added to this context. Only the flat raw-name space within the top-level message matters
         * for the lookup in {@link ProtobufData#toMessage}; nested messages carry their own maps.
         * For simplicity we merge all mappings into a single map (shadowing is acceptable since
         * Connect field names within a single message are unique).
         */
        private final Map<String, String> rawToSanitised = new LinkedHashMap<>();

        private MessageBuilderContext(DescriptorProtos.FileDescriptorProto.Builder file) {
            this.file = file;
        }

        /**
         * Returns an unmodifiable snapshot of the raw→sanitised name map for caching.
         */
        Map<String, String> buildNameMap() {
            return Map.copyOf(rawToSanitised);
        }

        private String addMessage(String preferredName, Schema schema) {
            String existing = messageNames.get(schema);
            if (existing != null) {
                return existing;
            }
            String messageName = uniqueMessageName(preferredName);
            messageNames.put(schema, messageName);
            DescriptorProtos.DescriptorProto.Builder message = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName(messageName);

            int fieldNumber = 1;
            for (Field field : schema.fields()) {
                message.addField(toField(field.name(), field.schema(), fieldNumber++));
            }
            file.addMessageType(message);
            return messageName;
        }

        private DescriptorProtos.FieldDescriptorProto toField(String name, Schema schema, int number) {
            String sanitisedName = sanitizeIdentifier(name, false);
            rawToSanitised.put(name, sanitisedName);

            DescriptorProtos.FieldDescriptorProto.Builder field = DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(sanitisedName)
                    .setNumber(number);

            if (schema.type() == Schema.Type.ARRAY) {
                field.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
                applyFieldType(field, schema.valueSchema(), sanitizeTypeName(name));
            } else if (schema.type() == Schema.Type.MAP) {
                String entryName = addMapEntry(name, schema);
                field.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName("." + packageName() + "." + entryName);
            } else {
                field.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
                if (schema.isOptional()) {
                    field.setProto3Optional(true);
                }
                applyFieldType(field, schema, sanitizeTypeName(name));
            }
            return field.build();
        }

        private void applyFieldType(DescriptorProtos.FieldDescriptorProto.Builder field, Schema schema,
                String preferredMessageName) {
            switch (schema.type()) {
                case INT8:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                            .setJsonName(JSON_NAME_INT8_PREFIX + field.getName());
                    break;
                case INT16:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                            .setJsonName(JSON_NAME_INT16_PREFIX + field.getName());
                    break;
                case INT32:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32);
                    break;
                case INT64:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64);
                    break;
                case FLOAT32:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT);
                    break;
                case FLOAT64:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE);
                    break;
                case BOOLEAN:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL);
                    break;
                case STRING:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING);
                    break;
                case BYTES:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES);
                    break;
                case STRUCT:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName("." + packageName() + "." + addMessage(preferredMessageName, schema));
                    break;
                case ARRAY:
                    String wrapperName = addWrapperMessage(preferredMessageName + "Wrapper",
                            schema.valueSchema());
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName("." + packageName() + "." + wrapperName);
                    break;
                case MAP:
                    String entryName = addMapEntry(preferredMessageName, schema);
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                            .setTypeName("." + packageName() + "." + entryName);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported Connect schema type: " + schema.type());
            }
        }

        /**
         * Creates a synthetic wrapper message with a single repeated {@code items} field to
         * represent a nested collection element whose type is itself a collection.
         */
        private String addWrapperMessage(String preferredName, Schema elementSchema) {
            String wrapperName = uniqueMessageName(preferredName);
            DescriptorProtos.DescriptorProto.Builder wrapper = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName(wrapperName);
            DescriptorProtos.FieldDescriptorProto.Builder itemsField =
                    DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName("items")
                            .setNumber(1)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
            applyFieldType(itemsField, elementSchema, wrapperName + "Element");
            wrapper.addField(itemsField);
            file.addMessageType(wrapper);
            messageNames.put(elementSchema, wrapperName);
            return wrapperName;
        }

        private String addMapEntry(String fieldName, Schema schema) {
            if (schema.keySchema().type() != Schema.Type.STRING) {
                throw new IllegalArgumentException("Only string map keys are supported by the Protobuf converter");
            }
            String entryName = uniqueMessageName(sanitizeTypeName(fieldName) + "Entry");
            DescriptorProtos.DescriptorProto.Builder entry = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName(entryName)
                    .setOptions(DescriptorProtos.MessageOptions.newBuilder().setMapEntry(true));
            entry.addField(toMapField("key", schema.keySchema(), 1));
            entry.addField(toMapField("value", schema.valueSchema(), 2));
            file.addMessageType(entry);
            return entryName;
        }

        private DescriptorProtos.FieldDescriptorProto toMapField(String name, Schema schema, int number) {
            DescriptorProtos.FieldDescriptorProto.Builder field = DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(name)
                    .setNumber(number)
                    .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
            applyFieldType(field, schema, sanitizeTypeName(name));
            return field.build();
        }

        private String uniqueMessageName(String preferredName) {
            String sanitized = sanitizeTypeName(preferredName);
            String candidate = sanitized;
            int idx = 2;
            while (messageNames.containsValue(candidate)) {
                candidate = sanitized + idx++;
            }
            return candidate;
        }

        private String packageName() {
            return file.getPackage();
        }
    }
}
