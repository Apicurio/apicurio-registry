package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.refs.ExternalReference;
import io.apicurio.registry.content.refs.ReferenceFinder;

import java.util.Set;

public class ConfiguredReferenceFinder implements ReferenceFinder {
    public ConfiguredReferenceFinder(Provider provider) {
    }

    @Override
    public Set<ExternalReference> findExternalReferences(TypedContent content) {
        return Set.of();
    }
}
