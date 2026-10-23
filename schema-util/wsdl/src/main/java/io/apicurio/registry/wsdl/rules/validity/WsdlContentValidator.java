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

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WsdlContentValidator extends XmlContentValidator {

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
                        Source[] sources = extractEmbeddedSchemas(root);
                        if (sources.length > 0) {
                            XsdSchemaCompiler.compile(sources, resolvedReferences);
                        }
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
     * Collects every xs:schema element directly under wsdl:types and turns each into a standalone schema
     * source carrying all namespace declarations that were in scope in the WSDL document.
     */
    private static Source[] extractEmbeddedSchemas(Element root) throws ParserConfigurationException {
        List<Source> sources = new ArrayList<>();
        for (Element types : childElements(root, XmlReferenceKind.WSDL11_NS, "types")) {
            for (Element schema : childElements(types, XMLConstants.W3C_XML_SCHEMA_NS_URI, "schema")) {
                Document doc = DocumentBuilderAccessor.getDocumentBuilder().newDocument();
                Element clone = (Element) doc.importNode(schema, true);
                doc.appendChild(clone);
                copyInScopeNamespaces(schema, clone);
                sources.add(new DOMSource(doc,
                        MappedReferenceResourceResolver.ROOT_SYSTEM_ID + "#schema" + sources.size()));
            }
        }
        return sources.toArray(new Source[0]);
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
