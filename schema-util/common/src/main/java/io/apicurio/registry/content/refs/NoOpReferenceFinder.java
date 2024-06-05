package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;

import java.util.Collections;
import java.util.Set;

public class NoOpReferenceFinder implements ReferenceFinder {
    
    public static final ReferenceFinder INSTANCE = new NoOpReferenceFinder();

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent) 
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        return Collections.emptySet();
    }

}
