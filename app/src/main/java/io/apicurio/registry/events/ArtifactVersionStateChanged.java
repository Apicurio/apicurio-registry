package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.types.VersionState;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_STATE_CHANGED;

public class ArtifactVersionStateChanged extends OutboxEvent {

    private final JSONObject eventPayload;

    private ArtifactVersionStateChanged(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactVersionStateChanged of(String groupId, String artifactId, String version,
                                                 VersionState oldState, VersionState newState) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("artifactId", artifactId).put("version", version)
                .put("oldState", oldState.name())
                .put("newDate", newState.name())
                .put("eventType", ARTIFACT_VERSION_STATE_CHANGED.name());

        return new ArtifactVersionStateChanged(id, groupId + "-" + artifactId + "-" + version, jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_STATE_CHANGED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}