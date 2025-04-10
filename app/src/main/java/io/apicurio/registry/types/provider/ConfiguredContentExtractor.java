package io.apicurio.registry.types.provider;

import io.apicurio.registry.config.artifactTypes.Provider;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;

public class ConfiguredContentExtractor implements ContentExtractor {
    public ConfiguredContentExtractor(Provider provider) {
    }

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        return null;
    }
}
