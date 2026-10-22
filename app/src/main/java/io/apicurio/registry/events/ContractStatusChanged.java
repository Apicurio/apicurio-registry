package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.CONTRACT_STATUS_CHANGED;

public class ContractStatusChanged extends OutboxEvent {
    private final JSONObject eventPayload;

    private ContractStatusChanged(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ContractStatusChanged of(String groupId, String artifactId,
            String fromStatus, String toStatus) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id)
                .put("groupId", groupId)
                .put("artifactId", artifactId)
                .put("fromStatus", fromStatus != null ? fromStatus : "")
                .put("toStatus", toStatus)
                .put("eventType", CONTRACT_STATUS_CHANGED.name());
        return new ContractStatusChanged(id, groupId + "-" + artifactId, jsonObject);
    }

    @Override
    public String getType() {
        return CONTRACT_STATUS_CHANGED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}
