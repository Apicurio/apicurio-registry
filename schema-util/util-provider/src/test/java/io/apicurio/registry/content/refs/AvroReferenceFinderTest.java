package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;
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
        TypedContent content = resourceToTypedContentHandle("avro-with-refs.avsc");
        AvroReferenceFinder finder = new AvroReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(2, foundReferences.size());
        Assertions.assertEquals(Set.of(new ExternalReference("com.kubetrade.schema.trade.TradeKey"),
                new ExternalReference("com.kubetrade.schema.trade.TradeValue")), foundReferences);
    }

    /**
     * Test that relative type names (short names without namespace) are properly resolved
     * to their fully qualified names using the enclosing namespace.
     * This addresses issue #6710.
     */
    @Test
    public void testFindExternalReferencesWithRelativeTypeNames() {
        TypedContent content = resourceToTypedContentHandle("avro-with-relative-refs.avsc");
        AvroReferenceFinder finder = new AvroReferenceFinder();
        Set<ExternalReference> foundReferences = finder.findExternalReferences(content);
        Assertions.assertNotNull(foundReferences);
        Assertions.assertEquals(2, foundReferences.size());
        // The reference finder should qualify relative type names with the enclosing namespace
        // "TradeKey" should be resolved to "com.kubetrade.schema.trade.TradeKey"
        // "TradeValue" should be resolved to "com.kubetrade.schema.trade.TradeValue"
        Assertions.assertEquals(Set.of(new ExternalReference("com.kubetrade.schema.trade.TradeKey"),
                new ExternalReference("com.kubetrade.schema.trade.TradeValue")), foundReferences);
    }

}
