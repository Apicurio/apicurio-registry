package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.json.content.extract.JsonSchemaTagExtractorSupport;
import io.apicurio.registry.types.ArtifactType;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class JsonSchemaTagExtractor implements TagExtractor {

    @Override
    public String getArtifactType() {
        return ArtifactType.JSON;
    }

    @Override
    public Map<String, Set<String>> extractTags(ContentHandle content) {
        return JsonSchemaTagExtractorSupport.extractTags(content);
    }
}
