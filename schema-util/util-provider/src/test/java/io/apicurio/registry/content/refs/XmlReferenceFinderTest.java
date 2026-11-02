package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.xml.content.refs.XmlReferenceFinder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class XmlReferenceFinderTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testFindXsdReferences() {
        Set<ExternalReference> refs = new XmlReferenceFinder()
                .findExternalReferences(resourceToTypedContentHandle("xsd-with-refs.xsd"));
        Assertions.assertEquals(Set.of(new ExternalReference("common.xsd"),
                new ExternalReference("order-parts.xsd")), refs);
        ExternalReference common = refs.stream().filter(r -> r.getFullReference().equals("common.xsd"))
                .findFirst().orElseThrow();
        Assertions.assertEquals("common.xsd", common.getResource());
        Assertions.assertEquals("urn:common", common.getComponent());
    }

    @Test
    public void testFindWsdlReferences() {
        Set<ExternalReference> refs = new XmlReferenceFinder()
                .findExternalReferences(resourceToTypedContentHandle("wsdl-with-refs.wsdl"));
        Assertions.assertEquals(Set.of(new ExternalReference("base.wsdl"),
                new ExternalReference("order.xsd")), refs);
    }

    @Test
    public void testMalformedThrowsReferenceFinderException() {
        TypedContent bad = TypedContent.create(ContentHandle.create("<xs:schema"), ContentTypes.APPLICATION_XML);
        Assertions.assertThrows(ReferenceFinderException.class,
                () -> new XmlReferenceFinder().findExternalReferences(bad));
    }
}
