package io.apicurio.registry.content.extract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Performs metadata extraction for A2A Agent Card content.
 *
 * Extracts the agent name and description from the Agent Card JSON structure.
 *
 * @see <a href="https://a2a-protocol.org/">A2A Protocol</a>
 */
public class AgentCardContentExtractor implements ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(AgentCardContentExtractor.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ExtractedMetaData extract(ContentHandle content) {
        try {
            JsonNode agentCard = mapper.readTree(content.bytes());
            JsonNode name = agentCard.get("name");
            JsonNode description = agentCard.get("description");

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
            log.warn("Error extracting metadata from Agent Card: {}", e.getMessage());
            return null;
        }
    }
}
