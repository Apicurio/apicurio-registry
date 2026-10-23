package io.apicurio.registry.xsd.util;

import io.apicurio.registry.content.TypedContent;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.InputStream;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves schema locations exclusively from a map of resolved registry references. Never accesses
 * network or filesystem. Unmapped locations are recorded and answered with an empty schema so that the
 * parser never falls back to loading the location itself.
 */
public class MappedReferenceResourceResolver implements LSResourceResolver {

    /**
     * Synthetic, non-URL system id given to the root schema source so that the parser passes literal
     * location values (rather than absolutized URLs) to this resolver.
     */
    public static final String ROOT_SYSTEM_ID = "urn:apicurio:root";

    private static final String EMPTY_SCHEMA = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";

    private final Map<String, TypedContent> resolvedReferences;
    private final Set<String> unresolved = new LinkedHashSet<>();

    /**
     * Constructor.
     *
     * @param resolvedReferences map of reference name (literal location) to content; may be null
     */
    public MappedReferenceResourceResolver(Map<String, TypedContent> resolvedReferences) {
        this.resolvedReferences = resolvedReferences == null ? Map.of() : resolvedReferences;
    }

    /**
     * Resolves the given location from the resolved references only.
     *
     * @see LSResourceResolver#resolveResource(String, String, String, String, String)
     */
    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
            String baseURI) {
        if (systemId == null) {
            return null;
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
            this.data = data;
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
            this.data = s;
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
