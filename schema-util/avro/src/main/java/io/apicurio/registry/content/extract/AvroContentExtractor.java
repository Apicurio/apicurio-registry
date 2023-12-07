package io.apicurio.registry.content.extract;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;

/**
 * Performs meta-data extraction for Avro content.
 */
public class AvroContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new ObjectMapper();

    public AvroContentExtractor() {
    }

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode avroSchema = mapper.readTree(content.bytes());
            JsonNode name = avroSchema.get("name");

            ExtractedMetaData metaData = null;
            if (name != null && !name.isNull()) {
                metaData = new ExtractedMetaData();
                metaData.setName(name.asText());
            }
            return metaData;
        } catch (IOException e) {
            log.warn("Error extracting metadata from JSON: {}", e.getMessage());
            return null;
        }
    }
}
