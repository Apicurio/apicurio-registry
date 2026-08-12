package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_DELETED;

public class ArtifactDeleted extends OutboxEvent {

    private final Map<String, Object> data;

    private ArtifactDeleted(String id, String aggregateId, Map<String, Object> data, Instant timestamp) {
        super(id, aggregateId, timestamp);
        this.data = data;
    }

    public static ArtifactDeleted of(String groupId, String artifactId) {
        return of(groupId, artifactId, Instant.now());
    }

    public static ArtifactDeleted of(String groupId, String artifactId, Instant timestamp) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        return new ArtifactDeleted(id, groupId + "-" + artifactId, data, timestamp);
    }

    @Override
    public String getType() {
        return ARTIFACT_DELETED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
