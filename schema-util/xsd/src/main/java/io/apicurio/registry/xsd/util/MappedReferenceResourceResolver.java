package io.apicurio.registry.xsd.util;

import io.apicurio.registry.content.TypedContent;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import javax.xml.XMLConstants;
import java.io.InputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves schema locations exclusively from a map of resolved registry references. Never accesses
 * network or filesystem. Unmapped schema locations are recorded and answered with an empty schema so that the
 * parser never falls back to loading the location itself. Non-schema resources (e.g. DTDs) are answered
 * with empty content and are not recorded.
 */
public class MappedReferenceResourceResolver implements LSResourceResolver {

    /**
     * Synthetic, non-URL system id given to the root schema source so that the parser passes literal
     * location values (rather than absolutized URLs) to this resolver.
     */
    public static final String ROOT_SYSTEM_ID = "urn:apicurio:root";

    private static final String EMPTY_SCHEMA = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";

    /**
     * Xerces treats an LSInput with empty string data as having no data and then opens the systemId itself
     * (bypassing ACCESS_EXTERNAL_* restrictions). String data handed to Xerces must therefore never be
     * empty; a single blank is used instead.
     */
    private static final String NON_EMPTY_BLANK = " ";

    /**
     * Prefix of synthetic system ids used for in-memory (inline) schema documents. Locations with this
     * prefix are never user references: they are never recorded as unresolved and never served from the
     * user-mapped references.
     */
    public static final String INLINE_ID_PREFIX = "urn:apicurio:inline:";

    private final Map<String, TypedContent> resolvedReferences;
    private final Map<String, String> inlineDocuments;
    private final Map<String, String> inlineNamespaces;
    private final Set<String> unresolved = new LinkedHashSet<>();

    /**
     * Constructor.
     *
     * @param resolvedReferences map of reference name (literal location) to content; may be null
     */
    public MappedReferenceResourceResolver(Map<String, TypedContent> resolvedReferences) {
        this(resolvedReferences, null, null);
    }

    /**
     * Constructor supporting in-memory (inline) schema documents.
     *
     * @param resolvedReferences map of reference name (literal location) to content; may be null
     * @param inlineDocuments map of synthetic system id (prefixed with {@link #INLINE_ID_PREFIX}) to schema
     *            text; may be null
     * @param inlineNamespaces map of target namespace to the synthetic system id of the inline document that
     *            provides it, used for namespace-only imports; may be null
     */
    public MappedReferenceResourceResolver(Map<String, TypedContent> resolvedReferences,
            Map<String, String> inlineDocuments, Map<String, String> inlineNamespaces) {
        this.resolvedReferences = resolvedReferences == null ? Map.of() : resolvedReferences;
        this.inlineDocuments = inlineDocuments == null ? Map.of() : inlineDocuments;
        this.inlineNamespaces = inlineNamespaces == null ? Map.of() : inlineNamespaces;
    }

    /**
     * Resolves the given location from the resolved references only.
     *
     * @see LSResourceResolver#resolveResource(String, String, String, String, String)
     */
    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
            String baseURI) {
        boolean schemaType = XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(type);
        if (systemId == null) {
            String inlineId = schemaType && namespaceURI != null ? inlineNamespaces.get(namespaceURI) : null;
            String inline = inlineId == null ? null : inlineDocuments.get(inlineId);
            return inline == null ? null : new StringInput(inlineId, publicId, inline);
        }
        if (!schemaType) {
            // Non-schema resources (e.g. external DTDs) are never loaded and are not registry references.
            return new StringInput(systemId, publicId, NON_EMPTY_BLANK);
        }
        if (systemId.startsWith(INLINE_ID_PREFIX)) {
            // Synthetic ids are served only from the in-memory map and never shadowed by user references.
            String inline = inlineDocuments.get(systemId);
            return new StringInput(systemId, publicId, inline == null ? EMPTY_SCHEMA : inline);
        }
        TypedContent found = resolvedReferences.get(systemId);
        if (found == null) {
            unresolved.add(systemId);
            return new StringInput(systemId, publicId, EMPTY_SCHEMA);
        }
        return new StringInput(systemId, publicId, found.getContent().content());
    }

    /**
     * Returns the locations that could not be resolved.
     *
     * @return unresolved locations, in encounter order
     */
    public Set<String> getUnresolved() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(unresolved));
    }

    private static final class StringInput implements LSInput {
        private String systemId;
        private String publicId;
        private String data;

        StringInput(String systemId, String publicId, String data) {
            this.systemId = systemId;
            this.publicId = publicId;
            this.data = nonEmpty(data);
        }

        private static String nonEmpty(String data) {
            return data == null || data.isEmpty() ? NON_EMPTY_BLANK : data;
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(Reader r) {
        }

        @Override
        public InputStream getByteStream() {
            return null;
        }

        @Override
        public void setByteStream(InputStream s) {
        }

        @Override
        public String getStringData() {
            return data;
        }

        @Override
        public void setStringData(String s) {
            this.data = nonEmpty(s);
        }

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String s) {
            this.systemId = s;
        }

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String s) {
            this.publicId = s;
        }

        @Override
        public String getBaseURI() {
            return null;
        }

        @Override
        public void setBaseURI(String s) {
        }

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public void setEncoding(String s) {
        }

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean b) {
        }
    }
}
