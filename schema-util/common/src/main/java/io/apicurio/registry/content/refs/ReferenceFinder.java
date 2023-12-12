package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.ContentHandle;

import java.util.Set;

public interface ReferenceFinder {

    /**
     * Finds the set of external references in a piece of content.
     * 
     * @param content
     */
    public Set<ExternalReference> findExternalReferences(ContentHandle content);

}
