package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.validity.ArtifactUtilProviderTestBase;
import io.apicurio.registry.xml.content.dereference.XmlReferenceDereferencer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class XmlReferenceDereferencerTest extends ArtifactUtilProviderTestBase {

    private static final String URL =
            "https://registry.example.com/apis/registry/v3/groups/g/artifacts/common/versions/1/content?references=REWRITE";

    @Test
    public void testRewriteReferences() {
        TypedContent content = resourceToTypedContentHandle("xsd-to-rewrite.xsd");
        TypedContent result = new XmlReferenceDereferencer().rewriteReferences(content,
                Map.of("common.xsd", URL));
        Assertions.assertEquals(resourceToString("expected-testRewrite-xsd.xsd"),
                result.getContent().content());
        Assertions.assertEquals(content.getContentType(), result.getContentType());
    }

    @Test
    public void testRewriteReferencesNoMatchReturnsOriginal() {
        TypedContent content = resourceToTypedContentHandle("xsd-to-rewrite.xsd");
        Assertions.assertSame(content, new XmlReferenceDereferencer().rewriteReferences(content,
                Map.of("other.xsd", URL)));
        Assertions.assertSame(content, new XmlReferenceDereferencer().rewriteReferences(content, Map.of()));
    }

    @Test
    public void testDereferenceNotSupported() {
        TypedContent content = resourceToTypedContentHandle("xsd-to-rewrite.xsd");
        DereferencingNotSupportedException ex = Assertions.assertThrows(DereferencingNotSupportedException.class,
                () -> new XmlReferenceDereferencer().dereference(content, Map.of()));
        Assertions.assertEquals(
                "Artifact types XSD and WSDL do not support references=DEREFERENCE; use references=REWRITE instead.",
                ex.getMessage());
    }
}
