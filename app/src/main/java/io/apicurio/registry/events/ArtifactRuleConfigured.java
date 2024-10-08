package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_RULE_CONFIGURED;

public class ArtifactRuleConfigured extends OutboxEvent {
    private final JSONObject eventPayload;

    private ArtifactRuleConfigured(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactRuleConfigured of(String groupId, String artifactId, RuleType ruleType,
            RuleConfigurationDto rule) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("artifactId", artifactId)
                .put("ruleType", ruleType.value()).put("rule", rule.getConfiguration())
                .put("eventType", ARTIFACT_RULE_CONFIGURED.name());

        return new ArtifactRuleConfigured(id, groupId + "-" + artifactId, jsonObject);
    }

    @Override
    public String getType() {
        return ARTIFACT_RULE_CONFIGURED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}