package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.CONTRACT_RULESET_CONFIGURED;

public class ContractRulesetConfigured extends OutboxEvent {
    private final JSONObject eventPayload;

    private ContractRulesetConfigured(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ContractRulesetConfigured of(String groupId, String artifactId,
            String version, String action) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id)
                .put("groupId", groupId)
                .put("artifactId", artifactId)
                .put("action", action)
                .put("eventType", CONTRACT_RULESET_CONFIGURED.name());
        if (version != null) {
            jsonObject.put("version", version);
        }
        return new ContractRulesetConfigured(id, groupId + "-" + artifactId, jsonObject);
    }

    @Override
    public String getType() {
        return CONTRACT_RULESET_CONFIGURED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}
