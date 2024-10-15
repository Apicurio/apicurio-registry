package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.ArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_CREATED;

public class ArtifactCreated extends OutboxEvent {
    private final JSONObject eventPayload;

    private ArtifactCreated(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactCreated of(ArtifactMetaDataDto artifactMetaDataDto) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", artifactMetaDataDto.getGroupId())
                .put("artifactId", artifactMetaDataDto.getArtifactId())
                .put("name", artifactMetaDataDto.getName())
                .put("description", artifactMetaDataDto.getDescription())
                .put("eventType", ARTIFACT_CREATED.name());

        return new ArtifactCreated(id,
                artifactMetaDataDto.getGroupId() + "-" + artifactMetaDataDto.getArtifactId(), jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_CREATED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}