package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;

import java.util.List;
import java.util.Objects;

/**
 * Represents a parsed Protobuf schema with its FileDescriptor and indexed file representation.
 *
 * NOTE: This class has been refactored to use only Google Protobuf API types (FileDescriptor).
 * The wire-schema ProtoFileElement field has been removed. Downstream code that depends on
 * getProtoFileElement() needs to be updated to work with FileDescriptor directly.
 *
 * Helper methods are provided for common operations that previously required wire-schema AST.
 */
public class ProtobufSchema {

    private final FileDescriptor fileDescriptor;
    private ProtobufFile protobufFile;
    private String originalProtoText; // Store original .proto text for toProtoText()

    public ProtobufSchema(FileDescriptor fileDescriptor) {
        Objects.requireNonNull(fileDescriptor);
        this.fileDescriptor = fileDescriptor;
        this.originalProtoText = null; // Will be generated on-demand if needed
    }

    public ProtobufSchema(FileDescriptor fileDescriptor, String originalProtoText) {
        Objects.requireNonNull(fileDescriptor);
        this.fileDescriptor = fileDescriptor;
        this.originalProtoText = originalProtoText;
    }

    /**
     * @return the fileDescriptor
     */
    public FileDescriptor getFileDescriptor() {
        return fileDescriptor;
    }

    /**
     * @return the protobufFile (lazy-initialized)
     */
    public ProtobufFile getProtobufFile() {
        if (protobufFile == null) {
            protobufFile = new ProtobufFile(fileDescriptor);
        }
        return protobufFile;
    }

    // ==================================================================================
    // HELPER METHODS - Convenience methods to replace wire-schema AST functionality
    // ==================================================================================

    /**
     * Get the name of the first message in this schema.
     * Replaces: getProtoFileElement().getTypes().get(0).getName()
     *
     * @return The name of the first message, or null if no messages exist
     */
    public String getFirstMessageName() {
        return ProtobufSchemaUtils.getFirstMessageName(fileDescriptor);
    }

    /**
     * Get the first message Descriptor in this schema.
     * Replaces: FileDescriptorUtils.firstMessage(getProtoFileElement())
     *
     * @return The first message Descriptor, or null if no messages exist
     */
    public Descriptors.Descriptor getFirstMessage() {
        return ProtobufSchemaUtils.getFirstMessage(fileDescriptor);
    }

    /**
     * Find a message by name in this schema.
     *
     * @param messageName The name of the message to find
     * @return The Descriptor for the message, or null if not found
     */
    public Descriptors.Descriptor findMessage(String messageName) {
        return ProtobufSchemaUtils.findMessage(fileDescriptor, messageName);
    }

    /**
     * Get all message names from this schema.
     *
     * @return List of message names
     */
    public List<String> getMessageNames() {
        return ProtobufSchemaUtils.getMessageNames(fileDescriptor);
    }

    /**
     * Get all dependency file names from this schema.
     * Replaces: getProtoFileElement().getImports()
     *
     * @return List of dependency file names
     */
    public List<String> getDependencyNames() {
        return ProtobufSchemaUtils.getDependencyNames(fileDescriptor);
    }

    /**
     * Convert this schema to protobuf text format.
     * Replaces: getProtoFileElement().toSchema()
     *
     * Returns the original .proto text if available, otherwise generates it from FileDescriptor.
     *
     * @return Text representation of the schema in .proto format
     */
    public String toProtoText() {
        if (originalProtoText != null) {
            return originalProtoText;
        }
        // Generate .proto text from FileDescriptor
        return ProtobufSchemaUtils.toProtoText(fileDescriptor);
    }

    // ==================================================================================
    // REMOVED METHOD: getProtoFileElement()
    // ==================================================================================
    // The getProtoFileElement() method has been removed because it returned wire-schema's
    // ProtoFileElement type. To restore this functionality, protobuf4j needs to expose AST.
    //
    // Downstream modules that depend on this method:
    // - serdes: ProtobufSchemaParser
    // - schema-util/protobuf: various validators and canonicalizers
    //
    // Migration options:
    // 1. Add AST support to protobuf4j and expose ProtoFileElement-like API
    // 2. Refactor downstream code to work directly with FileDescriptor
    // 3. Use FileDescriptor.toProto() to get FileDescriptorProto and work with that
    // ==================================================================================

}
