package io.apicurio.registry.xsd.content.extract;

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
 * Extracts structured elements from XSD content for search indexing. Parses the XML Schema document and
 * extracts top-level elements, complexTypes, simpleTypes, attributes, attributeGroups, and groups.
 */
public class XsdStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(XsdStructuredContentExtractor.class);

    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            Document doc = DocumentBuilderAccessor.getDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.content().getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();

            if (root == null || !XSD_NS.equals(root.getNamespaceURI())
                    || !"schema".equals(root.getLocalName())) {
                return Collections.emptyList();
            }

            List<StructuredElement> elements = new ArrayList<>();
            extractTopLevelChildren(root, "element", elements);
            extractTopLevelChildren(root, "complexType", elements);
            extractTopLevelChildren(root, "simpleType", elements);
            extractTopLevelChildren(root, "attribute", elements);
            extractTopLevelChildren(root, "attributeGroup", elements);
            extractTopLevelChildren(root, "group", elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from XSD: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts named children of a given XSD local name that are direct children of the schema root element.
     */
    private void extractTopLevelChildren(Element root, String localName,
            List<StructuredElement> elements) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement && XSD_NS.equals(childElement.getNamespaceURI())
                    && localName.equals(childElement.getLocalName())) {
                String name = childElement.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    elements.add(new StructuredElement(localName, name));
                }
            }
        }
    }
}
