package io.apicurio.registry.types.provider;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.content.canon.XmlContentCanonicalizer;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.NoopContentExtractor;
import io.apicurio.registry.content.refs.NoOpReferenceFinder;
import io.apicurio.registry.content.refs.ReferenceFinder;
import io.apicurio.registry.content.util.ContentTypeUtil;
import io.apicurio.registry.rules.compatibility.CompatibilityChecker;
import io.apicurio.registry.rules.compatibility.NoopCompatibilityChecker;
import io.apicurio.registry.rules.validity.ContentValidator;
import io.apicurio.registry.rules.validity.XmlContentValidator;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.util.DocumentBuilderAccessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Map;

public class XmlArtifactTypeUtilProvider extends AbstractArtifactTypeUtilProvider {

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String contentType = content.getContentType();
            if (contentType.toLowerCase().contains("xml")
                    && ContentTypeUtil.isParsableXml(content.getContent())) {
                Document xmlDocument = DocumentBuilderAccessor.getDocumentBuilder()
                        .parse(content.getContent().stream());
                Element root = xmlDocument.getDocumentElement();
                String ns = root.getNamespaceURI();
                if (ns != null && ns.equals("http://www.w3.org/2001/XMLSchema")) {
                    return false;
                } else if (ns != null && (ns.equals("http://schemas.xmlsoap.org/wsdl/")
                        || ns.equals("http://www.w3.org/ns/wsdl/"))) {
                    return false;
                } else {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * @see io.apicurio.registry.types.provider.ArtifactTypeUtilProvider#getArtifactType()
     */
    @Override
    public String getArtifactType() {
        return ArtifactType.XML;
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createCompatibilityChecker()
     */
    @Override
    protected CompatibilityChecker createCompatibilityChecker() {
        return NoopCompatibilityChecker.INSTANCE;
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createContentCanonicalizer()
     */
    @Override
    protected ContentCanonicalizer createContentCanonicalizer() {
        return new XmlContentCanonicalizer();
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createContentValidator()
     */
    @Override
    protected ContentValidator createContentValidator() {
        return new XmlContentValidator();
    }

    /**
     * @see io.apicurio.registry.types.provider.AbstractArtifactTypeUtilProvider#createContentExtractor()
     */
    @Override
    protected ContentExtractor createContentExtractor() {
        return NoopContentExtractor.INSTANCE;
    }

    @Override
    public ContentDereferencer getContentDereferencer() {
        return null;
    }

    /**
     * @see io.apicurio.registry.types.provider.ArtifactTypeUtilProvider#getReferenceFinder()
     */
    @Override
    public ReferenceFinder getReferenceFinder() {
        return NoOpReferenceFinder.INSTANCE;
    }
}
