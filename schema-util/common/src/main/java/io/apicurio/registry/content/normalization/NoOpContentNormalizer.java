package io.apicurio.registry.content.normalization;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

public class NoOpContentNormalizer implements ContentNormalizer {

    public ContentHandle normalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences) {
        return content;
    }
}
