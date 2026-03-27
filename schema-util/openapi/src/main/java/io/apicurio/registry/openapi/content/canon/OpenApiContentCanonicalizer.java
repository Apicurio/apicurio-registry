package io.apicurio.registry.openapi.content.canon;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.BaseContentCanonicalizer;
import io.apicurio.registry.content.canon.ContentCanonicalizationException;
import io.apicurio.registry.content.canon.JsonYamlCanonicalizer;

import java.util.Map;

/**
 * An OpenAPI content canonicalizer. This will remove any extra formatting such as whitespace and also sort
 * all fields/properties for all objects (because ordering of properties does not matter).
 */
public class OpenApiContentCanonicalizer extends BaseContentCanonicalizer {

    /**
     * @see ContentCanonicalizer#canonicalize(TypedContent, Map)
     */
    @Override
    protected TypedContent doCanonicalize(TypedContent content,
            Map<String, TypedContent> refs) throws ContentCanonicalizationException {
        return JsonYamlCanonicalizer.canonicalize(content);
    }

}
