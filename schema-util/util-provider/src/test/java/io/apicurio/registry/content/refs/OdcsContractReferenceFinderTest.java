package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class OdcsContractReferenceFinderTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testFindOdcsReferences() {
        TypedContent content = resourceToTypedContentHandle("odcs-with-refs.yaml");
        Set<ExternalReference> refs = new OdcsContractReferenceFinder().findExternalReferences(content);
        Assertions.assertEquals(Set.of(new ExternalReference("orders/OrderEvent:3"),
                new ExternalReference("shipping/ShippingEvent:1")), refs);
    }

    @Test
    public void testMalformedYamlReturnsEmptySet() {
        TypedContent content = resourceToTypedContentHandle("graphql-no-import.graphql");
        Assertions.assertTrue(new OdcsContractReferenceFinder().findExternalReferences(content).isEmpty());
    }
}
