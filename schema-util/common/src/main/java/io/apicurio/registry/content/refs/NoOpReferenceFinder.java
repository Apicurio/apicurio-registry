package io.apicurio.registry.content.refs;

import java.util.Collections;
import java.util.Set;

import io.apicurio.registry.content.ContentHandle;


public class NoOpReferenceFinder implements ReferenceFinder {
    
    public static final ReferenceFinder INSTANCE = new NoOpReferenceFinder();

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(ContentHandle content) {
        return Collections.emptySet();
    }

}
