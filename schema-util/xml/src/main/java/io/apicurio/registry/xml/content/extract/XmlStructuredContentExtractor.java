package io.apicurio.registry.xml.content.extract;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.xml.util.DocumentBuilderAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts structured elements from XML content for search indexing. Parses the XML document and extracts
 * the root element name, namespace URIs, and direct child element names.
 */
public class XmlStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(XmlStructuredContentExtractor.class);

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            Document doc = DocumentBuilderAccessor.getDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.content().getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            if (root == null) {
                return Collections.emptyList();
            }

            List<StructuredElement> elements = new ArrayList<>();

            // Extract root element name
            String rootName = root.getLocalName() != null ? root.getLocalName() : root.getTagName();
            elements.add(new StructuredElement("element", rootName));

            // Extract namespaces
            Set<String> namespaces = new LinkedHashSet<>();
            collectNamespaces(root, namespaces);
            for (String ns : namespaces) {
                elements.add(new StructuredElement("namespace", ns));
            }

            // Extract direct child element names (deduplicated, preserving order)
            Set<String> childNames = new LinkedHashSet<>();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element childElement) {
                    String name = childElement.getLocalName() != null
                            ? childElement.getLocalName()
                            : childElement.getTagName();
                    childNames.add(name);
                }
            }
            for (String childName : childNames) {
                elements.add(new StructuredElement("element", childName));
            }

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from XML: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Collects namespace URIs from the given element and its descendants.
     */
    private void collectNamespaces(Element element, Set<String> namespaces) {
        String ns = element.getNamespaceURI();
        if (ns != null && !ns.isBlank()) {
            namespaces.add(ns);
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                collectNamespaces(childElement, namespaces);
            }
        }
    }
}
