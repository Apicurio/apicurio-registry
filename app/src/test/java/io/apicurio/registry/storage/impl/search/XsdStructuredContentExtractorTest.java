package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.xsd.content.extract.XsdStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XsdStructuredContentExtractorTest {

    private final XsdStructuredContentExtractor extractor = new XsdStructuredContentExtractor();

    @Test
    void testExtractElements() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/">
                  <xs:element name="Order" type="xs:string"/>
                  <xs:element name="Customer" type="xs:string"/>
                </xs:schema>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xsd));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("element") && e.name().equals("Order")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("element") && e.name().equals("Customer")));
    }

    @Test
    void testExtractComplexTypes() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/">
                  <xs:complexType name="OrderType">
                    <xs:sequence>
                      <xs:element name="id" type="xs:int"/>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:complexType name="CustomerType">
                    <xs:sequence>
                      <xs:element name="name" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:schema>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xsd));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("complexType") && e.name().equals("OrderType")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("complexType") && e.name().equals("CustomerType")));
        // Nested elements within complexTypes should NOT be extracted
        assertTrue(elements.stream()
                .noneMatch(e -> e.kind().equals("element") && e.name().equals("id")));
        assertTrue(elements.stream()
                .noneMatch(e -> e.kind().equals("element") && e.name().equals("name")));
    }

    @Test
    void testExtractSimpleTypes() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/">
                  <xs:simpleType name="OrderStatus">
                    <xs:restriction base="xs:string">
                      <xs:enumeration value="PENDING"/>
                      <xs:enumeration value="COMPLETED"/>
                    </xs:restriction>
                  </xs:simpleType>
                  <xs:simpleType name="Priority">
                    <xs:restriction base="xs:int"/>
                  </xs:simpleType>
                </xs:schema>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xsd));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("simpleType") && e.name().equals("OrderStatus")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("simpleType") && e.name().equals("Priority")));
    }

    @Test
    void testExtractAttributes() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/">
                  <xs:attribute name="version" type="xs:string"/>
                  <xs:attribute name="lang" type="xs:string"/>
                </xs:schema>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xsd));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("attribute") && e.name().equals("version")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("attribute") && e.name().equals("lang")));
    }

    @Test
    void testExtractGroups() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/">
                  <xs:group name="AddressGroup">
                    <xs:sequence>
                      <xs:element name="street" type="xs:string"/>
                      <xs:element name="city" type="xs:string"/>
                    </xs:sequence>
                  </xs:group>
                  <xs:attributeGroup name="CommonAttributes">
                    <xs:attribute name="id" type="xs:int"/>
                  </xs:attributeGroup>
                </xs:schema>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xsd));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("group") && e.name().equals("AddressGroup")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("attributeGroup") && e.name().equals("CommonAttributes")));
    }

    @Test
    void testComprehensiveExtraction() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/">
                  <xs:element name="Order" type="tns:OrderType"/>
                  <xs:complexType name="OrderType">
                    <xs:sequence>
                      <xs:element name="id" type="xs:int"/>
                    </xs:sequence>
                  </xs:complexType>
                  <xs:simpleType name="OrderStatus">
                    <xs:restriction base="xs:string"/>
                  </xs:simpleType>
                  <xs:attribute name="version" type="xs:string"/>
                  <xs:attributeGroup name="CommonAttributes">
                    <xs:attribute name="id" type="xs:int"/>
                  </xs:attributeGroup>
                  <xs:group name="AddressGroup">
                    <xs:sequence>
                      <xs:element name="street" type="xs:string"/>
                    </xs:sequence>
                  </xs:group>
                </xs:schema>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xsd));

        // Verify all element types are present
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("element")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("complexType")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("simpleType")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("attribute")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("attributeGroup")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("group")));
    }

    @Test
    void testInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not xml at all"));

        assertTrue(elements.isEmpty());
    }

    @Test
    void testEmptySchema() {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                           targetNamespace="http://example.com/">
                </xs:schema>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(xsd));

        assertEquals(0, elements.size());
    }
}
