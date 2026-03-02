package io.apicurio.registry.asyncapi.content.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredContentExtractor;
import io.apicurio.registry.content.extract.StructuredElement;

/**
 * Extracts structured elements from AsyncAPI content for search indexing. Parses the AsyncAPI document and
 * extracts channels, messages, schemas, servers, operationIds, and tags.
 */
public class AsyncApiStructuredContentExtractor implements StructuredContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(AsyncApiStructuredContentExtractor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<StructuredElement> extract(ContentHandle content) {
        try {
            JsonNode root = objectMapper.readTree(content.content());
            List<StructuredElement> elements = new ArrayList<>();

            extractChannels(root, elements);
            extractMessages(root, elements);
            extractSchemas(root, elements);
            extractServers(root, elements);
            extractOperationIds(root, elements);
            extractTags(root, elements);

            return elements;
        } catch (Exception e) {
            log.debug("Failed to extract structured content from AsyncAPI: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts channel names from the channels section.
     */
    private void extractChannels(JsonNode root, List<StructuredElement> elements) {
        JsonNode channels = root.path("channels");
        if (!channels.isMissingNode() && channels.isObject()) {
            channels.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("channel", name));

                // Extract operationIds from channel operations (AsyncAPI 2.x)
                JsonNode channel = channels.get(name);
                extractChannelOperationIds(channel, elements);
            });
        }
    }

    /**
     * Extracts operationIds from subscribe/publish operations within a channel (AsyncAPI 2.x).
     */
    private void extractChannelOperationIds(JsonNode channel, List<StructuredElement> elements) {
        String[] operations = { "subscribe", "publish" };
        for (String op : operations) {
            JsonNode operation = channel.path(op);
            if (!operation.isMissingNode() && operation.has("operationId")) {
                elements.add(new StructuredElement("operation",
                        operation.get("operationId").asText()));
            }
        }
    }

    /**
     * Extracts message names from components/messages.
     */
    private void extractMessages(JsonNode root, List<StructuredElement> elements) {
        JsonNode messages = root.path("components").path("messages");
        if (!messages.isMissingNode() && messages.isObject()) {
            messages.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("message", name));
            });
        }
    }

    /**
     * Extracts schema names from components/schemas.
     */
    private void extractSchemas(JsonNode root, List<StructuredElement> elements) {
        JsonNode schemas = root.path("components").path("schemas");
        if (!schemas.isMissingNode() && schemas.isObject()) {
            schemas.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("schema", name));
            });
        }
    }

    /**
     * Extracts server names from the servers section.
     */
    private void extractServers(JsonNode root, List<StructuredElement> elements) {
        JsonNode servers = root.path("servers");
        if (!servers.isMissingNode() && servers.isObject()) {
            servers.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("server", name));
            });
        }
    }

    /**
     * Extracts operationIds from top-level operations (AsyncAPI 3.x).
     */
    private void extractOperationIds(JsonNode root, List<StructuredElement> elements) {
        JsonNode operations = root.path("operations");
        if (!operations.isMissingNode() && operations.isObject()) {
            operations.fieldNames().forEachRemaining(name -> {
                elements.add(new StructuredElement("operation", name));
            });
        }
    }

    /**
     * Extracts tag names from the top-level tags array.
     */
    private void extractTags(JsonNode root, List<StructuredElement> elements) {
        JsonNode tags = root.path("tags");
        if (!tags.isMissingNode() && tags.isArray()) {
            for (JsonNode tag : tags) {
                if (tag.has("name")) {
                    elements.add(new StructuredElement("tag", tag.get("name").asText()));
                }
            }
        }
    }
}
