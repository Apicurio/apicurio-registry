package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.ContentAccepter;
import io.apicurio.registry.content.TypedContent;

import java.util.Map;

// TODO implement this class
public class ConfiguredContentAccepter implements ContentAccepter {

    private Provider provider;

    public ConfiguredContentAccepter(Provider provider) {
        this.provider = provider;
    }

    @Override
    public boolean acceptsContent(TypedContent content, Map<String, TypedContent> resolvedReferences) {
        return false;
    }
}
