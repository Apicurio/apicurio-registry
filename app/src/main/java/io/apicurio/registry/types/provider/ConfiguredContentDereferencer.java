package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.dereference.ContentDereferencer;

import java.util.Map;

public class ConfiguredContentDereferencer implements ContentDereferencer {
    public ConfiguredContentDereferencer(Provider provider) {

    }

    @Override
    public TypedContent dereference(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return content;
    }

    @Override
    public TypedContent rewriteReferences(TypedContent content, Map<String, String> resolvedReferenceUrls) {
        return content;
    }
}
