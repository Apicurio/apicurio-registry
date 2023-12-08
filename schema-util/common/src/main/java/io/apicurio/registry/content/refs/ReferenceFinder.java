package io.apicurio.registry.content.refs;

import java.util.Set;

import io.apicurio.registry.content.ContentHandle;

public interface ReferenceFinder {

    /**
     * Finds the set of external references in a piece of content.
     * @param content
     */
    public Set<ExternalReference> findExternalReferences(ContentHandle content);

}
