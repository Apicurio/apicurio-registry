package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_DELETED;

public class GroupDeleted extends OutboxEvent {
    private final JSONObject eventPayload;

    private GroupDeleted(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static GroupDeleted of(String groupId) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("eventType", GROUP_DELETED.name());

        return new GroupDeleted(id, groupId, jsonObject);
    }

    @Override
    public String getType() {
        return GROUP_DELETED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}