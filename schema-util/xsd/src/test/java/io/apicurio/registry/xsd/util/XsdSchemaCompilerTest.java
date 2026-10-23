package io.apicurio.registry.xsd.util;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testExternalUrlsNeverFetched() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(500);
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/x.xsd";
            String evil = """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:include schemaLocation="file:///etc/passwd"/>
                      <xs:import namespace="urn:x" schemaLocation="%s"/>
                    </xs:schema>
                    """.formatted(url);
            XsdSchemaCompiler.UnresolvedReferencesException ex = Assertions.assertThrows(
                    XsdSchemaCompiler.UnresolvedReferencesException.class,
                    () -> XsdSchemaCompiler.compile(src(evil), Map.of()));
            Assertions.assertEquals(List.of("file:///etc/passwd", url), List.copyOf(ex.getLocations()));
            Assertions.assertThrows(SocketTimeoutException.class, server::accept);
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testDtdInReferencedSchemaNotReportedOrFetched() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(500);
            String dtdUrl = "http://127.0.0.1:" + server.getLocalPort() + "/x.dtd";
            String withDtd = """
                    <?xml version="1.0"?>
                    <!DOCTYPE xs:schema SYSTEM "%s">
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:common">
                      <xs:complexType name="Address"><xs:sequence>
                        <xs:element name="street" type="xs:string"/></xs:sequence></xs:complexType>
                    </xs:schema>
                    """.formatted(dtdUrl);
            MappedReferenceResourceResolver resolver = new MappedReferenceResourceResolver(
                    Map.of("common.xsd", xml(withDtd)));
            SchemaFactory factory = SchemaFactoryAccessor.newSchemaFactory();
            List<String[]> seen = new ArrayList<>();
            factory.setResourceResolver((type, ns, publicId, systemId, baseUri) -> {
                seen.add(new String[] { type, systemId });
                return resolver.resolveResource(type, ns, publicId, systemId, baseUri);
            });
            try {
                factory.newSchema(src(ORDER));
            } catch (SAXException e) {
                // a parse error after resolution is acceptable; resolver consultation is asserted below
            }
            Assertions.assertTrue(seen.stream().anyMatch(t -> dtdUrl.equals(t[1])
                    && !XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(t[0])),
                    "resolver was not consulted for the DTD as a non-schema resource");
            Assertions.assertFalse(resolver.getUnresolved().contains(dtdUrl));
            Assertions.assertEquals(Set.of(), resolver.getUnresolved());
            Assertions.assertThrows(SocketTimeoutException.class, server::accept);
        }
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void testEmptyMappedContentNeverFetched() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(500);
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/x.xsd";
            String root = """
                    <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                      <xs:import namespace="urn:x" schemaLocation="%s"/>
                    </xs:schema>
                    """.formatted(url);
            Assertions.assertThrows(SAXException.class,
                    () -> XsdSchemaCompiler.compile(src(root), Map.of(url, xml(""))));
            Assertions.assertThrows(SocketTimeoutException.class, server::accept);
        }
    }

    @Test
    public void testDiamondInclude() {
        String root = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="b.xsd"/>
                  <xs:include schemaLocation="c.xsd"/>
                  <xs:element name="root" type="BType"/>
                </xs:schema>
                """;
        String b = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="c.xsd"/>
                  <xs:complexType name="BType"><xs:sequence>
                    <xs:element name="c" type="CType"/></xs:sequence></xs:complexType>
                </xs:schema>
                """;
        String c = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:simpleType name="CType"><xs:restriction base="xs:string"/></xs:simpleType>
                </xs:schema>
                """;
        Assertions.assertDoesNotThrow(() -> XsdSchemaCompiler.compile(src(root),
                Map.of("b.xsd", xml(b), "c.xsd", xml(c))));
    }

    @Test
    public void testSharedFactoryNotContaminated() throws Exception {
        XsdSchemaCompiler.compile(src(ORDER), Map.of("common.xsd", xml(COMMON)));
        Assertions.assertNull(SchemaFactoryAccessor.getSchemaFactory().getResourceResolver());
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
