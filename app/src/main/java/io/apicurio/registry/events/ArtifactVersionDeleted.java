package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_DELETED;

public class ArtifactVersionDeleted extends OutboxEvent {

    private final JSONObject eventPayload;

    private ArtifactVersionDeleted(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactVersionDeleted of(String groupId, String artifactId, String version) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("artifactId", artifactId).put("version", version)
                .put("eventType", ARTIFACT_VERSION_DELETED.name());

        return new ArtifactVersionDeleted(id, groupId + "-" + artifactId + "-" + version, jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_DELETED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}
