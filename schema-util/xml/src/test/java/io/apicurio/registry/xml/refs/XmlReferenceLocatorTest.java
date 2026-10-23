package io.apicurio.registry.xml.refs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class XmlReferenceLocatorTest {

    @Test
    public void testXsdAllKindsCustomPrefix() {
        String xsd = """
                <?xml version="1.0"?>
                <s:schema xmlns:s="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:main">
                  <s:import namespace="urn:common" schemaLocation="common.xsd"/>
                  <s:import namespace="urn:nolocation"/>
                  <s:include schemaLocation="part.xsd"/>
                  <s:redefine schemaLocation="redef.xsd"/>
                  <s:override schemaLocation="over.xsd"/>
                  <s:element name="x" type="s:string"/>
                </s:schema>
                """;
        List<XmlReference> refs = XmlReferenceLocator.locate(xsd);
        Assertions.assertEquals(List.of(
                new XmlReference(XmlReferenceKind.XSD_IMPORT, "common.xsd", "urn:common"),
                new XmlReference(XmlReferenceKind.XSD_INCLUDE, "part.xsd", null),
                new XmlReference(XmlReferenceKind.XSD_REDEFINE, "redef.xsd", null),
                new XmlReference(XmlReferenceKind.XSD_OVERRIDE, "over.xsd", null)), refs);
    }

    @Test
    public void testWsdl11ImportAndEmbeddedSchemaImport() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:svc">
                  <import namespace="urn:base" location="base.wsdl"/>
                  <types>
                    <xsd:schema targetNamespace="urn:svc">
                      <xsd:import namespace="urn:order" schemaLocation="order.xsd"/>
                    </xsd:schema>
                  </types>
                </definitions>
                """;
        Assertions.assertEquals(List.of(
                new XmlReference(XmlReferenceKind.WSDL11_IMPORT, "base.wsdl", "urn:base"),
                new XmlReference(XmlReferenceKind.XSD_IMPORT, "order.xsd", "urn:order")),
                XmlReferenceLocator.locate(wsdl));
    }

    @Test
    public void testWsdl20ImportAndInclude() {
        String wsdl = """
                <description xmlns="http://www.w3.org/ns/wsdl" targetNamespace="urn:svc">
                  <import namespace="urn:a" location="a.wsdl"/>
                  <include location="b.wsdl"/>
                </description>
                """;
        Assertions.assertEquals(List.of(
                new XmlReference(XmlReferenceKind.WSDL20_IMPORT, "a.wsdl", "urn:a"),
                new XmlReference(XmlReferenceKind.WSDL20_INCLUDE, "b.wsdl", null)),
                XmlReferenceLocator.locate(wsdl));
    }

    @Test
    public void testDuplicatesCollapsedAndBlankSkipped() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="a.xsd"/>
                  <xs:include schemaLocation="a.xsd"/>
                  <xs:include schemaLocation="  "/>
                </xs:schema>
                """;
        Assertions.assertEquals(List.of(new XmlReference(XmlReferenceKind.XSD_INCLUDE, "a.xsd", null)),
                XmlReferenceLocator.locate(xsd));
    }

    @Test
    public void testUnrelatedNamespaceElementIgnored() {
        String xml = """
                <root xmlns:o="urn:other"><o:import location="x.xsd" schemaLocation="y.xsd"/></root>
                """;
        Assertions.assertEquals(List.of(), XmlReferenceLocator.locate(xml));
    }

    @Test
    public void testDoctypeRejected() {
        String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
        Assertions.assertThrows(XmlReferenceLocator.LocatorException.class,
                () -> XmlReferenceLocator.locate(xml));
    }
}
