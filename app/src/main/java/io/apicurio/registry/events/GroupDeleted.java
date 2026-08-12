package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_DELETED;

public class GroupDeleted extends OutboxEvent {

    private final Map<String, Object> data;

    private GroupDeleted(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static GroupDeleted of(String groupId) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        return new GroupDeleted(id, groupId, data);
    }

    @Override
    public String getType() {
        return GROUP_DELETED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
