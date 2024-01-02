package io.apicurio.registry.content.refs;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;

public class AsyncApiReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for {@link io.apicurio.registry.content.refs.AsyncApiReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)}.
     */
    @Test
    public void testFindExternalReferences() {
        ContentHandle content = resourceToContentHandle("asyncapi-with-refs.json");
        AsyncApiReferenceFinder finder = new AsyncApiReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(2, foundReferences.size());
        Assertions.assertEquals(Set.of(
                new JsonPointerExternalReference("./TradeKey.avsc"), 
                new JsonPointerExternalReference("./common-types.json#/components/schemas/User")), foundReferences);
    }

}
