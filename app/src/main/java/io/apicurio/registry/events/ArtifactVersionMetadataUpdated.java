package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_METADATA_UPDATED;
import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_METADATA_UPDATED;

public class ArtifactVersionMetadataUpdated extends OutboxEvent {

    private final JSONObject eventPayload;

    private ArtifactVersionMetadataUpdated(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactVersionMetadataUpdated of(String groupId, String artifactId, String version,
                                                    EditableVersionMetaDataDto editableVersionMetaDataDto) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId)
                .put("artifactId", artifactId)
                .put("version", version)
                .put("name", editableVersionMetaDataDto.getName())
                .put("description", editableVersionMetaDataDto.getDescription())
                .put("eventType", ARTIFACT_VERSION_METADATA_UPDATED.name());

        return new ArtifactVersionMetadataUpdated(id, groupId + "-" + artifactId + "-" + version, jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_VERSION_METADATA_UPDATED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}