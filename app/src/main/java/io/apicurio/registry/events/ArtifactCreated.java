package io.apicurio.registry.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_CREATED;

public class ArtifactCreated extends OutboxEvent {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final JsonNode eventPayload;

    private ArtifactCreated(String id, String aggregateId, JsonNode eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactCreated of(ArtifactMetaDataDto artifactMetaDataDto) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        ObjectNode asJson = mapper.createObjectNode().put("id", id)
                .put("artifactId", artifactMetaDataDto.getArtifactId())
                .put("name", artifactMetaDataDto.getName())
                .put("description", artifactMetaDataDto.getDescription())
                .put("eventType", ARTIFACT_CREATED.name());

        return new ArtifactCreated(id,
                artifactMetaDataDto.getGroupId() + "-" + artifactMetaDataDto.getArtifactId(), asJson);
    }

    @Override
    public String getType() {
        return ARTIFACT_CREATED.name();
    }

    @Override
    public JsonNode getPayload() {
        return eventPayload;
    }
}