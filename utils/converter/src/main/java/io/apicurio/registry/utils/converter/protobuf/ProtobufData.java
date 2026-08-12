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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts between Kafka Connect values and generic Protobuf messages.
 *
 * <h3>Fidelity notes</h3>
 * <ul>
 *   <li><b>INT8/INT16</b>: encoded as {@code int32} on the wire; the original Connect type is
 *       preserved in the {@code json_name} field option (prefixed {@code __int8:} or
 *       {@code __int16:}) and recovered on decode.</li>
 *   <li><b>Null scalars</b>: optional Connect fields use proto3 explicit presence
 *       ({@code proto3optional}) so a null value round-trips as null rather than the proto3
 *       type default (0 / "" / false / empty bytes).</li>
 *   <li><b>Special-character field names</b>: field names that require sanitisation (e.g.
 *       {@code user-id}, {@code first.name}) are stored in the {@code json_name} field option
 *       using the prefix {@code __raw:} and recovered on decode, so the original Connect field
 *       name is preserved across the round-trip.</li>
 *   <li><b>Nested collections</b>: array-of-array and map-of-array are supported by wrapping the
 *       inner collection in a synthetic {@code Wrapper{repeated items}} message. The wrapper is
 *       detected on decode by its single {@code items} repeated field and transparently unwrapped
 *       back to the original collection type, so the round-trip is lossless.</li>
 * </ul>
 */
public class ProtobufData {

    private static final String DEFAULT_PACKAGE = "io.apicurio.registry.connect";
    private static final String DEFAULT_MESSAGE = "ConnectMessage";

    /**
     * json_name prefix that carries the Connect INT8 type hint across the round-trip.
     * The field's wire encoding (int32) and proto field name are unaffected.
     */
    static final String JSON_NAME_INT8_PREFIX = "__int8:";

    /** json_name prefix that carries the Connect INT16 type hint across the round-trip. */
    static final String JSON_NAME_INT16_PREFIX = "__int16:";

    /**
     * json_name prefix that carries the original (pre-sanitisation) Connect field name.
     * Applied whenever the raw Connect name differs from the sanitised proto field name.
     * May be combined with a type prefix, e.g. {@code __int8:__raw:count-val}.
     */
    static final String JSON_NAME_RAW_PREFIX = "__raw:";

    /**
     * Name of the single repeated field inside a synthetic wrapper message.
     * A descriptor whose sole field has this name and is repeated is recognised as a wrapper
     * on the decode side and transparently unwrapped.
     */
    private static final String WRAPPER_ITEMS_FIELD = "items";

    /** Error message for map-in-collection schemas, which this converter does not support. */
    private static final String NESTED_MAP_UNSUPPORTED =
            "Nested map-in-collection is not supported. Wrap the inner map in a struct field.";

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

