package io.apicurio.registry.content.normalization;

import io.apicurio.registry.content.ContentHandle;

import java.util.Map;

public interface ContentNormalizer {

    public ContentHandle normalize(ContentHandle content, Map<String, ContentHandle> resolvedReferences);

}
