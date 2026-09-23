package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.time.Instant;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_CREATED;

public class GroupCreated extends OutboxEvent {
    private final JSONObject eventPayload;

    private GroupCreated(String id, String aggregateId, JSONObject eventPayload, Instant timestamp) {
        super(id, aggregateId, timestamp);
        this.eventPayload = eventPayload;
    }

    public static GroupCreated of(GroupMetaDataDto groupMetaDataDto) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupMetaDataDto.getGroupId()).put("eventType",
                GROUP_CREATED.name());

        Instant timestamp = Instant.ofEpochMilli(groupMetaDataDto.getCreatedOn());
        return new GroupCreated(id, groupMetaDataDto.getGroupId(), jsonObject, timestamp);
    }

    @Override
    public String getType() {
        return GROUP_CREATED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}