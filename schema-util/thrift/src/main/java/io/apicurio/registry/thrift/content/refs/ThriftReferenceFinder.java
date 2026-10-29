package io.apicurio.registry.thrift.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinderException;
import io.apicurio.registry.thrift.idl.ThriftIdlParseException;
import io.apicurio.registry.thrift.idl.ThriftIdlParser;
import io.apicurio.registry.thrift.idl.ThriftIdlParser.ThriftDocument;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * An Apache Thrift implementation of a reference finder. Thrift IDL documents reference other Thrift IDL
 * documents via the <code>include</code> directive (e.g. <code>include "shared.thrift"</code>). The
 * <code>cpp_include</code> directive is a C++-specific code generation hint and is intentionally not
 * treated as a reference.
 */
public class ThriftReferenceFinder implements ReferenceFinder {

    /**
     * @see io.apicurio.registry.content.refs.ReferenceFinder#findExternalReferences(TypedContent)
     */
    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        try {
            ThriftDocument document = ThriftIdlParser.parse(content.getContent().content());
            return document.getIncludes().stream().map(ExternalReference::new)
                    .collect(Collectors.toSet());
        } catch (ThriftIdlParseException e) {
            throw new ReferenceFinderException("Error finding external references in a Thrift IDL file.",
                    e);
        }
    }

}
