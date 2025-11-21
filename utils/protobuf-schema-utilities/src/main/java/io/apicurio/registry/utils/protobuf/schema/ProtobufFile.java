package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Indexed representation of a parsed .proto protobuf schema file, used mainly for schema validation.
 *
 * NOTE: This class has been refactored to use FileDescriptor instead of wire-schema's ProtoFileElement.
 * Indexes are now built from FileDescriptorProto (Google protobuf API).
 *
 * @see <a href="https://github.com/nilslice/protolock">Protolock</a>
 */
public class ProtobufFile {

    private final Descriptors.FileDescriptor fileDescriptor;
    private final DescriptorProtos.FileDescriptorProto fileDescriptorProto;

    private final Map<String, Set<Object>> reservedFields = new HashMap<>();
    private final Map<String, Map<String, DescriptorProtos.FieldDescriptorProto>> fieldMap = new HashMap<>();
    private final Map<String, Map<String, DescriptorProtos.EnumValueDescriptorProto>> enumFieldMap = new HashMap<>();
    private final Map<String, Map<String, DescriptorProtos.FieldDescriptorProto>> mapMap = new HashMap<>();
    private final Map<String, Set<Object>> nonReservedFields = new HashMap<>();
    private final Map<String, Set<Object>> nonReservedEnumFields = new HashMap<>();
    private final Map<String, Map<Integer, String>> fieldsById = new HashMap<>();
    private final Map<String, Map<Integer, String>> enumFieldsById = new HashMap<>();
    private final Map<String, Set<String>> serviceRPCnames = new HashMap<>();
    private final Map<String, Map<String, String>> serviceRPCSignatures = new HashMap<>();

    /**
     * Create ProtobufFile from a .proto schema string.
     */
    public ProtobufFile(String data) throws IOException {
        this.fileDescriptor = parseProtoString(data);
        this.fileDescriptorProto = fileDescriptor.toProto();
        buildIndexes();
    }

    /**
     * Create ProtobufFile from a .proto file.
     */
    public ProtobufFile(File file) throws IOException {
        String data = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        this.fileDescriptor = parseProtoString(data);
        this.fileDescriptorProto = fileDescriptor.toProto();
        buildIndexes();
    }

    /**
     * Create ProtobufFile from a FileDescriptor.
     */
    public ProtobufFile(Descriptors.FileDescriptor fileDescriptor) {
        this.fileDescriptor = fileDescriptor;
        this.fileDescriptorProto = fileDescriptor.toProto();
        buildIndexes();
    }

    /**
     * Create ProtobufFile from a FileDescriptorProto.
     */
    public ProtobufFile(DescriptorProtos.FileDescriptorProto fileDescriptorProto) throws Descriptors.DescriptorValidationException {
        this.fileDescriptorProto = fileDescriptorProto;
        this.fileDescriptor = FileDescriptorUtils.protoFileToFileDescriptor(fileDescriptorProto);
        buildIndexes();
    }

    /**
     * Create ProtobufFile from a .proto schema string with dependencies.
     */
    public ProtobufFile(String data, Map<String, String> schemaDefs, Map<String, Descriptors.FileDescriptor> dependencies) throws IOException {
        this.fileDescriptor = parseProtoStringWithDependencies(data, schemaDefs, dependencies);
        this.fileDescriptorProto = fileDescriptor.toProto();
        buildIndexes();
    }

    /**
     * Validate protobuf syntax without resolving imports.
     * This is a lightweight validation that checks if the content looks like valid protobuf syntax.
     * Used by ContentAccepter to quickly determine if content is protobuf without needing dependencies.
     */
    public static void validateSyntaxOnly(String data) throws IOException {
        if (data == null || data.trim().isEmpty()) {
            throw new IOException("Empty protobuf content");
        }

        // Check for basic protobuf syntax markers
        String normalized = data.toLowerCase();
        if (!normalized.contains("syntax") && !normalized.contains("message") &&
            !normalized.contains("enum") && !normalized.contains("service")) {
            throw new IOException("Content does not appear to be a protobuf schema");
        }

        // Try to parse as binary if it's not text
        if (!normalized.contains("syntax")) {
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(data);
                DescriptorProtos.FileDescriptorProto.parseFrom(decodedBytes);
                return; // Valid binary format
            } catch (Exception e) {
                throw new IOException("Invalid protobuf schema - not valid text or binary format", e);
            }
        }

