package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_CREATED;

public class ArtifactVersionCreated extends OutboxEvent {
    private final JSONObject eventPayload;

    private ArtifactVersionCreated(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactVersionCreated of(ArtifactVersionMetaDataDto versionMetaDataDto) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", versionMetaDataDto.getGroupId())
                .put("artifactId", versionMetaDataDto.getArtifactId())
                .put("version", versionMetaDataDto.getVersion()).put("name", versionMetaDataDto.getName())
                .put("description", versionMetaDataDto.getDescription())
                .put("eventType", ARTIFACT_VERSION_CREATED.name());

        return new ArtifactVersionCreated(id, versionMetaDataDto.getGroupId() + "-"
                + versionMetaDataDto.getArtifactId() + "-" + versionMetaDataDto.getVersion(), jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_CREATED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}