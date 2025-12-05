package io.apicurio.registry.content.refs;

import io.apicurio.registry.asyncapi.content.refs.AsyncApiReferenceFinder;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.protobuf.content.refs.ProtobufReferenceFinder;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class ProtobufReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for
     * {@link AsyncApiReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)}.
     */
    @Test
    public void testFindExternalReferences() {
        TypedContent content = resourceToTypedContentHandle("protobuf-with-refs.proto");
        ProtobufReferenceFinder finder = new ProtobufReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(Set.of(
                new ExternalReference("sample/table_info.proto"),
                new ExternalReference("sample/table_notification_type.proto")), foundReferences);
    }

}
