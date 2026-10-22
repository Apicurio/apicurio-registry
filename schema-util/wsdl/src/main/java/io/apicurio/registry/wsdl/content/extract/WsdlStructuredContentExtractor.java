package io.apicurio.registry.wsdl.content.extract;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.xml.util.DocumentBuilderAccessor;

/**
 * Extracts structured elements from WSDL content for search indexing. Parses the WSDL document and extracts
 * services, ports, bindings, portTypes, operations, messages, and embedded XSD types (elements, complexTypes,
 * simpleTypes).
 */
public class WsdlStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(WsdlStructuredContentExtractor.class);

    private static final String WSDL_NS = "http://schemas.xmlsoap.org/wsdl/";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            Document doc = DocumentBuilderAccessor.getDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.content().getBytes(StandardCharsets.UTF_8)));
            List<StructuredElement> elements = new ArrayList<>();

            extractServices(doc, elements);
            extractBindings(doc, elements);
            extractPortTypes(doc, elements);
            extractMessages(doc, elements);
            extractEmbeddedSchemaTypes(doc, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from WSDL: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts service names and their port names from wsdl:service elements.
     */
    private void extractServices(Document doc, List<StructuredElement> elements) {
        NodeList services = doc.getElementsByTagNameNS(WSDL_NS, "service");
        for (int i = 0; i < services.getLength(); i++) {
            Element service = (Element) services.item(i);
            addNamedElement(service, "service", elements);

            NodeList ports = service.getElementsByTagNameNS(WSDL_NS, "port");
            for (int j = 0; j < ports.getLength(); j++) {
                addNamedElement((Element) ports.item(j), "port", elements);
            }
        }
    }

    /**
     * Extracts binding names from wsdl:binding elements.
     */
    private void extractBindings(Document doc, List<StructuredElement> elements) {
        NodeList bindings = doc.getElementsByTagNameNS(WSDL_NS, "binding");
        for (int i = 0; i < bindings.getLength(); i++) {
            addNamedElement((Element) bindings.item(i), "binding", elements);
        }
    }

    /**
     * Extracts portType names and their operation names from wsdl:portType elements.
     */
    private void extractPortTypes(Document doc, List<StructuredElement> elements) {
        NodeList portTypes = doc.getElementsByTagNameNS(WSDL_NS, "portType");
        for (int i = 0; i < portTypes.getLength(); i++) {
            Element portType = (Element) portTypes.item(i);
            addNamedElement(portType, "portType", elements);

            NodeList operations = portType.getElementsByTagNameNS(WSDL_NS, "operation");
            for (int j = 0; j < operations.getLength(); j++) {
                addNamedElement((Element) operations.item(j), "operation", elements);
            }
        }
    }

    /**
     * Extracts message names from wsdl:message elements.
     */
    private void extractMessages(Document doc, List<StructuredElement> elements) {
        NodeList messages = doc.getElementsByTagNameNS(WSDL_NS, "message");
        for (int i = 0; i < messages.getLength(); i++) {
            addNamedElement((Element) messages.item(i), "message", elements);
        }
    }

    /**
     * Extracts XSD element, complexType, and simpleType names from embedded schemas within wsdl:types.
     */
    private void extractEmbeddedSchemaTypes(Document doc, List<StructuredElement> elements) {
        NodeList schemas = doc.getElementsByTagNameNS(XSD_NS, "schema");
        for (int i = 0; i < schemas.getLength(); i++) {
            Element schema = (Element) schemas.item(i);
            extractTopLevelSchemaChildren(schema, "element", elements);
            extractTopLevelSchemaChildren(schema, "complexType", elements);
            extractTopLevelSchemaChildren(schema, "simpleType", elements);
        }
    }

    /**
     * Extracts named children of a given XSD local name that are direct children of the schema element.
     */
    private void extractTopLevelSchemaChildren(Element schema, String localName,
            List<StructuredElement> elements) {
        NodeList children = schema.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && XSD_NS.equals(childElement.getNamespaceURI())
                    && localName.equals(childElement.getLocalName())) {
                addNamedElement(childElement, localName, elements);
            }
        }
    }

    /**
     * Adds a StructuredElement for the given DOM element if it has a "name" attribute.
     */
    private void addNamedElement(Element element, String kind, List<StructuredElement> elements) {
        String name = element.getAttribute("name");
        if (name != null && !name.isEmpty()) {
            elements.add(new StructuredElement(kind, name));
        }
    }
}
