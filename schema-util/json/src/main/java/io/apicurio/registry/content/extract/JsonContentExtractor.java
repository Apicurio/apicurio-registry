package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Performs meta-data extraction for JSON Schema content.
 */
public class JsonContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode jsonSchema = mapper.readTree(content.bytes());
            JsonNode title = jsonSchema.get("title");
            JsonNode desc = jsonSchema.get("description");

            ExtractedMetaData metaData = null;
            if (title != null && !title.isNull()) {
                metaData = new ExtractedMetaData();
                metaData.setName(title.asText());
            }
            if (desc != null && !desc.isNull()) {
                if (metaData == null) {
                    metaData = new ExtractedMetaData();
                }
                metaData.setDescription(desc.asText());
            }
            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from JSON: {}", e.getMessage());
            return null;
        }
    }
}
