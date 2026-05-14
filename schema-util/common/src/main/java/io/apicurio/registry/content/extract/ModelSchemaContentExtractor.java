package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Performs metadata extraction for AI/ML Model Schema content.
 *
 * Extracts the model ID as the artifact name and the provider as the description.
 */
public class ModelSchemaContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ModelSchemaContentExtractor.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode root = mapper.readTree(content.bytes());
            ExtractedMetaData metaData = null;

            JsonNode modelId = root.get("modelId");
            if (modelId != null && !modelId.isNull() && modelId.isTextual()) {
                metaData = new ExtractedMetaData();
                metaData.setName(modelId.asText());
            }

            JsonNode provider = root.get("provider");
            if (provider != null && !provider.isNull() && provider.isTextual()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(provider.asText());
            }

            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from Model Schema content: {}", e.getMessage());
            return null;
        }
    }
}
