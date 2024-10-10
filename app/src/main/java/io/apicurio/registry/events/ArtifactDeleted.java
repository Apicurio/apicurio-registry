package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_DELETED;

public class ArtifactDeleted extends OutboxEvent {

    private final JSONObject eventPayload;

    private ArtifactDeleted(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactDeleted of(String groupId, String artifactId) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("artifactId", artifactId).put("eventType",
                ARTIFACT_DELETED.name());

        return new ArtifactDeleted(id, groupId + "-" + artifactId, jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_DELETED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}
