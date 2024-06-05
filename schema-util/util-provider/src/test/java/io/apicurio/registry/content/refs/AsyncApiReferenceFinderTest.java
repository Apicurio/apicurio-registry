package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class AsyncApiReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for {@link io.apicurio.registry.content.refs.AsyncApiReferenceFinder#findExternalReferences(TypedContent)}
     */
    @Test
    public void testFindExternalReferences() {
        TypedContent content = resourceToTypedContentHandle("asyncapi-with-refs.json");
        AsyncApiReferenceFinder finder = new AsyncApiReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(2, foundReferences.size());
        Assertions.assertEquals(Set.of(
                new JsonPointerExternalReference("./TradeKey.avsc"), 
                new JsonPointerExternalReference("./common-types.json#/components/schemas/User")), foundReferences);
    }

}
