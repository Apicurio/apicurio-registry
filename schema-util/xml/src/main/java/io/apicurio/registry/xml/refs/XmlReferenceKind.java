package io.apicurio.registry.xml.refs;

import java.util.Arrays;
import java.util.Optional;

/**
 * Kinds of XML elements that point to another document via a location attribute.
 */
public enum XmlReferenceKind {

    XSD_IMPORT(XmlReferenceKind.XSD_NS, "import", "schemaLocation"),
    XSD_INCLUDE(XmlReferenceKind.XSD_NS, "include", "schemaLocation"),
    XSD_REDEFINE(XmlReferenceKind.XSD_NS, "redefine", "schemaLocation"),
    XSD_OVERRIDE(XmlReferenceKind.XSD_NS, "override", "schemaLocation"),
    WSDL11_IMPORT(XmlReferenceKind.WSDL11_NS, "import", "location"),
    WSDL20_IMPORT(XmlReferenceKind.WSDL20_NS, "import", "location"),
    WSDL20_INCLUDE(XmlReferenceKind.WSDL20_NS, "include", "location");

    public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";
    public static final String WSDL11_NS = "http://schemas.xmlsoap.org/wsdl/";
    public static final String WSDL20_NS = "http://www.w3.org/ns/wsdl";

    private final String namespaceUri;
    private final String localName;
    private final String locationAttribute;

    XmlReferenceKind(String namespaceUri, String localName, String locationAttribute) {
        this.namespaceUri = namespaceUri;
        this.localName = localName;
        this.locationAttribute = locationAttribute;
    }

    /**
     * @return the namespace URI of the element
     */
    public String namespaceUri() {
        return namespaceUri;
    }

    /**
     * @return the local name of the element
     */
    public String localName() {
        return localName;
    }

    /**
     * @return the (unqualified) attribute holding the referenced location
     */
    public String locationAttribute() {
        return locationAttribute;
    }

    /**
     * Finds the kind matching an element's namespace URI and local name.
     *
     * @param nsUri element namespace URI (may be null)
     * @param localName element local name
     * @return the matching kind, or empty
     */
    public static Optional<XmlReferenceKind> of(String nsUri, String localName) {
        return Arrays.stream(values())
                .filter(k -> k.namespaceUri.equals(nsUri) && k.localName.equals(localName))
                .findFirst();
    }
}
