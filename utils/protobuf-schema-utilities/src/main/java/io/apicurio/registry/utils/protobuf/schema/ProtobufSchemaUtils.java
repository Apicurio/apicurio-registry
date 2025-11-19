package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for protobuf schema operations using protobuf4j.
 * This class replaces wire-schema functionality throughout the codebase.
 *
 * Purpose: Provide helper methods to work with FileDescriptor API directly,
 * eliminating the need for wire-schema AST types (ProtoFileElement, MessageElement, etc.)
 */
public class ProtobufSchemaUtils {

    /**
     * Parse and compile a protobuf schema with dependencies using protobuf4j.
     *
     * This replaces the wire-schema pattern:
     * <pre>
     * ProtoFileElement fileElem = ProtoParser.parse(location, content);
     * Descriptor descriptor = FileDescriptorUtils.toDescriptor(messageName, fileElem, deps);
     * </pre>
     *
     * With:
     * <pre>
     * FileDescriptor fd = ProtobufSchemaUtils.parseAndCompile(fileName, content, deps);
     * </pre>
     *
     * @param fileName The proto file name (e.g., "main.proto")
     * @param protoContent The .proto file content
     * @param dependencies Map of dependency file names to their content
     * @return Compiled FileDescriptor
     * @throws IOException if parsing or compilation fails
     */
    public static FileDescriptor parseAndCompile(String fileName, String protoContent,
                                                  Map<String, String> dependencies) throws IOException {
        try {
            // Extract package name from schema
            Optional<String> packageName = extractPackageName(protoContent);

            // Use existing FileDescriptorUtils method which uses protobuf4j internally
            return FileDescriptorUtils.protoFileToFileDescriptor(
                protoContent, fileName, packageName, dependencies, Collections.emptyMap()
            );
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IOException("Failed to parse and compile protobuf schema: " + fileName, e);
        }
    }

    /**
     * Get the name of the first message in a FileDescriptor.
     *
     * Replaces: FileDescriptorUtils.firstMessage(ProtoFileElement).getName()
     *
     * @param descriptor The FileDescriptor to inspect
     * @return The name of the first message, or null if no messages exist
     */
    public static String getFirstMessageName(FileDescriptor descriptor) {
        return descriptor.getMessageTypes().isEmpty()
            ? null
            : descriptor.getMessageTypes().get(0).getName();
    }

    /**
     * Get the first message Descriptor from a FileDescriptor.
     *
     * Replaces: FileDescriptorUtils.firstMessage(ProtoFileElement)
     *
     * @param descriptor The FileDescriptor to inspect
     * @return The first message Descriptor, or null if no messages exist
     */
    public static Descriptors.Descriptor getFirstMessage(FileDescriptor descriptor) {
        return descriptor.getMessageTypes().isEmpty()
            ? null
            : descriptor.getMessageTypes().get(0);
    }

    /**
     * Find a message by name in a FileDescriptor.
     *
     * @param descriptor The FileDescriptor to search
     * @param messageName The name of the message to find
     * @return The Descriptor for the message, or null if not found
     */
    public static Descriptors.Descriptor findMessage(FileDescriptor descriptor, String messageName) {
        return descriptor.findMessageTypeByName(messageName);
    }

    /**
     * Get all message names from a FileDescriptor.
     *
     * @param descriptor The FileDescriptor to inspect
     * @return List of message names
     */
    public static List<String> getMessageNames(FileDescriptor descriptor) {
        return descriptor.getMessageTypes().stream()
            .map(Descriptors.Descriptor::getName)
            .collect(Collectors.toList());
    }

    /**
     * Get all import/dependency names from a FileDescriptor.
     *
     * Replaces: Parsing ProtoFileElement.imports()
     *
     * @param descriptor The FileDescriptor to inspect
     * @return List of dependency file names
     */
    public static List<String> getDependencyNames(FileDescriptor descriptor) {
        return descriptor.getDependencies().stream()
            .map(FileDescriptor::getName)
            .collect(Collectors.toList());
    }

    /**
     * Convert FileDescriptor to protobuf text format.
     *
     * This replaces: ProtoFileElement.toSchema()
     *
     * Note: This uses FileDescriptorProto.toString() which produces protobuf text format
     * (not as pretty as .proto source, but semantically equivalent and parseable).
     *
     * @param descriptor The FileDescriptor to convert
     * @return Text representation of the schema
     */
    public static String toProtoText(FileDescriptor descriptor) {
        return descriptor.toProto().toString();
    }

    /**
     * Extract package name from proto schema text.
     *
     * @param schema The proto schema content
     * @return Optional package name
     */
    private static Optional<String> extractPackageName(String schema) {
        String[] lines = schema.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                String packageName = trimmed.substring(8, trimmed.length() - 1).trim();
                return Optional.of(packageName);
            }
        }
        return Optional.empty();
    }

    /**
     * Helper method to add convenience methods to ProtobufSchema.
     * This provides a bridge for code that previously used ProtoFileElement.
     */
    public static class ProtobufSchemaHelper {

        /**
         * Get first message name from ProtobufSchema.
         */
        public static String getFirstMessageName(ProtobufSchema schema) {
            return ProtobufSchemaUtils.getFirstMessageName(schema.getFileDescriptor());
        }

        /**
         * Get first message from ProtobufSchema.
         */
        public static Descriptors.Descriptor getFirstMessage(ProtobufSchema schema) {
            return ProtobufSchemaUtils.getFirstMessage(schema.getFileDescriptor());
        }

        /**
         * Find message by name in ProtobufSchema.
         */
        public static Descriptors.Descriptor findMessage(ProtobufSchema schema, String messageName) {
            return ProtobufSchemaUtils.findMessage(schema.getFileDescriptor(), messageName);
        }
    }
}
