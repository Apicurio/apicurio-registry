package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.EditableGroupMetaDataDto;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_METADATA_UPDATED;

public class GroupMetadataUpdated extends OutboxEvent {
    private final Map<String, Object> data;

    private GroupMetadataUpdated(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static GroupMetadataUpdated of(String groupId, EditableGroupMetaDataDto groupMetaDataDto) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("description", groupMetaDataDto.getDescription());
        data.put("owner", groupMetaDataDto.getOwner());
        data.put("labels", groupMetaDataDto.getLabels());
        return new GroupMetadataUpdated(id, groupId, data);
    }

    @Override
    public String getType() {
        return GROUP_METADATA_UPDATED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}