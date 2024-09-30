package io.apicurio.registry.storage.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_CREATED;

public class ArtifactCreatedEvent extends OutboxEvent {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final JsonNode designPayload;

    private ArtifactCreatedEvent(String id, String aggregateId, JsonNode designPayload) {
        super(id, aggregateId, ARTIFACT_CREATED.name());
        this.designPayload = designPayload;
    }

    public static ArtifactCreatedEvent of(ArtifactMetaDataDto artifactMetaDataDto) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        ObjectNode asJson = mapper.createObjectNode().put("id", id)
                .put("artifactId", artifactMetaDataDto.getArtifactId())
                .put("name", artifactMetaDataDto.getName())
                .put("description", artifactMetaDataDto.getDescription());

        return new ArtifactCreatedEvent(id, artifactMetaDataDto.getArtifactId(), asJson);
    }

    @Override
    public String getType() {
        return getEventType();
    }

    @Override
    public JsonNode getPayload() {
        return designPayload;
    }
}