package io.apicurio.registry.xsd.util;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XsdSchemaCompilerTest {

    private static final String COMMON = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:common">
              <xs:complexType name="Address"><xs:sequence>
                <xs:element name="street" type="xs:string"/></xs:sequence></xs:complexType>
            </xs:schema>
            """;
    private static final String ORDER = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:c="urn:common"
                       targetNamespace="urn:order">
              <xs:import namespace="urn:common" schemaLocation="common.xsd"/>
              <xs:element name="order" type="c:Address"/>
            </xs:schema>
            """;
    private static final String TOP = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:o="urn:order" targetNamespace="urn:top">
              <xs:import namespace="urn:order" schemaLocation="order.xsd"/>
              <xs:element name="top"><xs:complexType><xs:sequence>
                <xs:element ref="o:order"/></xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

    private static TypedContent xml(String s) {
        return TypedContent.create(ContentHandle.create(s), ContentTypes.APPLICATION_XML);
    }

    private static Source[] src(String s) {
        return new Source[] { new StreamSource(new StringReader(s), MappedReferenceResourceResolver.ROOT_SYSTEM_ID) };
    }

    @Test
    public void testMappedImportCompiles() {
        Assertions.assertDoesNotThrow(() -> XsdSchemaCompiler.compile(src(ORDER), Map.of("common.xsd", xml(COMMON))));
    }

    @Test
    public void testNestedImportsResolveFromFlatMap() {
        Assertions.assertDoesNotThrow(() -> XsdSchemaCompiler.compile(src(TOP),
                Map.of("order.xsd", xml(ORDER), "common.xsd", xml(COMMON))));
    }

    @Test
    public void testUnmappedImportReported() {
        XsdSchemaCompiler.UnresolvedReferencesException ex = Assertions.assertThrows(
                XsdSchemaCompiler.UnresolvedReferencesException.class,
                () -> XsdSchemaCompiler.compile(src(ORDER), Map.of()));
        Assertions.assertEquals(List.of("common.xsd"), List.copyOf(ex.getLocations()));
    }

    @Test
    public void testExternalUrlsNeverFetched() {
        String evil = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="file:///etc/passwd"/>
                  <xs:import namespace="urn:x" schemaLocation="http://127.0.0.1:1/x.xsd"/>
                </xs:schema>
                """;
        XsdSchemaCompiler.UnresolvedReferencesException ex = Assertions.assertThrows(
                XsdSchemaCompiler.UnresolvedReferencesException.class,
                () -> XsdSchemaCompiler.compile(src(evil), Map.of()));
        Assertions.assertEquals(List.of("file:///etc/passwd", "http://127.0.0.1:1/x.xsd"),
                List.copyOf(ex.getLocations()));
    }

    @Test
    public void testNamespaceOnlyImportNotReported() {
        String s = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:import namespace="urn:nothing"/>
                </xs:schema>
                """;
        Assertions.assertDoesNotThrow(() -> XsdSchemaCompiler.compile(src(s), Map.of()));
    }

    @Test
    public void testSystemIdsPassedToResolverAreLiteral() throws Exception {
        List<String[]> seen = new ArrayList<>();
        MappedReferenceResourceResolver delegate = new MappedReferenceResourceResolver(
                Map.of("order.xsd", xml(ORDER), "common.xsd", xml(COMMON)));
        SchemaFactory factory = SchemaFactoryAccessor.newSchemaFactory();
        factory.setResourceResolver((type, ns, publicId, systemId, baseUri) -> {
            seen.add(new String[] { systemId, baseUri });
            return delegate.resolveResource(type, ns, publicId, systemId, baseUri);
        });
        factory.newSchema(src(TOP));
        Assertions.assertEquals(2, seen.size());
        Assertions.assertEquals("order.xsd", seen.get(0)[0]);
        Assertions.assertEquals(MappedReferenceResourceResolver.ROOT_SYSTEM_ID, seen.get(0)[1]);
        Assertions.assertEquals("common.xsd", seen.get(1)[0]);
        // Xerces absolutizes the *baseURI* of a resolved input (against user.dir), but the systemId
        // passed for nested imports stays literal, so lookup by literal systemId is sufficient.
        Assertions.assertTrue(seen.get(1)[1].endsWith("/order.xsd"), seen.get(1)[1]);
        Assertions.assertTrue(delegate.getUnresolved().isEmpty());
    }

    @Test
    public void testSchemaErrorStillReportedWhenAllResolved() {
        String bad = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="a" type="xs:noSuchType"/>
                </xs:schema>
                """;
        Assertions.assertThrows(SAXException.class, () -> XsdSchemaCompiler.compile(src(bad), Map.of()));
    }
}
