package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class AvroReferenceFinderTest extends ArtifactUtilProviderTestBase {

    /**
     * Test method for
     * {@link io.apicurio.registry.content.refs.AsyncApiReferenceFinder#findExternalReferences(io.apicurio.registry.content.ContentHandle)}.
     */
    @Test
    public void testFindExternalReferences() {
        ContentHandle content = resourceToContentHandle("avro-with-refs.avsc");
        AvroReferenceFinder finder = new AvroReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(2, foundReferences.size());
        Assertions.assertEquals(Set.of(new ExternalReference("com.kubetrade.schema.trade.TradeKey"),
                new ExternalReference("com.kubetrade.schema.trade.TradeValue")), foundReferences);
    }

}
