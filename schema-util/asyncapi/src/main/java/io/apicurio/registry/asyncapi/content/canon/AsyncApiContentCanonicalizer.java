package io.apicurio.registry.asyncapi.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import org.apache.avro.Schema;

import java.util.Map;

public class AsyncApiContentCanonicalizer extends BaseContentCanonicalizer {
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
                                          Map<String, TypedContent> refs) throws Exception {
        Schema schema = new Schema.Parser().parse(content.getContent().content());
        String canonical = schema.toString();
        return TypedContent.create(ContentHandle.create(canonical), content.getContentType());
    }
}
