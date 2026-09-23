package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.wsdl.content.extract.WsdlStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WsdlStructuredContentExtractorTest {

    private final WsdlStructuredContentExtractor extractor = new WsdlStructuredContentExtractor();

    @Test
    void testExtractServices() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             targetNamespace="http://example.com/">
                  <service name="OrderService">
                    <port name="OrderPort" binding="tns:OrderBinding"/>
                  </service>
                  <service name="InventoryService">
                    <port name="InventoryPort" binding="tns:InventoryBinding"/>
                  </service>
                </definitions>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(wsdl));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("service") && e.name().equals("OrderService")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("service") && e.name().equals("InventoryService")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("port") && e.name().equals("OrderPort")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("port") && e.name().equals("InventoryPort")));
    }

    @Test
    void testExtractPortTypes() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             targetNamespace="http://example.com/">
                  <portType name="OrderPortType">
                    <operation name="placeOrder"/>
                    <operation name="cancelOrder"/>
                  </portType>
                </definitions>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(wsdl));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("portType") && e.name().equals("OrderPortType")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("operation") && e.name().equals("placeOrder")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("operation") && e.name().equals("cancelOrder")));
    }

    @Test
    void testExtractMessages() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             targetNamespace="http://example.com/">
                  <message name="PlaceOrderRequest">
                    <part name="body" element="tns:Order"/>
                  </message>
                  <message name="PlaceOrderResponse">
                    <part name="body" element="tns:OrderConfirmation"/>
                  </message>
                </definitions>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(wsdl));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("message") && e.name().equals("PlaceOrderRequest")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("message") && e.name().equals("PlaceOrderResponse")));
    }

    @Test
    void testExtractBindings() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             targetNamespace="http://example.com/">
                  <binding name="OrderSoapBinding" type="tns:OrderPortType"/>
                  <binding name="InventorySoapBinding" type="tns:InventoryPortType"/>
                </definitions>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(wsdl));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("binding") && e.name().equals("OrderSoapBinding")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("binding") && e.name().equals("InventorySoapBinding")));
    }

    @Test
    void testExtractTypesSchemaElements() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                             targetNamespace="http://example.com/">
                  <types>
                    <xsd:schema targetNamespace="http://example.com/">
                      <xsd:element name="Order" type="tns:OrderType"/>
                      <xsd:complexType name="OrderType">
                        <xsd:sequence>
                          <xsd:element name="item" type="xsd:string"/>
                        </xsd:sequence>
                      </xsd:complexType>
                      <xsd:simpleType name="OrderStatus">
                        <xsd:restriction base="xsd:string"/>
                      </xsd:simpleType>
                    </xsd:schema>
                  </types>
                </definitions>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(wsdl));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("element") && e.name().equals("Order")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("complexType") && e.name().equals("OrderType")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("simpleType") && e.name().equals("OrderStatus")));
        // Nested element "item" within complexType should NOT be extracted as top-level
        assertTrue(elements.stream()
                .noneMatch(e -> e.kind().equals("element") && e.name().equals("item")));
    }

    @Test
    void testComprehensiveExtraction() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                             xmlns:tns="http://example.com/"
                             targetNamespace="http://example.com/">
                  <types>
                    <xsd:schema targetNamespace="http://example.com/">
                      <xsd:element name="Order" type="tns:OrderType"/>
                      <xsd:complexType name="OrderType">
                        <xsd:sequence>
                          <xsd:element name="id" type="xsd:int"/>
                        </xsd:sequence>
                      </xsd:complexType>
                    </xsd:schema>
                  </types>
                  <message name="PlaceOrderRequest"/>
                  <message name="PlaceOrderResponse"/>
                  <portType name="OrderPortType">
                    <operation name="placeOrder"/>
                  </portType>
                  <binding name="OrderSoapBinding" type="tns:OrderPortType"/>
                  <service name="OrderService">
                    <port name="OrderPort" binding="tns:OrderSoapBinding"/>
                  </service>
                </definitions>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(wsdl));

        // Verify all element types are present
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("service")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("port")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("binding")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("portType")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("operation")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("message")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("element")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("complexType")));
    }

    @Test
    void testInvalidContent() {
        List<StructuredElement> elements = extractor.extract(ContentHandle.create("not xml at all"));

        assertTrue(elements.isEmpty());
    }

    @Test
    void testEmptyDefinitions() {
        String wsdl = """
                <definitions xmlns="http://schemas.xmlsoap.org/wsdl/"
                             targetNamespace="http://example.com/">
                </definitions>
                """;

        List<StructuredElement> elements = extractor.extract(ContentHandle.create(wsdl));

        assertEquals(0, elements.size());
    }
}
