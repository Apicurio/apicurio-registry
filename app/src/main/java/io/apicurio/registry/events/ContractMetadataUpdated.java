package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.CONTRACT_METADATA_UPDATED;

public class ContractMetadataUpdated extends OutboxEvent {
    private final JSONObject eventPayload;

    private ContractMetadataUpdated(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ContractMetadataUpdated of(String groupId, String artifactId) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id)
                .put("groupId", groupId)
                .put("artifactId", artifactId)
                .put("eventType", CONTRACT_METADATA_UPDATED.name());
        return new ContractMetadataUpdated(id, groupId + "-" + artifactId, jsonObject);
    }

    @Override
    public String getType() {
        return CONTRACT_METADATA_UPDATED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}
