package io.apicurio.registry.content.dereference;

import io.apicurio.registry.content.TypedContent;

import java.util.Map;

public class NoopContentDereferencer implements ContentDereferencer {

    public static final NoopContentDereferencer INSTANCE = new NoopContentDereferencer();

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return content;
    }

    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        return content;
    }
}
