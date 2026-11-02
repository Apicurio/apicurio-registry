package io.apicurio.registry.xml.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.content.dereference.DereferencingNotSupportedException;
import io.apicurio.registry.xml.refs.XmlLocationRewriter;

import java.util.Map;

/**
 * Reference handling for XSD and WSDL. Only REWRITE is supported; inlining is not.
 */
public class XmlReferenceDereferencer implements ContentDereferencer {

    /**
     * Inlining XML schema references is not supported.
     *
     * @throws DereferencingNotSupportedException always
     */
    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        throw new DereferencingNotSupportedException(
                "Artifact types XSD and WSDL do not support references=DEREFERENCE; use references=REWRITE instead.");
    }

    /**
     * Rewrites schemaLocation/location attributes whose value is a mapped reference name. Returns the
     * original content when nothing changes.
     *
     * @see ContentDereferencer#rewriteReferences(TypedContent, Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        String original = content.getContent().content();
        String rewritten = XmlLocationRewriter.rewrite(original, resolvedReferenceUrls);
        if (rewritten.equals(original)) {
            return content;
        }
        return TypedContent.create(ContentHandle.create(rewritten), content.getContentType());
    }
}
