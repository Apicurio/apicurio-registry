package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_CREATED;

public class GroupCreated extends OutboxEvent {
    private final JSONObject eventPayload;

    private GroupCreated(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static GroupCreated of(GroupMetaDataDto groupMetaDataDto) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupMetaDataDto.getGroupId()).put("eventType",
                GROUP_CREATED.name());

        return new GroupCreated(id, groupMetaDataDto.getGroupId(), jsonObject);
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