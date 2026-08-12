package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_METADATA_UPDATED;

public class ArtifactMetadataUpdated extends OutboxEvent {

    private final Map<String, Object> data;

    private ArtifactMetadataUpdated(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ArtifactMetadataUpdated of(String groupId, String artifactId,
            EditableArtifactMetaDataDto artifactMetaDataDto) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        data.put("name", artifactMetaDataDto.getName());
        data.put("description", artifactMetaDataDto.getDescription());
        data.put("owner", artifactMetaDataDto.getOwner());
        data.put("labels", artifactMetaDataDto.getLabels());
        return new ArtifactMetadataUpdated(id, groupId + "-" + artifactId, data);
    }

    @Override
    public String getType() {
        return ARTIFACT_METADATA_UPDATED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}