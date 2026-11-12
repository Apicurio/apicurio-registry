package io.apicurio.registry.agents.content.extract;

import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

/**
 * Performs metadata extraction for AI/ML Model Schema content.
 *
 * Extracts the model ID as the artifact name and the provider as the description.
 */
public class ModelSchemaContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ModelSchemaContentExtractor.class);

    private static final ObjectMapper mapper = new JsonMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode root = mapper.readTree(content.bytes());
            ExtractedMetaData metaData = null;

            JsonNode modelId = root.get("modelId");
            if (modelId != null && !modelId.isNull() && modelId.isString()) {
                metaData = new ExtractedMetaData();
                metaData.setName(modelId.asString());
            }

            JsonNode provider = root.get("provider");
            if (provider != null && !provider.isNull() && provider.isString()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(provider.asString());
            }

            return metaData;
        } catch (JacksonException e) {
            log.warn("Error extracting metadata from Model Schema content: {}", e.getMessage());
            return null;
        }
    }
}
