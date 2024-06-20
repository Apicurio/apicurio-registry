package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.CanonicalizationException;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.c14n.InvalidCanonicalizerException;
import org.apache.xml.security.parser.XMLParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * A common XML content canonicalizer.
 */
public class XmlContentCanonicalizer implements ContentCanonicalizer {

    private static ThreadLocal<Canonicalizer> xmlCanonicalizer = new ThreadLocal<Canonicalizer>() {
        @Override
        protected Canonicalizer initialValue() {
            try {
                return Canonicalizer.getInstance(Canonicalizer.ALGO_ID_C14N_OMIT_COMMENTS);
            } catch (InvalidCanonicalizerException e) {
                throw new RuntimeException(e);
            }
        }
    };

    static {
        Init.init();
    }

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            Canonicalizer canon = xmlCanonicalizer.get();
            var out = new ByteArrayOutputStream(content.getContent().getSizeBytes());
            canon.canonicalize(content.getContent().bytes(), out, false); // TODO secureValidation?
            var canonicalized = out.toString(Canonicalizer.ENCODING);
            return TypedContent.create(ContentHandle.create(canonicalized), ContentTypes.APPLICATION_XML);
        } catch (CanonicalizationException | IOException | XMLParserException e) {
        }
        return content;
    }
}
