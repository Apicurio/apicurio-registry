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
    private String cachedProtoText;      // Stores either original or generated text
    private String cachedNormalizedText; // Stores normalized text (generated via protobuf4j)
    private final boolean hasOriginalText; // Tracks if original was provided

    public ProtobufSchema(FileDescriptor fileDescriptor) {
        Objects.requireNonNull(fileDescriptor);
        this.fileDescriptor = fileDescriptor;
        this.cachedProtoText = null; // Will be generated on-demand if needed
        this.cachedNormalizedText = null;
        this.hasOriginalText = false;
    }

    public ProtobufSchema(FileDescriptor fileDescriptor, String originalProtoText) {
        Objects.requireNonNull(fileDescriptor);
        this.fileDescriptor = fileDescriptor;
        this.cachedProtoText = originalProtoText;
        this.cachedNormalizedText = null;
        this.hasOriginalText = originalProtoText != null;
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
     * Returns the original .proto text if available, otherwise generates normalized text
     * from FileDescriptor using protobuf4j. The generated text is cached for subsequent calls.
     *
     * <p>For explicit canonicalization purposes where you always want the normalized form,
     * use {@link #toNormalizedProtoText()} instead.</p>
     *
     * @return Text representation of the schema in .proto format
     */
    public String toProtoText() {
        if (cachedProtoText != null) {
            return cachedProtoText;
        }
        // Generate .proto text from FileDescriptor and cache it
        cachedProtoText = ProtobufSchemaUtils.toProtoText(fileDescriptor);
        return cachedProtoText;
    }

    /**
     * Convert this schema to normalized/canonical protobuf text format.
     *
     * This method always returns the normalized form generated via protobuf4j's
     * normalization, regardless of whether original text was provided. This ensures
     * consistent representation for content hashing and comparison.
     *
     * <p>The normalized text is cached separately from the original text.</p>
     *
     * @return Normalized/canonical text representation of the schema in .proto format
     */
    public String toNormalizedProtoText() {
        if (cachedNormalizedText != null) {
            return cachedNormalizedText;
        }
        // Always generate normalized text from FileDescriptor
        cachedNormalizedText = ProtobufSchemaUtils.toProtoText(fileDescriptor);
        return cachedNormalizedText;
    }

    /**
     * Returns true if this schema was created with original .proto text.
     *
     * <p>When original text is available, {@link #toProtoText()} returns it as-is.
     * When not available, it generates normalized text from the FileDescriptor.</p>
     *
     * @return true if original .proto text was provided at construction time
     */
    public boolean hasOriginalProtoText() {
        return hasOriginalText;
    }
}
