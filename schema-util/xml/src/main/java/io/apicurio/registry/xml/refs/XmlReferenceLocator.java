package io.apicurio.registry.xml.refs;

import io.apicurio.registry.xml.util.DocumentBuilderAccessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Locates references (imports, includes, ...) in XSD and WSDL documents.
 */
public final class XmlReferenceLocator {

    private XmlReferenceLocator() {
    }

    /**
     * Thrown when the content cannot be parsed as XML.
     */
    public static class LocatorException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        LocatorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parses the content and returns all references in document order, de-duplicated by location.
     * Elements without a non-blank location are skipped.
     *
     * @param xml the XML content
     * @return references, never null
     * @throws LocatorException if the content is not well-formed XML
     */
    public static List<XmlReference> locate(String xml) {
        Document doc;
        try {
            doc = DocumentBuilderAccessor.getDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new LocatorException("Content is not well-formed XML.", e);
        }
        List<XmlReference> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collect(doc.getDocumentElement(), result, seen);
        return result;
    }

    private static void collect(Element element, List<XmlReference> result, Set<String> seen) {
        Optional<XmlReferenceKind> kind = XmlReferenceKind.of(element.getNamespaceURI(), element.getLocalName());
        if (kind.isPresent()) {
            String location = element.getAttribute(kind.get().locationAttribute()).trim();
            if (!location.isEmpty() && seen.add(location)) {
                String ns = element.hasAttribute("namespace") ? element.getAttribute("namespace") : null;
                result.add(new XmlReference(kind.get(), location, ns));
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                collect((Element) child, result, seen);
            }
        }
    }
}
