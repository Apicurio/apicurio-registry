package io.apicurio.registry.openapi.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.apitomy.datamodels.Library;
import io.apitomy.datamodels.models.Node;
import io.apitomy.datamodels.models.asyncapi.AsyncApiMessage;
import io.apitomy.datamodels.models.asyncapi.v2x.AsyncApi2xMessage;
import io.apitomy.datamodels.models.visitors.AllNodeVisitor;
import io.apitomy.datamodels.refs.IReferenceResolver;
import io.apitomy.datamodels.refs.ResolvedReference;
import io.apitomy.datamodels.util.ModelTypeUtil;

/**
 * Dereferences the payloads of AsyncAPI 2.x Message Objects. The data models library keeps a 2.x message
 * payload as raw JSON, so the library's dereferencer never sees a {@code $ref} in it; this visitor inlines
 * such references instead.
 * <p>
 * In AsyncAPI 2.x a message's {@code schemaFormat} describes its payload, and there is no Multi-Format
 * Schema Object to carry one per schema, so the referenced schema is inlined directly into the payload:
 * <ul>
 * <li>Avro: the schema is inlined and the message gets a versioned Avro schemaFormat (a versioned Avro
 * declaration is kept).</li>
 * <li>Protobuf: the schema text is inlined and the message gets a versioned Protobuf schemaFormat (a
 * versioned Protobuf declaration is kept, otherwise the version is inferred from the schema).</li>
 * <li>JSON Schema and schemas inside OpenAPI/AsyncAPI documents: the schema is inlined and the message's
 * schemaFormat is left as declared. Schemas that contain their own {@code $ref}s are left unresolved,
 * because those references are relative to the referenced document and would break once inlined.</li>
 * </ul>
 * Internal references and references that cannot be resolved are left untouched.
 */
public class AsyncApi2PayloadInliner extends AllNodeVisitor {

    private static final String REF = "$ref";

    private final IReferenceResolver resolver;

    /**
     * Constructor.
     *
     * @param resolver the resolver used to load referenced content
     */
    public AsyncApi2PayloadInliner(IReferenceResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * @see io.apitomy.datamodels.models.visitors.AllNodeVisitor#visitNode(Node)
     */
    @Override
    protected void visitNode(Node node) {
        // Only messages are of interest
    }

    /**
     * @see io.apitomy.datamodels.models.visitors.AllNodeVisitor#visitMessage(AsyncApiMessage)
     */
    @Override
    public void visitMessage(AsyncApiMessage node) {
        super.visitMessage(node);
        if (!ModelTypeUtil.isAsyncApi2Model(node)) {
            return;
        }
        AsyncApi2xMessage message = (AsyncApi2xMessage) node;
        JsonNode payload = message.getPayload();
        if (payload == null || !payload.isObject() || payload.size() != 1 || !payload.path(REF).isTextual()) {
            return;
        }
        String ref = payload.get(REF).asText();
        if (ref.startsWith("#")) {
            // Internal reference: already valid within the document
            return;
        }
        ResolvedReference resolved = resolver.resolveRef(ref, message);
        if (resolved != null) {
            inline(message, resolved);
        }
    }

    private void inline(AsyncApi2xMessage message, ResolvedReference resolved) {
        String mediaType = resolved.getMediaType();
        if (resolved.isText() && AsyncApiSchemaFormats.isProtobuf(mediaType)) {
            String proto = resolved.asText();
            message.setPayload(TextNode.valueOf(proto));
            message.setSchemaFormat(AsyncApiSchemaFormats.forProtobuf(message.getSchemaFormat(), proto));
        } else if (resolved.isJson() && AsyncApiSchemaFormats.isAvro(mediaType)) {
            message.setPayload(resolved.asJson());
            message.setSchemaFormat(AsyncApiSchemaFormats.forAvro(message.getSchemaFormat()));
        } else if (resolved.isJson()) {
            inlineIfSelfContained(message, resolved.asJson());
        } else if (resolved.isNode()) {
            inlineIfSelfContained(message, Library.writeNode(resolved.asNode()));
        }
    }

    private static void inlineIfSelfContained(AsyncApi2xMessage message, JsonNode schema) {
        if (schema != null && schema.findValue(REF) == null) {
            message.setPayload(schema);
        }
    }
}
