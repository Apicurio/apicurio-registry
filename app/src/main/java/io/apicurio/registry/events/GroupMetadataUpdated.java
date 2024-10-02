package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_METADATA_UPDATED;

public class GroupMetadataUpdated extends OutboxEvent {
    private final JSONObject eventPayload;

    private GroupMetadataUpdated(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static GroupMetadataUpdated of(String groupId, EditableGroupMetaDataDto groupMetaDataDto) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("description", groupMetaDataDto.getDescription())
                .put("eventType", GROUP_METADATA_UPDATED.name());

        return new GroupMetadataUpdated(id, groupId, jsonObject);
    }

    @Override
    public String getType() {
        return GROUP_METADATA_UPDATED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}