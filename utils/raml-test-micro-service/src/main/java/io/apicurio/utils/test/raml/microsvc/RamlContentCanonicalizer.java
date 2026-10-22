package io.apicurio.utils.test.raml.microsvc;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;
import io.apicurio.registry.types.ContentTypes;

import java.util.Map;

public class RamlContentCanonicalizer implements ContentCanonicalizer {

    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        try {
            String converted = content.getContent().content()
                    .replace("use to query all orders of a user", "USE TO QUERY ALL ORDERS OF A USER")
                    .replace("Lists all orders of a specific user", "LIST ALL ORDERS OF A SPECIFIC USER");
            return TypedContent.create(ContentHandle.create(converted), ContentTypes.APPLICATION_YAML);
        } catch (Throwable t) {
            return content;
        }
    }

}
