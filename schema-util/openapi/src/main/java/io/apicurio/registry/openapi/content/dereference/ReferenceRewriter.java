package io.apicurio.registry.openapi.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.asyncapi.AsyncApiMessage;
import io.apicurio.datamodels.models.asyncapi.AsyncApiMultiFormatSchema;
import io.apicurio.datamodels.models.asyncapi.v2x.AsyncApi2xMessage;
import io.apicurio.datamodels.models.asyncapi.v3x.AsyncApi3xMessage;
import io.apicurio.datamodels.models.union.MultiFormatSchemaSchemaUnion;
import io.apicurio.datamodels.models.visitors.AllNodeVisitor;
import io.apicurio.datamodels.util.ModelTypeUtil;

import java.util.Map;

/**
 * Rewrites all references in a data model using a map of replacements provided.
 */
public class ReferenceRewriter extends AllNodeVisitor {

    private final Map<String, String> referenceUrls;

    /**
     * Constructor.
     */
    public ReferenceRewriter(Map<String, String> referenceUrls) {
        this.referenceUrls = referenceUrls;
    }

    /**
     * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitNode(io.apicurio.datamodels.models.Node)
     */
    @Override
    protected void visitNode(Node node) {
        if (node instanceof Referenceable) {
            String $ref = ((Referenceable) node).get$ref();
            if ($ref != null && referenceUrls.containsKey($ref)) {
                ((Referenceable) node).set$ref(referenceUrls.get($ref));
            }
        }
    }

    /**
     * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitMessage(io.apicurio.datamodels.models.asyncapi.AsyncApiMessage)
     */
    @Override
    public void visitMessage(AsyncApiMessage node) {
        super.visitMessage(node);

        // Note: for now we have special handling of the AsyncAPI payload because it's not yet fully modeled in the
        // apicurio-data-models library.
        JsonNode payload = getPayload(node);
        if (payload != null && payload.hasNonNull("$ref")) {
            String $ref = payload.get("$ref").asText();
            if (referenceUrls.containsKey($ref)) {
                ((ObjectNode) payload).put("$ref", referenceUrls.get($ref));
            }
        }
    }

    private JsonNode getPayload(AsyncApiMessage node) {
        if (ModelTypeUtil.isAsyncApi2Model(node)) {
            return ((AsyncApi2xMessage) node).getPayload();
        } else if (ModelTypeUtil.isAsyncApiModel(node)) {
            MultiFormatSchemaSchemaUnion payload = ((AsyncApi3xMessage) node).getPayload();
            if (payload != null && payload.isMultiFormatSchema()) {
                AsyncApiMultiFormatSchema multiFormatSchema = payload.asMultiFormatSchema();
                if (multiFormatSchema != null && multiFormatSchema.getSchema() != null && multiFormatSchema.getSchema().isAny()) {
                    return multiFormatSchema.getSchema().asAny();
                }
            }
        }
        return null;
    }

}
