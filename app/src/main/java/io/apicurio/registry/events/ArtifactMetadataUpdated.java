package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_METADATA_UPDATED;

public class ArtifactMetadataUpdated extends OutboxEvent {

    private final JSONObject eventPayload;

    private ArtifactMetadataUpdated(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactMetadataUpdated of(String groupId, String artifactId,
            EditableArtifactMetaDataDto artifactMetaDataDto) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("artifactId", artifactId)
                .put("name", artifactMetaDataDto.getName()).put("owner", artifactMetaDataDto.getOwner())
                .put("description", artifactMetaDataDto.getDescription())
                .put("eventType", ARTIFACT_METADATA_UPDATED.name());

        return new ArtifactMetadataUpdated(id, groupId + "-" + artifactId, jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_METADATA_UPDATED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}