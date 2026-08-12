package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.GroupMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_CREATED;

public class GroupCreated extends OutboxEvent {
    private final GroupMetaDataDto data;

    private GroupCreated(String id, String aggregateId, GroupMetaDataDto data, Instant timestamp) {
        super(id, aggregateId, timestamp);
        this.data = data;
    }

    public static GroupCreated of(GroupMetaDataDto groupMetaDataDto) {
        String id = UUID.randomUUID().toString();
        Instant timestamp = Instant.ofEpochMilli(groupMetaDataDto.getCreatedOn());
        return new GroupCreated(id, groupMetaDataDto.getGroupId(), groupMetaDataDto, timestamp);
    }

    @Override
    public String getType() {
        return GROUP_CREATED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
