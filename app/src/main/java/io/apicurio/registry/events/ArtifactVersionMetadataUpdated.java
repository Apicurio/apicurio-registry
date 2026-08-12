package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_METADATA_UPDATED;

public class ArtifactVersionMetadataUpdated extends OutboxEvent {

    private final Map<String, Object> data;

    private ArtifactVersionMetadataUpdated(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ArtifactVersionMetadataUpdated of(String groupId, String artifactId, String version,
            EditableVersionMetaDataDto editableVersionMetaDataDto) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        data.put("version", version);
        data.put("name", editableVersionMetaDataDto.getName());
        data.put("description", editableVersionMetaDataDto.getDescription());
        data.put("labels", editableVersionMetaDataDto.getLabels());
        return new ArtifactVersionMetadataUpdated(id, groupId + "-" + artifactId + "-" + version, data);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_METADATA_UPDATED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}