        // For text format, just check it has reasonable structure
        // Don't try to compile it since that requires resolving imports
    }

    /**
     * Parse a .proto string to FileDescriptor.
     * Supports both text format and binary FileDescriptorProto format.
     */
    private static Descriptors.FileDescriptor parseProtoString(String data) throws IOException {
        IOException lastException = null;

        // Try to parse as textual .proto file using protobuf4j
        try {
            return FileDescriptorUtils.protoFileToFileDescriptor(data, "default.proto", Optional.empty());
        } catch (Exception e) {
            lastException = new IOException("Failed to parse protobuf text", e);
            // Continue to try base64 decoding
        }

        // Try to parse as binary FileDescriptorProto (base64 encoded)
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(data);
            DescriptorProtos.FileDescriptorProto descriptorProto = DescriptorProtos.FileDescriptorProto.parseFrom(decodedBytes);
            return FileDescriptorUtils.protoFileToFileDescriptor(descriptorProto);
        } catch (IllegalArgumentException e) {
            // Not base64 encoded, throw the original parsing error
            if (lastException != null) {
                throw lastException;
            }
            throw new IOException("Failed to parse protobuf schema - not valid proto text or base64", e);
        } catch (InvalidProtocolBufferException | Descriptors.DescriptorValidationException e) {
            throw new IOException("Failed to parse protobuf descriptor", e);
        }
    }

    /**
     * Parse a .proto string to FileDescriptor with dependencies.
     * Supports both text format and binary FileDescriptorProto format.
     */
    private static Descriptors.FileDescriptor parseProtoStringWithDependencies(String data, Map<String, String> schemaDefs, Map<String, Descriptors.FileDescriptor> dependencies) throws IOException {
        IOException lastException = null;

        // Try to parse as textual .proto file using protobuf4j
        try {
            return FileDescriptorUtils.protoFileToFileDescriptor(data, "default.proto", Optional.empty(), schemaDefs, dependencies);
        } catch (Exception e) {
            lastException = new IOException("Failed to parse protobuf text with dependencies", e);
            // Continue to try base64 decoding
        }

        // Try to parse as binary FileDescriptorProto (base64 encoded)
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(data);
            DescriptorProtos.FileDescriptorProto descriptorProto = DescriptorProtos.FileDescriptorProto.parseFrom(decodedBytes);
            return FileDescriptorUtils.protoFileToFileDescriptor(descriptorProto);
        } catch (IllegalArgumentException e) {
            // Not base64 encoded, throw the original parsing error
            if (lastException != null) {
                throw lastException;
            }
            throw new IOException("Failed to parse protobuf schema - not valid proto text or base64", e);
        } catch (InvalidProtocolBufferException | Descriptors.DescriptorValidationException e) {
            throw new IOException("Failed to parse protobuf descriptor", e);
        }
    }

    public String getPackageName() {
        return fileDescriptorProto.getPackage();
    }

    public Descriptors.FileDescriptor getFileDescriptor() {
        return fileDescriptor;
    }

    public Map<String, Set<Object>> getReservedFields() {
        return reservedFields;
    }

    public Map<String, Map<String, DescriptorProtos.FieldDescriptorProto>> getFieldMap() {
        return fieldMap;
    }

    public Map<String, Map<String, DescriptorProtos.EnumValueDescriptorProto>> getEnumFieldMap() {
        return enumFieldMap;
    }

    public Map<String, Map<String, DescriptorProtos.FieldDescriptorProto>> getMapMap() {
        return mapMap;
    }

    public Map<String, Set<Object>> getNonReservedFields() {
        return nonReservedFields;
    }

    public Map<String, Set<Object>> getNonReservedEnumFields() {
        return nonReservedEnumFields;
    }

    public Map<String, Map<Integer, String>> getFieldsById() {
        return fieldsById;
    }

    public Map<String, Map<Integer, String>> getEnumFieldsById() {
        return enumFieldsById;
    }

    public Map<String, Set<String>> getServiceRPCnames() {
        return serviceRPCnames;
    }

    public Map<String, Map<String, String>> getServiceRPCSignatures() {
        return serviceRPCSignatures;
    }

    public String getSyntax() {
        String syntax = fileDescriptorProto.getSyntax();
        return (syntax == null || syntax.isEmpty()) ? "proto2" : syntax;
    }

    public String getMapType(String entryType) {
        if (entryType != null && entryType.endsWith("Entry")) {
            return "map<string, string>";
        }
        return null;
    }

    private void buildIndexes() {
        for (DescriptorProtos.DescriptorProto messageType : fileDescriptorProto.getMessageTypeList()) {
            processMessageDescriptor("", messageType);
        }

        for (DescriptorProtos.EnumDescriptorProto enumType : fileDescriptorProto.getEnumTypeList()) {
            processEnumDescriptor("", enumType);
        }

        for (DescriptorProtos.ServiceDescriptorProto serviceProto : fileDescriptorProto.getServiceList()) {
            Set<String> rpcNames = new HashSet<>();
            Map<String, String> rpcSignatures = new HashMap<>();

            for (DescriptorProtos.MethodDescriptorProto method : serviceProto.getMethodList()) {
                rpcNames.add(method.getName());

                String signature = method.getInputType() + ":" + method.getClientStreaming() + "->"
                        + method.getOutputType() + ":" + method.getServerStreaming();
                rpcSignatures.put(method.getName(), signature);
            }

            if (!rpcNames.isEmpty()) {
                serviceRPCnames.put(serviceProto.getName(), rpcNames);
                serviceRPCSignatures.put(serviceProto.getName(), rpcSignatures);
            }
        }
    }

    private void processMessageDescriptor(String scope, DescriptorProtos.DescriptorProto messageDescriptor) {
        String fullName = scope + messageDescriptor.getName();

        // Process reserved fields
        Set<Object> reservedFieldSet = new HashSet<>();
        for (DescriptorProtos.DescriptorProto.ReservedRange range : messageDescriptor.getReservedRangeList()) {
            for (int i = range.getStart(); i < range.getEnd(); i++) {
                reservedFieldSet.add(i);
            }
        }
        for (String reservedName : messageDescriptor.getReservedNameList()) {
            reservedFieldSet.add(reservedName);
        }
        if (!reservedFieldSet.isEmpty()) {
            reservedFields.put(fullName, reservedFieldSet);
        }

        // Process fields
        Map<String, DescriptorProtos.FieldDescriptorProto> fieldTypeMap = new HashMap<>();
        Map<String, DescriptorProtos.FieldDescriptorProto> mapTypeMap = new HashMap<>();
        Map<Integer, String> idsToNames = new HashMap<>();
        Set<Object> fieldKeySet = new HashSet<>();

        for (DescriptorProtos.FieldDescriptorProto field : messageDescriptor.getFieldList()) {
            fieldTypeMap.put(field.getName(), field);
            idsToNames.put(field.getNumber(), field.getName());
            fieldKeySet.add(field.getNumber());
            fieldKeySet.add(field.getName());

            // Check if this is a map field
            if (field.hasTypeName() && field.getTypeName().endsWith("Entry") &&
                field.getLabel() == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
                mapTypeMap.put(field.getName(), field);
            }
        }

        fieldMap.put(fullName, fieldTypeMap);
        if (!mapTypeMap.isEmpty()) {
            mapMap.put(fullName, mapTypeMap);
        }
        if (!idsToNames.isEmpty()) {
            fieldsById.put(fullName, idsToNames);
        }
        nonReservedFields.put(fullName, fieldKeySet);

        // Process nested types
        for (DescriptorProtos.DescriptorProto nestedType : messageDescriptor.getNestedTypeList()) {
            processMessageDescriptor(fullName + ".", nestedType);
        }

        // Process nested enums
        for (DescriptorProtos.EnumDescriptorProto nestedEnum : messageDescriptor.getEnumTypeList()) {
            processEnumDescriptor(fullName + ".", nestedEnum);
        }
    }

    private void processEnumDescriptor(String scope, DescriptorProtos.EnumDescriptorProto enumDescriptor) {
        String fullName = scope + enumDescriptor.getName();

        Map<String, DescriptorProtos.EnumValueDescriptorProto> enumMap = new HashMap<>();
        Map<Integer, String> idsToNames = new HashMap<>();
        Set<Object> fieldKeySet = new HashSet<>();

        for (DescriptorProtos.EnumValueDescriptorProto enumValue : enumDescriptor.getValueList()) {
            enumMap.put(enumValue.getName(), enumValue);
            idsToNames.put(enumValue.getNumber(), enumValue.getName());
            fieldKeySet.add(enumValue.getNumber());
            fieldKeySet.add(enumValue.getName());
        }

        if (!enumMap.isEmpty()) {
            enumFieldMap.put(fullName, enumMap);
        }
        if (!idsToNames.isEmpty()) {
            enumFieldsById.put(fullName, idsToNames);
        }
        if (!fieldKeySet.isEmpty()) {
            nonReservedEnumFields.put(fullName, fieldKeySet);
        }
    }
}
