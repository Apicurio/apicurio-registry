package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Performs metadata extraction for Prompt Template content.
 *
 * Extracts the template ID as the artifact name and the description field as the description.
 */
public class PromptTemplateContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateContentExtractor.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode root = mapper.readTree(content.bytes());
            ExtractedMetaData metaData = null;

            JsonNode templateId = root.get("templateId");
            if (templateId != null && !templateId.isNull() && templateId.isTextual()) {
                metaData = new ExtractedMetaData();
                metaData.setName(templateId.asText());
            }

            JsonNode description = root.get("description");
            if (description != null && !description.isNull() && description.isTextual()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(description.asText());
            }

            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from Prompt Template content: {}", e.getMessage());
            return null;
        }
    }
}
