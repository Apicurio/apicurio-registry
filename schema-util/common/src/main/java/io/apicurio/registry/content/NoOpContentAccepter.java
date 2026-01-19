package io.apicurio.registry.content;

import java.util.Map;

public class NoOpContentAccepter implements ContentAccepter {

    public static final NoOpContentAccepter INSTANCE = new NoOpContentAccepter();

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return false;
    }
}
