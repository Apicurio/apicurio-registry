package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_DELETED;

public class ArtifactVersionDeleted extends OutboxEvent {

    private final Map<String, Object> data;

    private ArtifactVersionDeleted(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ArtifactVersionDeleted of(String groupId, String artifactId, String version) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        data.put("version", version);
        return new ArtifactVersionDeleted(id, groupId + "-" + artifactId + "-" + version, data);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_DELETED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
