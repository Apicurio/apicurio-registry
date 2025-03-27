package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.canon.ContentCanonicalizer;

import java.util.Map;

public class ConfiguredContentCanonicalizer implements ContentCanonicalizer {
    public ConfiguredContentCanonicalizer(Provider provider) {
    }

    @Override
    public TypedContent canonicalize(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return content;
    }
}
