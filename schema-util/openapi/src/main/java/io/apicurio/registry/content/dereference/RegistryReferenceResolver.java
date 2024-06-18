package io.apicurio.registry.content.dereference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.refs.LocalReferenceResolver;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;
import io.apicurio.registry.content.util.ContentTypeUtil;

import java.io.IOException;
import java.util.Map;

public class RegistryReferenceResolver extends LocalReferenceResolver {

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
     * @see io.apicurio.datamodels.refs.IReferenceResolver#resolveRef(java.lang.String,
     *      io.apicurio.datamodels.models.Node)
     */
    @Override
    public Node resolveRef(String reference, Node from) {
        try {
            if (resolvedReferences.containsKey(reference)) {
                TypedContent resolvedRefContent = resolvedReferences.get(reference);
                JsonNode node = ContentTypeUtil.parseJsonOrYaml(resolvedRefContent);
                Document resolvedRefDoc = Library.readDocument((ObjectNode) node);
                JsonPointerExternalReference ref = new JsonPointerExternalReference(reference);
                return super.resolveRef(ref.getComponent(), resolvedRefDoc);
                // TODO if we find a Node, make sure to modify it by updating all of its $ref values to point
                // to appropriate locations
            }

            // TODO handle recursive $ref values (refs from refs)

            // Cannot resolve the ref, return null.
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
