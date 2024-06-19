package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class ProtobufReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for
     * {@link io.apicurio.registry.content.refs.AsyncApiReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)}.
     */
    @Test
    public void testFindExternalReferences() {
        TypedContent content = resourceToTypedContentHandle("protobuf-with-refs.proto");
        ProtobufReferenceFinder finder = new ProtobufReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(3, foundReferences.size());
        Assertions.assertEquals(Set.of(new ExternalReference("google/protobuf/timestamp.proto"),
                new ExternalReference("sample/table_info.proto"),
                new ExternalReference("sample/table_notification_type.proto")), foundReferences);
    }

}
