package io.apicurio.registry.content;

import java.util.Map;

public interface ContentAccepter {

    /**
     * Returns true if the given content is accepted as handled by the provider. Useful to know if e.g. some
     * bit of content is an AVRO or OPENAPI.
     */
    boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences);

}
