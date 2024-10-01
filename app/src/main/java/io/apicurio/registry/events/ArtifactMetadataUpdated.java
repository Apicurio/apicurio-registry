package io.apicurio.registry.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_METADATA_UPDATED;

public class ArtifactMetadataUpdated extends OutboxEvent {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final JsonNode eventPayload;

    private ArtifactMetadataUpdated(String id, String aggregateId, JsonNode eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactMetadataUpdated of(String groupId, String artifactId,
            EditableArtifactMetaDataDto artifactMetaDataDto) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        ObjectNode asJson = mapper.createObjectNode().put("id", id).put("groupId", groupId)
                .put("artifactId", artifactId).put("name", artifactMetaDataDto.getName())
                .put("owner", artifactMetaDataDto.getOwner())
                .put("description", artifactMetaDataDto.getDescription())
                .put("eventType", ARTIFACT_METADATA_UPDATED.name());

        return new ArtifactMetadataUpdated(id, groupId + "-" + artifactId, asJson);
    }

    @Override
    public String getType() {
        return ARTIFACT_METADATA_UPDATED.name();
    }

    @Override
    public JsonNode getPayload() {
        return eventPayload;
    }
}