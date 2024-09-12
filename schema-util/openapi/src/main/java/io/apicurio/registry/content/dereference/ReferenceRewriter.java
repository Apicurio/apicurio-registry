package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.models.Referenceable;
import io.apicurio.datamodels.models.asyncapi.AsyncApiMessage;
import io.apicurio.datamodels.models.asyncapi.v20.AsyncApi20Message;
import io.apicurio.datamodels.models.asyncapi.v21.AsyncApi21Message;
import io.apicurio.datamodels.models.asyncapi.v22.AsyncApi22Message;
import io.apicurio.datamodels.models.asyncapi.v23.AsyncApi23Message;
import io.apicurio.datamodels.models.asyncapi.v24.AsyncApi24Message;
import io.apicurio.datamodels.models.asyncapi.v25.AsyncApi25Message;
import io.apicurio.datamodels.models.asyncapi.v26.AsyncApi26Message;
import io.apicurio.datamodels.models.visitors.AllNodeVisitor;

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

        // Note: for now we have special handling of the payload because it's not yet fully modeled in the
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
        if (node instanceof AsyncApi20Message) {
            return ((AsyncApi20Message) node).getPayload();
        }
        if (node instanceof AsyncApi21Message) {
            return ((AsyncApi21Message) node).getPayload();
        }
        if (node instanceof AsyncApi22Message) {
            return ((AsyncApi22Message) node).getPayload();
        }
        if (node instanceof AsyncApi23Message) {
            return ((AsyncApi23Message) node).getPayload();
        }
        if (node instanceof AsyncApi24Message) {
            return ((AsyncApi24Message) node).getPayload();
        }
        if (node instanceof AsyncApi25Message) {
            return ((AsyncApi25Message) node).getPayload();
        }
        if (node instanceof AsyncApi26Message) {
            return ((AsyncApi26Message) node).getPayload();
        }
        return null;
    }

}
