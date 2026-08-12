package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.CONTRACT_STATUS_CHANGED;

public class ContractStatusChanged extends OutboxEvent {
    private final Map<String, Object> data;

    private ContractStatusChanged(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ContractStatusChanged of(String groupId, String artifactId,
            String fromStatus, String toStatus) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        data.put("fromStatus", fromStatus);
        data.put("toStatus", toStatus);
        return new ContractStatusChanged(id, groupId + "-" + artifactId, data);
    }

    @Override
    public String getType() {
        return CONTRACT_STATUS_CHANGED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