    /**
     * Rebuilds a Connect struct schema from a Protobuf descriptor.
     * Field names are recovered from the {@code json_name} option when present (see
     * {@link #extractConnectFieldName}).
     */
    public Schema toConnectSchema(Descriptors.Descriptor descriptor) {
        SchemaBuilder builder = SchemaBuilder.struct().name(connectName(descriptor.getFullName()));
        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            String connectFieldName = extractConnectFieldName(field);
            builder.field(connectFieldName, toConnectFieldSchema(field));
        }
        return builder.build();
    }

    /**
     * Ensures both the descriptor and the raw→sanitised name map for {@code schema} are in their
     * caches. Uses {@code computeIfAbsent} so concurrent calls for the same schema key only build
     * the descriptor once; a second thread that races past the containsKey check will find the
     * entry already present and return immediately.
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
            if (schema.type() != Schema.Type.MAP) {
                throw new IllegalArgumentException(NESTED_MAP_UNSUPPORTED);
            }
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
                    Descriptors.FieldDescriptor itemsField = wrapperDescriptor.findFieldByName(WRAPPER_ITEMS_FIELD);
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
                throw new IllegalArgumentException(NESTED_MAP_UNSUPPORTED);
            default:
                throw new IllegalArgumentException("Unsupported Connect schema type: " + schema.type());
        }
    }

    /**
     * Extracts the original Connect field name from a proto field descriptor.
     * If the {@code json_name} carries a {@code __raw:} suffix (optionally preceded by a type
     * prefix), the raw Connect name is returned; otherwise the sanitised proto field name is used.
     */
    private String extractConnectFieldName(Descriptors.FieldDescriptor protoField) {
        String jsonName = protoField.toProto().getJsonName();
        if (jsonName.startsWith(JSON_NAME_INT8_PREFIX)) {
            jsonName = jsonName.substring(JSON_NAME_INT8_PREFIX.length());
        } else if (jsonName.startsWith(JSON_NAME_INT16_PREFIX)) {
            jsonName = jsonName.substring(JSON_NAME_INT16_PREFIX.length());
        }
        if (jsonName.startsWith(JSON_NAME_RAW_PREFIX)) {
            return jsonName.substring(JSON_NAME_RAW_PREFIX.length());
        }
        return protoField.getName();
    }

    /**
     * Returns true if {@code descriptor} is a synthetic wrapper message generated by
     * {@link MessageBuilderContext#addWrapperMessage} — i.e. it has exactly one field whose name
     * is {@value #WRAPPER_ITEMS_FIELD} and which is repeated.
     * Used to transparently unwrap nested collections on the decode side.
     */
    private boolean isWrapperMessage(Descriptors.Descriptor descriptor) {
        List<Descriptors.FieldDescriptor> fields = descriptor.getFields();
        if (fields.size() != 1) {
            return false;
        }
        Descriptors.FieldDescriptor sole = fields.get(0);
        return WRAPPER_ITEMS_FIELD.equals(sole.getName()) && sole.isRepeated();
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
                if (isWrapperMessage(field.getMessageType())) {
                    Descriptors.FieldDescriptor itemsField =
                            field.getMessageType().findFieldByName(WRAPPER_ITEMS_FIELD);
                    return toConnectFieldSchema(itemsField);
                }
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
        Map<String, Descriptors.FieldDescriptor> connectNameToProto = new LinkedHashMap<>();
        for (Descriptors.FieldDescriptor protoField : message.getDescriptorForType().getFields()) {
            connectNameToProto.put(extractConnectFieldName(protoField), protoField);
        }
        for (Field field : schema.fields()) {
            Descriptors.FieldDescriptor protoField = connectNameToProto.get(field.name());
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
            List<Object> resultList = new ArrayList<>();
            for (Object item : (Collection<Object>) value) {
                resultList.add(toConnectScalarValue(schema.valueSchema(), field, item));
            }
            return resultList;
        }
        return toConnectScalarValue(schema, field, value);
    }

    @SuppressWarnings("unchecked")
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
                if (isWrapperMessage(field.getMessageType())) {
                    Message wrapperMsg = (Message) value;
                    Descriptors.FieldDescriptor itemsField =
                            field.getMessageType().findFieldByName(WRAPPER_ITEMS_FIELD);
                    List<Object> items = new ArrayList<>();
                    for (Object item : (Collection<Object>) wrapperMsg.getField(itemsField)) {
                        items.add(toConnectScalarValue(schema.valueSchema(), itemsField, item));
                    }
                    return items;
                }
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

        /**
         * Maps Connect Schema → proto message name, used to dedup struct messages so that two
         * Connect fields sharing the same schema object produce a single proto message type.
         * Wrapper message names are tracked separately in {@link #wrapperMessageNames} to avoid
         * corrupting this map with synthetic schema keys.
         */
        private final Map<Schema, String> messageNames = new LinkedHashMap<>();

        /**
         * Tracks all message names already emitted (structs, wrapper messages, map entries) to
         * ensure {@link #uniqueMessageName} never produces a collision.
         */
        private final Set<String> allMessageNames = new HashSet<>();

        /**
         * Maps raw Connect field name → sanitised proto field name for the current build context.
         */
        private final Map<String, String> rawToSanitised = new LinkedHashMap<>();

        private MessageBuilderContext(DescriptorProtos.FileDescriptorProto.Builder file) {
            this.file = file;
        }

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
            boolean nameChanged = !name.equals(sanitisedName);

            DescriptorProtos.FieldDescriptorProto.Builder field = DescriptorProtos.FieldDescriptorProto.newBuilder()
                    .setName(sanitisedName)
                    .setNumber(number);

            if (schema.type() == Schema.Type.ARRAY) {
                field.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
                applyFieldType(field, schema.valueSchema(), sanitizeTypeName(name));
                applyJsonName(field, schema.valueSchema(), name, sanitisedName, nameChanged);
            } else if (schema.type() == Schema.Type.MAP) {
                String entryName = addMapEntry(name, schema);
                field.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName("." + packageName() + "." + entryName);
                if (nameChanged) {
                    field.setJsonName(JSON_NAME_RAW_PREFIX + name);
                }
            } else {
                field.setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
                if (schema.isOptional()) {
                    field.setProto3Optional(true);
                }
                applyFieldType(field, schema, sanitizeTypeName(name));
                applyJsonName(field, schema, name, sanitisedName, nameChanged);
            }
            return field.build();
        }

        /**
         * Sets the {@code json_name} option on {@code field} to encode the type hint (INT8/INT16)
         * and/or the raw Connect field name when it differs from the sanitised proto name.
         *
         * <p>Format: {@code [typePrefix][__raw:rawName | sanitisedName]}
         * <ul>
         *   <li>{@code __int8:user_id} — INT8 field, name not changed</li>
         *   <li>{@code __int8:__raw:user-id} — INT8 field, name was sanitised</li>
         *   <li>{@code __raw:user-id} — regular field, name was sanitised</li>
         *   <li>(not set) — regular field, name was not changed</li>
         * </ul>
         */
        private void applyJsonName(DescriptorProtos.FieldDescriptorProto.Builder field,
                Schema effectiveSchema, String rawName, String sanitisedName, boolean nameChanged) {
            String typePrefix = "";
            if (effectiveSchema.type() == Schema.Type.INT8) {
                typePrefix = JSON_NAME_INT8_PREFIX;
            } else if (effectiveSchema.type() == Schema.Type.INT16) {
                typePrefix = JSON_NAME_INT16_PREFIX;
            }
            if (!typePrefix.isEmpty() || nameChanged) {
                String namePart = nameChanged ? (JSON_NAME_RAW_PREFIX + rawName) : sanitisedName;
                field.setJsonName(typePrefix + namePart);
            }
        }

        private void applyFieldType(DescriptorProtos.FieldDescriptorProto.Builder field, Schema schema,
                String preferredMessageName) {
            switch (schema.type()) {
                case INT8:
                case INT16:
                    field.setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32);
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
         * Creates a synthetic wrapper message {@code WrapperName { repeated <T> items = 1; }}.
         *
         * <p>The wrapper name is tracked in {@link #allMessageNames} for uniqueness but NOT in
         * {@link #messageNames}, which is reserved for struct dedup keyed by Connect Schema.
         * This prevents collisions when the same struct schema is later used as a standalone field.
         */
        private String addWrapperMessage(String preferredName, Schema elementSchema) {
            String wrapperName = uniqueMessageName(preferredName);
            DescriptorProtos.DescriptorProto.Builder wrapper = DescriptorProtos.DescriptorProto.newBuilder()
                    .setName(wrapperName);
            DescriptorProtos.FieldDescriptorProto.Builder itemsField =
                    DescriptorProtos.FieldDescriptorProto.newBuilder()
                            .setName(WRAPPER_ITEMS_FIELD)
                            .setNumber(1)
                            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
            applyFieldType(itemsField, elementSchema, wrapperName + "Element");
            wrapper.addField(itemsField);
            file.addMessageType(wrapper);
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

        /**
         * Returns a unique message name, avoiding collisions with all previously emitted messages
         * (structs, wrappers, and map entries). Both {@link #messageNames} values and
         * {@link #allMessageNames} are consulted so wrapper names do not shadow struct names.
         */
        private String uniqueMessageName(String preferredName) {
            String sanitized = sanitizeTypeName(preferredName);
            String candidate = sanitized;
            int idx = 2;
            while (allMessageNames.contains(candidate) || messageNames.containsValue(candidate)) {
                candidate = sanitized + idx++;
            }
            allMessageNames.add(candidate);
            return candidate;
        }

        private String packageName() {
            return file.getPackage();
        }
    }
}
