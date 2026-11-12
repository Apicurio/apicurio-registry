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
 * Generic content extractor for JSON artifacts that have top-level "name" and "description" fields.
 * Shared by AGENT_CARD and MCP_TOOL artifact types.
 */
public class JsonNameDescriptionContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(JsonNameDescriptionContentExtractor.class);

    private static final ObjectMapper mapper = new JsonMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode root = mapper.readTree(content.bytes());
            JsonNode name = root.get("name");
            JsonNode description = root.get("description");

            ExtractedMetaData metaData = null;

            if (name != null && !name.isNull() && name.isString()) {
                metaData = new ExtractedMetaData();
                metaData.setName(name.asString());
            }

            if (description != null && !description.isNull() && description.isString()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(description.asString());
            }

            return metaData;
        } catch (JacksonException e) {
            log.warn("Error extracting metadata from JSON content: {}", e.getMessage());
            return null;
        }
    }
}
