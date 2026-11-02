package io.apicurio.registry.xsd.util;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.ls.LSInput;

import javax.xml.XMLConstants;
import java.util.Map;

public class MappedReferenceResourceResolverTest {

    private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private static final String WRAPPER_ID = "urn:apicurio:inline:ns0";
    private static final String WRAPPER = "<xs:schema xmlns:xs=\"" + XS + "\" targetNamespace=\"urn:a\"/>";

    @Test
    public void testNamespaceOnlyImportOfUnknownNamespaceReturnsNull() {
        MappedReferenceResourceResolver resolver = new MappedReferenceResourceResolver(Map.of(),
                Map.of(WRAPPER_ID, WRAPPER), Map.of("urn:a", WRAPPER_ID));
        Assertions.assertNull(resolver.resolveResource(XS, "urn:unknown", null, null, null));
        Assertions.assertTrue(resolver.getUnresolved().isEmpty());
    }

    @Test
    public void testNamespaceOnlyImportOfInlineNamespaceReturnsWrapper() {
        MappedReferenceResourceResolver resolver = new MappedReferenceResourceResolver(Map.of(),
                Map.of(WRAPPER_ID, WRAPPER), Map.of("urn:a", WRAPPER_ID));
        LSInput input = resolver.resolveResource(XS, "urn:a", null, null, null);
        Assertions.assertEquals(WRAPPER, input.getStringData());
        Assertions.assertEquals(WRAPPER_ID, input.getSystemId());
        Assertions.assertTrue(resolver.getUnresolved().isEmpty());
    }

    @Test
    public void testSyntheticIdsNeverUnresolvedAndNotShadowedByUserReferences() {
        TypedContent attacker = TypedContent.create(ContentHandle.create("<evil/>"),
                ContentTypes.APPLICATION_XML);
        MappedReferenceResourceResolver resolver = new MappedReferenceResourceResolver(
                Map.of(WRAPPER_ID, attacker), Map.of(WRAPPER_ID, WRAPPER), Map.of("urn:a", WRAPPER_ID));
        LSInput input = resolver.resolveResource(XS, "urn:a", null, WRAPPER_ID, null);
        Assertions.assertEquals(WRAPPER, input.getStringData());
        LSInput missing = resolver.resolveResource(XS, null, null, "urn:apicurio:inline:schema9", null);
        Assertions.assertNotNull(missing);
        Assertions.assertTrue(resolver.getUnresolved().isEmpty());
    }

    @Test
    public void testUnmappedUserLocationStillUnresolved() {
        MappedReferenceResourceResolver resolver = new MappedReferenceResourceResolver(Map.of(),
                Map.of(WRAPPER_ID, WRAPPER), Map.of("urn:a", WRAPPER_ID));
        LSInput input = resolver.resolveResource(XS, "urn:x", null, "common.xsd", null);
        Assertions.assertNotNull(input);
        Assertions.assertEquals(1, resolver.getUnresolved().size());
        Assertions.assertTrue(resolver.getUnresolved().contains("common.xsd"));
    }
}
