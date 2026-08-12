package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.CONTRACT_METADATA_UPDATED;

public class ContractMetadataUpdated extends OutboxEvent {
    private final Map<String, Object> data;

    private ContractMetadataUpdated(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ContractMetadataUpdated of(String groupId, String artifactId) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        return new ContractMetadataUpdated(id, groupId + "-" + artifactId, data);
    }

    @Override
    public String getType() {
        return CONTRACT_METADATA_UPDATED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
