package io.apicurio.registry.avro.content.extract;

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
 * Performs meta-data extraction for Avro content.
 */
public class AvroContentExtractor implements ContentExtractor {

    Logger log = LoggerFactory.getLogger(getClass());

    private ObjectMapper mapper = new JsonMapper();

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
                metaData.setName(name.asString());
            }
            return metaData;
        } catch (JacksonException e) {
            log.warn("Error extracting metadata from JSON: {}", e.getMessage());
            return null;
        }
    }
}
