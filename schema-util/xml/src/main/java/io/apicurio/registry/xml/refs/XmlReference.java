package io.apicurio.registry.xml.refs;

/**
 * A reference from an XML document to another document.
 *
 * @param kind the kind of referencing element
 * @param location the literal (unescaped) location attribute value
 * @param namespace the namespace attribute value, or null if absent
 */
public record XmlReference(XmlReferenceKind kind, String location, String namespace) {
}
