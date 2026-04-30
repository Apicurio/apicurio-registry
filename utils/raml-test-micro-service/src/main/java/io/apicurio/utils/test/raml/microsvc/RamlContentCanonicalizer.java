package io.apicurio.utils.test.raml.microsvc;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizationException;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

public class RamlContentCanonicalizer extends BaseContentCanonicalizer {

    @Override
    protected TypedContent doCanonicalize(TypedContent content,
            Map<String, TypedContent> refs) throws ContentCanonicalizationException {
        String converted = content.getContent().content()
                .replace("use to query all orders of a user", "USE TO QUERY ALL ORDERS OF A USER")
                .replace("Lists all orders of a specific user", "LIST ALL ORDERS OF A SPECIFIC USER");
        return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_YAML);
    }

}
