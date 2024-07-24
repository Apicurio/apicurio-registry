package io.apicurio.registry.content.refs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.TraverserDirection;
import io.apicurio.datamodels.models.Document;
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
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of a reference finder that uses Apicurio Data Models and so supports any specification
 * contained therein. Parses the document, finds all $refs, converts them to external references, and returns
 * them.
 */
public abstract class AbstractDataModelsReferenceFinder implements ReferenceFinder {

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            JsonNode node = ContentTypeUtil.parseJsonOrYaml(content);
            Document doc = Library.readDocument((ObjectNode) node);

            // Find all the $refs
            RefFinderVisitor visitor = new RefFinderVisitor();
            Library.visitTree(doc, visitor, TraverserDirection.down);

            // Convert to ExternalReference and filter.
            return visitor.allReferences.stream().map(ref -> new JsonPointerExternalReference(ref))
                    .filter(ref -> ref.getResource() != null).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Visitor that will visit every node looking for "$ref" properties.
     */
    private static class RefFinderVisitor extends AllNodeVisitor {

        public Set<String> allReferences = new HashSet<>();

        /**
         * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitNode(io.apicurio.datamodels.models.Node)
         */
        @Override
        protected void visitNode(Node node) {
            if (node instanceof Referenceable) {
                String ref = ((Referenceable) node).get$ref();
                if (ref != null && !ref.trim().isEmpty()) {
                    allReferences.add(ref);
                }
            }
        }

        /**
         * @see io.apicurio.datamodels.models.visitors.AllNodeVisitor#visitMessage(io.apicurio.datamodels.models.asyncapi.AsyncApiMessage)
         */
        @Override
        public void visitMessage(AsyncApiMessage node) {
            // Note: special handling of message payloads because data-models doesn't fully model the payload
            // yet.
            JsonNode payload = getPayload(node);
            if (payload != null && payload.has("$ref") && !payload.get("$ref").isNull()) {
                String ref = payload.get("$ref").asText();
                allReferences.add(ref);
            }
            super.visitMessage(node);
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

}
