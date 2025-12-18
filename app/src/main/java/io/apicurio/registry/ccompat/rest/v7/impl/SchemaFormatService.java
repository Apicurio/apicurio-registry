package io.apicurio.registry.ccompat.rest.v7.impl;

import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.avro.content.dereference.AvroDereferencer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.protobuf.content.dereference.ProtobufDereferencer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.protobuf.schema.ProtobufFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;
import org.apache.avro.Schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Service for applying format transformations to schemas based on the Confluent Schema Registry
 * format query parameter.
 */
@ApplicationScoped
public class SchemaFormatService {

    private static final String FORMAT_RESOLVED = "resolved";
    private static final String FORMAT_IGNORE_EXTENSIONS = "ignore_extensions";
    private static final String FORMAT_SERIALIZED = "serialized";

    private static final Map<String, Set<String>> VALID_FORMATS = new HashMap<>();
    static {
        // Valid formats for AVRO
        VALID_FORMATS.put(ArtifactType.AVRO, Set.of(FORMAT_RESOLVED));
        // Valid formats for PROTOBUF
        VALID_FORMATS.put(ArtifactType.PROTOBUF, Set.of(FORMAT_IGNORE_EXTENSIONS, FORMAT_SERIALIZED));
    }

    /**
     * Applies the specified format transformation to the schema content.
     *
     * @param content the original schema content
     * @param artifactType the type of schema (AVRO, PROTOBUF, JSON)
     * @param format the desired output format
     * @param resolvedReferences map of resolved reference contents
     * @return the formatted schema content
     * @throws BadRequestException if the format is invalid for the given schema type
     */
    public ContentHandle applyFormat(ContentHandle content, String artifactType,
                                     String format, Map<String, TypedContent> resolvedReferences) {
        if (format == null || format.trim().isEmpty()) {
            return content;
        }

        validateFormat(artifactType, format);

        switch (artifactType) {
            case ArtifactType.AVRO:
                return applyAvroFormat(content, format, resolvedReferences);
            case ArtifactType.PROTOBUF:
                return applyProtobufFormat(content, format, resolvedReferences);
            case ArtifactType.JSON:
                // JSON schemas don't support format transformations
                return content;
            default:
                return content;
        }
    }

    /**
     * Validates that the format parameter is valid for the given schema type.
     *
     * @param artifactType the type of schema
     * @param format the format parameter to validate
     * @throws BadRequestException if the format is invalid
     */
    private void validateFormat(String artifactType, String format) {
        if (format == null || format.isEmpty() || ArtifactType.JSON.equals(artifactType)) {
            return;
        }

        Set<String> validFormats = VALID_FORMATS.get(artifactType);
        if (validFormats == null || !validFormats.contains(format)) {
            throw new BadRequestException(
                    String.format("Invalid format '%s' for %s schema. Valid values: %s",
                            format, artifactType, String.join(",", validFormats == null ?  Collections.emptySet() : validFormats)));
        }
    }

    /**
     * Applies format transformation for AVRO schemas.
     *
     * @param content the original AVRO schema content
     * @param format the desired format (currently only "resolved" is supported)
     * @param resolvedReferences map of resolved reference contents
     * @return the formatted schema content
     */
    private ContentHandle applyAvroFormat(ContentHandle content, String format,
                                          Map<String, TypedContent> resolvedReferences) {
        if (FORMAT_RESOLVED.equals(format)) {
            // Use the existing AvroDereferencer to resolve all references inline
            ContentDereferencer dereferencer = new AvroDereferencer();
            TypedContent typedContent = TypedContent.create(content, ContentTypes.APPLICATION_JSON);

            if (resolvedReferences != null && !resolvedReferences.isEmpty()) {
                TypedContent dereferencedContent = dereferencer.dereference(typedContent,
                        resolvedReferences);
                return dereferencedContent.getContent();
            } else {
                // No references to resolve, but still parse and return the canonical form
                Schema.Parser parser = new Schema.Parser();
                Schema schema = parser.parse(content.content());
                return ContentHandle.create(schema.toString());
            }
        }

        return content;
    }

    /**
     * Applies format transformation for PROTOBUF schemas.
     *
     * @param content the original PROTOBUF schema content
     * @param format the desired format ("ignore_extensions" or "serialized")
     * @param resolvedReferences map of resolved reference contents
     * @return the formatted schema content
     */
    private ContentHandle applyProtobufFormat(ContentHandle content, String format,
                                              Map<String, TypedContent> resolvedReferences) {
        if (FORMAT_SERIALIZED.equals(format)) {
            return applyProtobufSerializedFormat(content, resolvedReferences);
        } else if (FORMAT_IGNORE_EXTENSIONS.equals(format)) {
            return applyProtobufIgnoreExtensionsFormat(content);
        }

        return content;
    }

    /**
     * Applies the "serialized" format for PROTOBUF schemas, returning the FileDescriptor bytes.
     *
     * @param content the original PROTOBUF schema content
     * @param resolvedReferences map of resolved reference contents
     * @return the serialized FileDescriptor as bytes
     */
    private ContentHandle applyProtobufSerializedFormat(ContentHandle content,
                                                        Map<String, TypedContent> resolvedReferences) {
        // Use the existing ProtobufDereferencer logic to create FileDescriptor
        ContentDereferencer dereferencer = new ProtobufDereferencer();
        TypedContent typedContent = TypedContent.create(content, ContentTypes.APPLICATION_PROTOBUF);

        Map<String, TypedContent> refs = resolvedReferences != null ? resolvedReferences
                : Collections.emptyMap();
        TypedContent dereferencedContent = dereferencer.dereference(typedContent, refs);

        // The dereferencer already returns the serialized FileDescriptor bytes
        return dereferencedContent.getContent();
    }

    /**
     * Applies the "ignore_extensions" format for PROTOBUF schemas, removing extensions and custom
     * options.
     *
     * @param content the original PROTOBUF schema content
     * @return the schema content without extensions
     */
    private ContentHandle applyProtobufIgnoreExtensionsFormat(ContentHandle content) {
        try {
            ProtoFileElement protoFileElement = ProtobufFile.toProtoFileElement(content.content());

            // Create a new ProtoFileElement without extensions
            ProtoFileElement cleanedProto = new ProtoFileElement(protoFileElement.getLocation(),
                    protoFileElement.getPackageName(), protoFileElement.getSyntax(),
                    protoFileElement.getImports(), protoFileElement.getPublicImports(),
                    protoFileElement.getWeakImports(),
                    protoFileElement.getTypes(), protoFileElement.getServices(),
                    Collections.emptyList(), // Remove extensions
                    protoFileElement.getOptions());

            // Convert back to string format
            String cleanedSchema = cleanedProto.toSchema();
            return ContentHandle.create(cleanedSchema);
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply ignore_extensions format to PROTOBUF schema",
                    e);
        }
    }
}
