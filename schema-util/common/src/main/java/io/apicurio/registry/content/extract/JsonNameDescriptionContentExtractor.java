package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Generic content extractor for JSON artifacts that have top-level "name" and "description" fields.
 * Shared by AGENT_CARD and MCP_TOOL artifact types.
 */
public class JsonNameDescriptionContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonNameDescriptionContentExtractor.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode root = mapper.readTree(content.bytes());
            JsonNode name = root.get("name");
            JsonNode description = root.get("description");

            ExtractedMetaData metaData = null;

            if (name != null && !name.isNull() && name.isTextual()) {
                metaData = new ExtractedMetaData();
                metaData.setName(name.asText());
            }

            if (description != null && !description.isNull() && description.isTextual()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(description.asText());
            }

            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from JSON content: {}", e.getMessage());
            return null;
        }
    }
}
