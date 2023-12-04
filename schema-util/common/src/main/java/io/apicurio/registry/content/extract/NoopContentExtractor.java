package io.apicurio.registry.content.extract;

import io.apicurio.registry.content.ContentHandle;


public class NoopContentExtractor implements ContentExtractor {
    public static final ContentExtractor INSTANCE = new NoopContentExtractor();

    private NoopContentExtractor() {
    }

    public ExtractedMetaData extract(ContentHandle content) {
        return null;
    }
}
