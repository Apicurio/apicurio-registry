package io.apicurio.registry.wsdl.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.xml.rules.validity.XmlContentValidator;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.wsdl.util.WSDLReaderAccessor;
import io.apicurio.registry.xml.refs.XmlReferenceKind;
import io.apicurio.registry.xml.util.DocumentBuilderAccessor;
import io.apicurio.registry.xsd.util.MappedReferenceResourceResolver;
import io.apicurio.registry.xsd.util.XsdSchemaCompiler;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WsdlContentValidator extends XmlContentValidator {

    private static final String INLINE_PREFIX = MappedReferenceResourceResolver.INLINE_ID_PREFIX;

    /**
     * Constructor.
     */
    public WsdlContentValidator() {
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validate(ValidityLevel, TypedContent, Map)
     */
    @Override
    public void validate(ValidityLevel level, TypedContent content,
            Map<String, TypedContent> resolvedReferences) throws RuleViolationException {
        if (level == ValidityLevel.SYNTAX_ONLY || level == ValidityLevel.FULL) {
            try (InputStream stream = content.getContent().stream()) {
                Document wsdlDoc = DocumentBuilderAccessor.getDocumentBuilder().parse(stream);
                if (level == ValidityLevel.FULL) {
                    // validate that its a valid schema
                    WSDLReaderAccessor.getWSDLReader().readWSDL(null, wsdlDoc);
                    Element root = wsdlDoc.getDocumentElement();
                    if (root != null && XmlReferenceKind.WSDL11_NS.equals(root.getNamespaceURI())) {
                        compileEmbeddedSchemas(root, resolvedReferences);
                    }
                }
            } catch (XsdSchemaCompiler.UnresolvedReferencesException e) {
                throw XsdSchemaCompiler.toRuleViolation(level, e.getLocations(), "WSDL");
            } catch (Exception e) {
                throw new RuleViolationException("Syntax violation for WSDL Schema artifact.",
                        RuleType.VALIDITY, level.name(), e);
            }
        }
    }

    /**
     * Compiles every xs:schema element directly under wsdl:types as one schema set. JAXP only processes the
     * first source per target namespace, so the embedded schemas are grouped by target namespace: each
     * namespace gets an in-memory wrapper schema that xs:includes every block of that namespace, and a single
     * synthetic no-namespace root imports every wrapper (and directly includes no-namespace blocks). Each
     * block carries all namespace declarations that were in scope in the WSDL document.
     */
    private static void compileEmbeddedSchemas(Element root, Map<String, TypedContent> resolvedReferences)
            throws ParserConfigurationException, TransformerException, SAXException,
            XsdSchemaCompiler.UnresolvedReferencesException {
        Map<String, String> inlineDocuments = new HashMap<>();
        Map<String, List<String>> blocksByNamespace = new LinkedHashMap<>();
        List<String> noNamespaceBlocks = new ArrayList<>();
        int blockCount = 0;
        for (Element types : childElements(root, XmlReferenceKind.WSDL11_NS, "types")) {
            for (Element schema : childElements(types, XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema")) {
                Document doc = DocumentBuilderAccessor.getDocumentBuilder().newDocument();
                Element clone = (Element) doc.importNode(schema, true);
                doc.appendChild(clone);
                copyInScopeNamespaces(schema, clone);
                String blockId = INLINE_PREFIX + "schema" + blockCount;
                blockCount++;
                inlineDocuments.put(blockId, serialize(doc));
                String ns = schema.getAttribute("targetNamespace");
                if (ns.isEmpty()) {
                    noNamespaceBlocks.add(blockId);
                } else {
                    blocksByNamespace.computeIfAbsent(ns, k -> new ArrayList<>()).add(blockId);
                }
            }
        }
        if (blockCount == 0) {
            return;
        }
        Map<String, String> inlineNamespaces = new LinkedHashMap<>();
        StringBuilder rootSchema = new StringBuilder("<xs:schema xmlns:xs=\"")
                .append(XMLConstants.W3C_XML_SCHEMA_NS_URI).append("\">");
        for (Map.Entry<String, List<String>> group : blocksByNamespace.entrySet()) {
            String wrapperId = INLINE_PREFIX + "ns" + inlineNamespaces.size();
            inlineNamespaces.put(group.getKey(), wrapperId);
            StringBuilder wrapper = new StringBuilder("<xs:schema xmlns:xs=\"")
                    .append(XMLConstants.W3C_XML_SCHEMA_NS_URI).append("\" targetNamespace=\"")
                    .append(escape(group.getKey())).append("\">");
            group.getValue().forEach(id -> wrapper.append(include(id)));
            wrapper.append("</xs:schema>");
            inlineDocuments.put(wrapperId, wrapper.toString());
            rootSchema.append("<xs:import namespace=\"").append(escape(group.getKey()))
                    .append("\" schemaLocation=\"").append(wrapperId).append("\"/>");
        }
        noNamespaceBlocks.forEach(id -> rootSchema.append(include(id)));
        rootSchema.append("</xs:schema>");
        Source rootSource = new StreamSource(new StringReader(rootSchema.toString()),
                MappedReferenceResourceResolver.ROOT_SYSTEM_ID);
        XsdSchemaCompiler.compile(new Source[] { rootSource }, resolvedReferences, inlineDocuments,
                inlineNamespaces);
    }

    private static String include(String schemaLocation) {
        return "<xs:include schemaLocation=\"" + schemaLocation + "\"/>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static String serialize(Document doc) throws TransformerException {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    private static List<Element> childElements(Element parent, String ns, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && ns.equals(n.getNamespaceURI())
                    && localName.equals(n.getLocalName())) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private static void copyInScopeNamespaces(Element original, Element clone) {
        for (Node p = original.getParentNode(); p != null && p.getNodeType() == Node.ELEMENT_NODE;
                p = p.getParentNode()) {
            NamedNodeMap attrs = p.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                    continue;
                }
                // Nearest ancestor wins: only copy if not already declared on the clone.
                if (!clone.hasAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, attr.getLocalName())) {
                    clone.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, attr.getName(), attr.getValue());
                }
            }
        }
    }

    /**
     * @see io.apicurio.registry.rules.validity.ContentValidator#validateReferences(TypedContent, List)
     */
    @Override
    public void validateReferences(TypedContent content, List<ArtifactReference> references)
            throws RuleViolationException {
        validateLocatedReferences(content, references);
    }
}
