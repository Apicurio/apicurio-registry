package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;

import java.util.Set;

/**
 * Interface for finding external references in schema/API content.
 * Implementations should analyze the provided content and return a set of external references
 * that the content depends on.
 */
public interface ReferenceFinder {

    /**
     * Finds the set of external references in a piece of content.
     *
     * @param content the typed content to analyze for external references
     * @return a set of external references found in the content; never null, may be empty
     * @throws ReferenceFinderException if an error occurs while parsing or analyzing the content
     */
    Set<ExternalReference> findExternalReferences(TypedContent content);

}
