package io.apicurio.registry.openapi.content.dereference;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apitomy.datamodels.Library;
import io.apitomy.datamodels.models.Document;
import io.apitomy.datamodels.models.Node;
import io.apitomy.datamodels.models.asyncapi.AsyncApiMultiFormatSchema;
import io.apitomy.datamodels.refs.LocalReferenceResolver;
import io.apitomy.datamodels.refs.ResolvedReference;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.types.ContentTypes;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern PROTO_COMMENT_PATTERN = Pattern
            .compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern PROTO_SYNTAX_PATTERN = Pattern
            .compile("\\bsyntax\\s*=\\s*[\"']proto([23])[\"']");

    private static final Pattern PROTO_EDITION_PATTERN = Pattern
            .compile("\\bedition\\s*=\\s*[\"'][^\"']*[\"']");

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

        // Check for Protobuf first (text-based). The content type may be a media type or an artifact type name.
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("proto")) {
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
     * @see io.apitomy.datamodels.refs.IReferenceResolver#resolveRef(java.lang.String,
     *      io.apitomy.datamodels.models.Node)
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
                        return ResolvedReference.fromJson(avroNode, avroSchemaFormat(from));

                    case PROTOBUF:
                        // For Protobuf, return as text with appropriate media type
                        // The dereferencer will wrap it in a Multi-Format Schema Object
                        String protoContent = resolvedRefContent.getContent().content();
                        return ResolvedReference.fromText(protoContent,
                                protobufSchemaFormat(from, protoContent));

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

    /**
     * Determines the schemaFormat to use for a referenced Avro schema.
     *
     * @param from the node containing the unresolved reference
     */
    private static String avroSchemaFormat(Node from) {
        // Keep the version the document already declares rather than rewriting it
        String declared = declaredSchemaFormat(from);
        if (declared != null && declared.startsWith(ContentTypes.ASYNCAPI_SCHEMA_FORMAT_AVRO_PREFIX)) {
            return declared;
        }
        return ContentTypes.ASYNCAPI_SCHEMA_FORMAT_AVRO;
    }

    /**
     * Determines the schemaFormat to use for a referenced Protobuf schema.
     *
     * @param from the node containing the unresolved reference
     * @param protoContent the referenced Protobuf schema
     */
    private static String protobufSchemaFormat(Node from, String protoContent) {
        // Keep the version the document already declares rather than rewriting it
        String declared = declaredSchemaFormat(from);
        if (declared != null
                && declared.startsWith(ContentTypes.ASYNCAPI_SCHEMA_FORMAT_PROTOBUF_PREFIX)) {
            return declared;
        }
        return detectProtobufSchemaFormat(protoContent);
    }

    /**
     * Detects the Protobuf schemaFormat from the schema's syntax statement.
     *
     * @param protoContent the Protobuf schema
     */
    private static String detectProtobufSchemaFormat(String protoContent) {
        // Strip comments first so a commented-out syntax statement is not matched
        String stripped = PROTO_COMMENT_PATTERN.matcher(protoContent).replaceAll(" ");
        // AsyncAPI registers nothing newer than proto3, so editions map to it
        if (PROTO_EDITION_PATTERN.matcher(stripped).find()) {
            return ContentTypes.ASYNCAPI_SCHEMA_FORMAT_PROTOBUF_3;
        }
        Matcher syntaxMatcher = PROTO_SYNTAX_PATTERN.matcher(stripped);
        if (syntaxMatcher.find() && "3".equals(syntaxMatcher.group(1))) {
            return ContentTypes.ASYNCAPI_SCHEMA_FORMAT_PROTOBUF_3;
        }
        // No syntax statement means proto2 (the Protobuf language default)
        return ContentTypes.ASYNCAPI_SCHEMA_FORMAT_PROTOBUF_2;
    }

    /**
     * Returns the schemaFormat declared on the Multi-Format Schema Object containing the reference,
     * or null if there is none.
     *
     * @param from the node containing the unresolved reference
     */
    private static String declaredSchemaFormat(Node from) {
        // The $ref may be on the Multi-Format Schema Object itself or on its "schema" property
        Node node = from;
        for (int depth = 0; node != null && depth < 2; depth++) {
            if (node instanceof AsyncApiMultiFormatSchema) {
                return ((AsyncApiMultiFormatSchema) node).getSchemaFormat();
            }
            node = node.parent();
        }
        return null;
    }

}
