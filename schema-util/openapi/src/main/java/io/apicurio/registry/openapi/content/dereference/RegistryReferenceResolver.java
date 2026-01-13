package io.apicurio.registry.openapi.content.dereference;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.refs.LocalReferenceResolver;
import io.apicurio.datamodels.refs.ResolvedReference;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.io.IOException;
import java.util.Map;

public class RegistryReferenceResolver extends LocalReferenceResolver {

    /**
     * Enum to classify the type of referenced content.
     */
    private enum ReferencedContentType {
        OPENAPI,
        ASYNCAPI,
        JSON_SCHEMA,
        AVRO,
        PROTOBUF,
        OTHER
    }

    private final Map<String, TypedContent> resolvedReferences;

    /**
     * Constructor.
     * 
     * @param resolvedReferences
     */
    public RegistryReferenceResolver(Map<String, TypedContent> resolvedReferences) {
        this.resolvedReferences = resolvedReferences;
    }

    /**
     * Detects the type of referenced content.
     *
     * @param resolvedRefContent the resolved reference content
     * @return the detected content type
     */
    private ReferencedContentType detectContentType(TypedContent resolvedRefContent) {
        String contentType = resolvedRefContent.getContentType();

        // Check for Protobuf first (text-based)
        if (contentType != null && (contentType.contains("protobuf") || contentType.contains("proto"))) {
            return ReferencedContentType.PROTOBUF;
        }

        // For JSON/YAML content, parse and inspect the structure
        try {
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(resolvedRefContent);

            // Check for OpenAPI (has "openapi" property or "swagger" property)
            if (node.has("openapi") || node.has("swagger")) {
                return ReferencedContentType.OPENAPI;
            }

            // Check for AsyncAPI (has "asyncapi" field)
            if (node.has("asyncapi")) {
                return ReferencedContentType.ASYNCAPI;
            }

            // Check for JSON Schema (has "$schema" field or is a schema-like structure)
            if (node.has("$schema")) {
                return ReferencedContentType.JSON_SCHEMA;
            }

            // Check for Avro (has "type" and optionally "name", "namespace", "fields")
            // Avro records have: type, name, namespace (optional), fields
            // Avro primitives/enums/arrays/maps also have "type"
            if (node.has("type")) {
                JsonNode typeNode = node.get("type");
                if (typeNode.isTextual()) {
                    String type = typeNode.asText();
                    // Avro types: null, boolean, int, long, float, double, bytes, string,
                    // record, enum, array, map, fixed
                    if (type.equals("record") || type.equals("enum") || type.equals("fixed")
                            || type.equals("array") || type.equals("map")
                            || (node.has("name") && (node.has("fields") || node.has("symbols")))) {
                        return ReferencedContentType.AVRO;
                    }
                }
            }

        } catch (IOException e) {
            // Not parsable as JSON/YAML, might be other text format
        }

        return ReferencedContentType.OTHER;
    }

    /**
     * @see io.apicurio.datamodels.refs.IReferenceResolver#resolveRef(java.lang.String,
     *      io.apicurio.datamodels.models.Node)
     */
    @Override
    public ResolvedReference resolveRef(String reference, Node from) {
        try {
            if (resolvedReferences.containsKey(reference)) {
                TypedContent resolvedRefContent = resolvedReferences.get(reference);

                // Detect the type of content we're dealing with
                ReferencedContentType contentType = detectContentType(resolvedRefContent);

                // Handle based on content type
                switch (contentType) {
                    case OPENAPI:
                    case ASYNCAPI:
                        // For OpenAPI, AsyncAPI, and JSON Schema, parse as Document and resolve the JSON pointer
                        JsonNode node = ContentTypeUtil.parseJsonOrYaml(resolvedRefContent);
                        Document resolvedRefDoc = Library.readDocument((ObjectNode) node);
                        JsonPointerExternalReference ref = new JsonPointerExternalReference(reference);
                        Node resolvedNode = super.resolveRef(ref.getComponent(), resolvedRefDoc).asNode();
                        return ResolvedReference.fromNode(resolvedNode);

                    case JSON_SCHEMA:
                        // For JSON Schema, return as JSON with appropriate media type
                        // The dereferencer will wrap it in a Multi-Format Schema Object
                        JsonNode jsonSchemaNode = ContentTypeUtil.parseJsonOrYaml(resolvedRefContent);

                        // Parse the reference to extract the JSON pointer component
                        JsonPointerExternalReference jsonSchemaRef = new JsonPointerExternalReference(reference);
                        String component = jsonSchemaRef.getComponent();

                        if (component != null && !component.isEmpty()) {
                            // Resolve the JSON pointer to get the specific schema definition
                            // Component format is "#/definitions/Address", need to remove the leading '#'
                            JsonPointer pointer = JsonPointer.compile(component.substring(1));
                            JsonNode resolvedSchema = jsonSchemaNode.at(pointer);

                            if (!resolvedSchema.isMissingNode() && resolvedSchema.isObject()) {
                                // Successfully resolved to a specific schema definition
                                return ResolvedReference.fromJson(resolvedSchema, "application/schema+json");
                            }
                            // If resolution failed, fall through to return the whole document
                        }

                        // No JSON pointer component, or resolution failed - return the whole document
                        return ResolvedReference.fromJson(jsonSchemaNode, "application/schema+json");

                    case AVRO:
                        // For Avro, return as JSON with appropriate media type
                        // The dereferencer will wrap it in a Multi-Format Schema Object
                        JsonNode avroNode = ContentTypeUtil.parseJsonOrYaml(resolvedRefContent);
                        return ResolvedReference.fromJson(avroNode,
                                "application/vnd.apache.avro+json");

                    case PROTOBUF:
                        // For Protobuf, return as text with appropriate media type
                        // The dereferencer will wrap it in a Multi-Format Schema Object
                        String protoContent = resolvedRefContent.getContent().content();
                        return ResolvedReference.fromText(protoContent, "application/x-protobuf");

                    case OTHER:
                        // For Other, return as text with no media type
                        // The dereferencer will wrap it in a Multi-Format Schema Object
                        String otherContent = resolvedRefContent.getContent().content();
                        return ResolvedReference.fromText(otherContent, "text/plain;type=unknown");
                }
            }
            // Cannot resolve the ref, return null.
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
