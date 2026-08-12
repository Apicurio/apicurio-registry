package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.types.VersionState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_STATE_CHANGED;

public class ArtifactVersionStateChanged extends OutboxEvent {

    private final Map<String, Object> data;

    private ArtifactVersionStateChanged(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ArtifactVersionStateChanged of(String groupId, String artifactId, String version,
            VersionState oldState, VersionState newState) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        data.put("version", version);
        data.put("oldState", oldState.name());
        data.put("newState", newState.name());
        return new ArtifactVersionStateChanged(id, groupId + "-" + artifactId + "-" + version, data);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_STATE_CHANGED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
