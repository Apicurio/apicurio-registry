package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Convert FileDescriptor to .proto text format using protobuf4j.
     *
     * This replaces: ProtoFileElement.toSchema()
     *
     * Uses protobuf4j's toProtoText(FileDescriptor) which converts a single FileDescriptor
     * to .proto text output. This method now uses {@link ProtobufCompilationContext} for
     * pooled filesystem and WASM instance reuse.
     *
     * @param descriptor The FileDescriptor to convert
     * @return .proto text representation
     */
    public static String toProtoText(FileDescriptor descriptor) {
        // Use pooled compilation context for better performance
        try (ProtobufCompilationContext ctx = ProtobufCompilationContext.acquire()) {
            return ctx.getProtobuf().toProtoText(descriptor);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert FileDescriptor to proto text", e);
        }
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

    // Pattern to match import statements: import "path/to/file.proto";
    // Handles: import "file.proto"; import 'file.proto'; import public "file.proto"; import weak "file.proto";
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "^\\s*import\\s+(?:public\\s+|weak\\s+)?[\"']([^\"']+)[\"']\\s*;",
            Pattern.MULTILINE
    );

    /**
     * Extract import statements from proto schema text WITHOUT compiling.
     * This is useful for reference validation where we need to know what imports
     * are declared without actually resolving them.
     *
     * @param schemaContent The proto schema content
     * @return Set of import paths (e.g., "message2.proto", "google/protobuf/timestamp.proto")
     */
    public static Set<String> extractImports(String schemaContent) {
        Set<String> imports = new HashSet<>();
        if (schemaContent == null || schemaContent.isEmpty()) {
            return imports;
        }

        Matcher matcher = IMPORT_PATTERN.matcher(schemaContent);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    /**
     * Extract non-well-known imports from proto schema text.
     * Filters out google/protobuf/* and google/type/* imports.
     *
     * @param schemaContent The proto schema content
     * @return Set of import paths excluding well-known types
     */
    public static Set<String> extractNonWellKnownImports(String schemaContent) {
        return extractImports(schemaContent).stream()
                .filter(imp -> !isWellKnownType(imp))
                .collect(Collectors.toSet());
    }

    /**
     * Check if an import path is a well-known type.
     * @see ProtobufWellKnownTypes#isWellKnownType(String)
     */
    private static boolean isWellKnownType(String importPath) {
        return ProtobufWellKnownTypes.isWellKnownType(importPath);
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

    // ==================================================================================
    // BINARY DESCRIPTOR SUPPORT
    // ==================================================================================
    // Methods for detecting and parsing base64-encoded FileDescriptorProto binary format.
    // This supports users who store compiled protobuf descriptors instead of .proto text.
    // ==================================================================================

    /**
     * Check if the given content appears to be a base64-encoded binary protobuf descriptor.
     *
     * This method attempts to detect binary protobuf format by:
     * 1. Checking if it's NOT valid proto text (doesn't start with proto keywords)
     * 2. Checking if it CAN be decoded as base64
     * 3. Checking if the decoded bytes parse as a FileDescriptorProto
     *
     * @param content The schema content to check
     * @return true if the content appears to be a base64-encoded FileDescriptorProto
     */
    public static boolean isBase64BinaryDescriptor(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String trimmed = content.trim();

        // Quick check: if it looks like proto text, it's not binary
        if (looksLikeProtoText(trimmed)) {
            return false;
        }

        // Try to decode as base64 and parse as FileDescriptorProto
        try {
            byte[] decoded = Base64.getDecoder().decode(trimmed);
            DescriptorProtos.FileDescriptorProto.parseFrom(decoded);
            return true;
        } catch (IllegalArgumentException | InvalidProtocolBufferException e) {
            return false;
        }
    }

    /**
     * Check if the given raw bytes appear to be a binary protobuf descriptor.
     *
     * @param rawBytes The raw bytes to check
     * @return true if the bytes parse as a FileDescriptorProto
     */
    public static boolean isBinaryDescriptor(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return false;
        }

        try {
            DescriptorProtos.FileDescriptorProto.parseFrom(rawBytes);
            return true;
        } catch (InvalidProtocolBufferException e) {
            return false;
        }
    }

    /**
     * Quick heuristic check if content looks like proto text format.
     * This helps avoid expensive base64 decode + proto parse attempts.
     */
    private static boolean looksLikeProtoText(String content) {
        // Common proto file starters
        return content.startsWith("syntax")
                || content.startsWith("package")
                || content.startsWith("import")
                || content.startsWith("option")
                || content.startsWith("message")
                || content.startsWith("enum")
                || content.startsWith("service")
                || content.startsWith("//")   // Comment
                || content.startsWith("/*");  // Block comment
    }

    /**
     * Parse a base64-encoded FileDescriptorProto to a FileDescriptor.
     *
     * @param base64Content The base64-encoded FileDescriptorProto content
     * @return The parsed FileDescriptor
     * @throws IOException if parsing fails
     */
    public static FileDescriptor parseBase64BinaryDescriptor(String base64Content) throws IOException {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Content.trim());
            return parseBinaryDescriptor(decoded);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid base64 encoding for protobuf descriptor", e);
        }
    }

    /**
     * Parse binary FileDescriptorProto bytes to a FileDescriptor.
     *
     * @param rawBytes The raw FileDescriptorProto bytes
     * @return The parsed FileDescriptor
     * @throws IOException if parsing fails
     */
    public static FileDescriptor parseBinaryDescriptor(byte[] rawBytes) throws IOException {
        try {
            DescriptorProtos.FileDescriptorProto descriptorProto =
                    DescriptorProtos.FileDescriptorProto.parseFrom(rawBytes);
            return FileDescriptorUtils.protoFileToFileDescriptor(descriptorProto);
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Invalid protobuf descriptor format", e);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IOException("Invalid protobuf descriptor: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a base64-encoded FileDescriptorProto with binary dependencies.
     *
     * This method handles the case where both the main schema and its dependencies
     * are in base64-encoded binary format.
     *
     * @param base64Content The base64-encoded FileDescriptorProto content
     * @param dependencies Map of dependency names to their content (may be text or base64 binary)
     * @return The parsed FileDescriptor with dependencies resolved
     * @throws IOException if parsing fails
     */
    public static FileDescriptor parseBase64BinaryDescriptorWithDependencies(
            String base64Content, Map<String, String> dependencies) throws IOException {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Content.trim());
            DescriptorProtos.FileDescriptorProto mainProto =
                    DescriptorProtos.FileDescriptorProto.parseFrom(decoded);

            // Build dependency FileDescriptors
            Map<String, FileDescriptor> depDescriptors = new HashMap<>();

            // First, parse all dependencies (they may be text or binary)
            for (Map.Entry<String, String> entry : dependencies.entrySet()) {
                String depName = entry.getKey();
                String depContent = entry.getValue();

                FileDescriptor depFd;
                if (isBase64BinaryDescriptor(depContent)) {
                    depFd = parseBase64BinaryDescriptor(depContent);
                } else {
                    // Try to parse as text
                    depFd = parseAndCompile(depName, depContent, Collections.emptyMap());
                }
                depDescriptors.put(depFd.getName(), depFd);
            }

            // Build the main descriptor with dependencies
            FileDescriptor[] depArray = resolveDependencyOrder(mainProto, depDescriptors);
            return FileDescriptor.buildFrom(mainProto, depArray);

        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid base64 encoding for protobuf descriptor", e);
        } catch (InvalidProtocolBufferException e) {
            throw new IOException("Invalid protobuf descriptor format", e);
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IOException("Invalid protobuf descriptor: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve dependencies in the correct order for a FileDescriptorProto.
     */
    private static FileDescriptor[] resolveDependencyOrder(
            DescriptorProtos.FileDescriptorProto proto,
            Map<String, FileDescriptor> availableDeps) {

        // Start with well-known dependencies
        FileDescriptor[] wellKnown = FileDescriptorUtils.baseDependencies();
        Map<String, FileDescriptor> allDeps = new HashMap<>();
        for (FileDescriptor fd : wellKnown) {
            allDeps.put(fd.getName(), fd);
        }
        allDeps.putAll(availableDeps);

        // Return dependencies in the order they're declared in the proto
        FileDescriptor[] result = new FileDescriptor[proto.getDependencyCount()];
        for (int i = 0; i < proto.getDependencyCount(); i++) {
            String depName = proto.getDependency(i);
            FileDescriptor dep = allDeps.get(depName);
            if (dep == null) {
                // Try to find by base name (without path)
                String baseName = depName.contains("/")
                        ? depName.substring(depName.lastIndexOf('/') + 1)
                        : depName;
                dep = allDeps.values().stream()
                        .filter(fd -> fd.getName().endsWith(baseName))
                        .findFirst()
                        .orElse(null);
            }
            result[i] = dep;
        }

        // Filter out nulls and return
        return java.util.Arrays.stream(result)
                .filter(java.util.Objects::nonNull)
                .toArray(FileDescriptor[]::new);
    }

    /**
     * Validate a base64-encoded binary descriptor's syntax.
     *
     * @param base64Content The base64-encoded FileDescriptorProto content
     * @throws IOException if the content is not a valid protobuf descriptor
     */
    public static void validateBinaryDescriptorSyntax(String base64Content) throws IOException {
        // Simply parse it - if it parses, the syntax is valid
        parseBase64BinaryDescriptor(base64Content);
    }
}
