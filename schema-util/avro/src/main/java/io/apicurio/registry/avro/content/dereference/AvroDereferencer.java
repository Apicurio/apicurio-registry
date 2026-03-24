package io.apicurio.registry.avro.content.dereference;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;
import io.apicurio.registry.types.ContentTypes;
import org.apache.avro.Schema;

import java.util.Map;

public class AvroDereferencer implements ContentDereferencer {

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        final Schema.Parser parser = new Schema.Parser();
        for (TypedContent referencedContent : resolvedReferences.values()) {
            parser.parse(referencedContent.getContent().content());
        }
        final Schema schema = parser.parse(content.getContent().content());
        return TypedContent.create(ContentHandle.create(schema.toString()), ContentTypes.APPLICATION_JSON);
    }

    /**
     * @see io.apicurio.registry.content.dereference.ContentDereferencer#rewriteReferences(io.apicurio.registry.content.TypedContent,
     *      java.util.Map)
     */
    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        // Avro does not support rewriting references. A reference in Avro is a QName of a type
        // defined in another .avsc file. The location of that other file is not included in the Avro
        // specification (in other words there is no "import" statement). So rewriting is meaningless
        // in Avro.
        // TODO: Should we throw an exception instead of failing silently?
        return content;
    }
}
