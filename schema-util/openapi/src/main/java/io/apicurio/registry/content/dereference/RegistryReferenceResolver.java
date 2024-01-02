package io.apicurio.registry.content.dereference;

import java.util.Map;

import io.apicurio.datamodels.Library;
import io.apicurio.datamodels.models.Document;
import io.apicurio.datamodels.models.Node;
import io.apicurio.datamodels.refs.LocalReferenceResolver;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.refs.JsonPointerExternalReference;

public class RegistryReferenceResolver extends LocalReferenceResolver {
    
    private final Map<String, ContentHandle> resolvedReferences;

    /**
     * Constructor.
     * @param resolvedReferences
     */
    public RegistryReferenceResolver(Map<String, ContentHandle> resolvedReferences) {
        this.resolvedReferences = resolvedReferences;
    }

    /**
     * @see io.apicurio.datamodels.refs.IReferenceResolver#resolveRef(java.lang.String, io.apicurio.datamodels.models.Node)
     */
    @Override
    public Node resolveRef(String reference, Node from) {
        if (resolvedReferences.containsKey(reference)) {
            ContentHandle resolvedRefContent = resolvedReferences.get(reference);
            Document resolvedRefDoc = Library.readDocumentFromJSONString(resolvedRefContent.content());
            JsonPointerExternalReference ref = new JsonPointerExternalReference(reference);
            return super.resolveRef(ref.getComponent(), resolvedRefDoc);
            // TODO if we find a Node, make sure to modify it by updating all of its $ref values to point to appropriate locations
        }
        
        
        // TODO handle recursive $ref values (refs from refs)
        
        // Cannot resolve the ref, return null.
        return null;
    }

}
