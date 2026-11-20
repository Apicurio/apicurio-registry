package io.apicurio.registry.xml.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.xml.refs.XmlReferenceLocator;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Finds external references (schemaLocation / location) in XSD and WSDL content. The reference name is
 * the literal location value; the component carries the target namespace when present.
 */
public class XmlReferenceFinder implements ReferenceFinder {

    /**
     * @see ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            return XmlReferenceLocator.locate(content.getContent().content()).stream()
                    .map(r -> new ExternalReference(r.location(), r.location(), r.namespace()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (XmlReferenceLocator.LocatorException e) {
            throw new ReferenceFinderException("Failed to parse XML content to find references.", e);
        }
    }
}
