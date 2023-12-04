package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import org.apache.avro.Schema;


import java.util.Map;


public class AvroDereferencer implements ContentDereferencer {

    @Override
    public ContentHandle dereference(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        final Schema.Parser parser = new Schema.Parser();
        for (ContentHandle referencedContent : resolvedReferences.values()) {
            parser.parse(referencedContent.content());
        }
        final Schema schema = parser.parse(content.content());
        return ContentHandle.create(schema.toString());
    }
    
    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.ContentHandle, java.util.Map)
     */
    @Override
    public ContentHandle rewriteReferences(ContentHandle content, Map<String, String> resolvedReferenceUrls) {
        // Avro does not support rewriting references.  A reference in Avro is a QName of a type
        // defined in another .avsc file.  The location of that other file is not included in the Avro
        // specification (in other words there is no "import" statement).  So rewriting is meaningless
        // in Avro.
        return content;
    }
}
