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
 * Performs metadata extraction for Prompt Template content.
 *
 * Extracts the template ID as the artifact name and the description field as the description.
 */
public class PromptTemplateContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateContentExtractor.class);

    private static final ObjectMapper mapper = new JsonMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode root = mapper.readTree(content.bytes());
            ExtractedMetaData metaData = null;

            JsonNode templateId = root.get("templateId");
            if (templateId != null && !templateId.isNull() && templateId.isString()) {
                metaData = new ExtractedMetaData();
                metaData.setName(templateId.asString());
            }

            JsonNode description = root.get("description");
            if (description != null && !description.isNull() && description.isString()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(description.asString());
            }

            return metaData;
        } catch (JacksonException e) {
            log.warn("Error extracting metadata from Prompt Template content: {}", e.getMessage());
            return null;
        }
    }
}
