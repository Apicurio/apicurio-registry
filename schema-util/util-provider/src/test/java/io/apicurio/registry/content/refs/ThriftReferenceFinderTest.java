package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.thrift.content.refs.ThriftReferenceFinder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class ThriftReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for
     * {@link ThriftReferenceFinder#findExternalReferences(io.apicurio.registry.content.TypedContent)}.
     */
    @Test
    public void testFindExternalReferences() {
        TypedContent content = resourceToTypedContentHandle("thrift-with-refs.thrift");
        ThriftReferenceFinder finder = new ThriftReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(Set.of(new ExternalReference("shared/common.thrift"),
                new ExternalReference("shared/other.thrift")), foundReferences);
    }

    @Test
    public void testFindExternalReferences_NoIncludes() {
        TypedContent content = resourceToTypedContentHandle("thrift-no-refs.thrift");
        ThriftReferenceFinder finder = new ThriftReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertTrue(foundReferences.isEmpty());
    }

    @Test
    public void testFindExternalReferences_CppIncludeIgnored() {
        TypedContent content = resourceToTypedContentHandle("thrift-with-refs.thrift");
        ThriftReferenceFinder finder = new ThriftReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertFalse(foundReferences.contains(new ExternalReference("my_header.h")));
        Assertions.assertEquals(2, foundReferences.size());
    }

}
