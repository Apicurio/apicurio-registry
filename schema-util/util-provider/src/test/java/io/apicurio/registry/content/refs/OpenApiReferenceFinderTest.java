package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class OpenApiReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for
     * {@link io.apicurio.registry.content.refs.AsyncApiReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)}.
     */
    @Test
    public void testFindExternalReferences() {
        TypedContent content = resourceToTypedContentHandle("openapi-with-refs.json");
        OpenApiReferenceFinder finder = new OpenApiReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(2, foundReferences.size());
        Assertions.assertEquals(
                Set.of(new JsonPointerExternalReference("./types/bar-types.json#/components/schemas/Bar"),
                        new JsonPointerExternalReference("./types/foo-types.json#/components/schemas/Foo")),
                foundReferences);
    }

}
