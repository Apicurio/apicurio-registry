package io.apicurio.registry.json.content.extract;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.ContentExtractor;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

/**
 * Performs meta-data extraction for JSON Schema content.
 */
public class JsonContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new JsonMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode jsonSchema = mapper.readTree(content.bytes());
            JsonNode title = jsonSchema.get("title");
            JsonNode desc = jsonSchema.get("description");

            ExtractedMetaData metaData = null;
            if (title != null && !title.isNull()) {
                metaData = new ExtractedMetaData();
                metaData.setName(title.asString());
            }
            if (desc != null && !desc.isNull()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(desc.asString());
            }
            return metaData;
        } catch (JacksonException e) {
            log.warn("Error extracting metadata from JSON: {}", e.getMessage());
            return null;
        }
    }
}
