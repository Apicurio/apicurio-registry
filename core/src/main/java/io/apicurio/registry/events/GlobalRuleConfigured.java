package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GLOBAL_RULE_CONFIGURED;

public class GlobalRuleConfigured extends OutboxEvent {
    private final JSONObject eventPayload;

    private GlobalRuleConfigured(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static GlobalRuleConfigured of(RuleType ruleType, RuleConfigurationDto rule) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("ruleType", ruleType.value()).put("rule", rule.getConfiguration())
                .put("eventType", GLOBAL_RULE_CONFIGURED.name());

        return new GlobalRuleConfigured(id, ruleType.value(), jsonObject);
    }

    @Override
    public String getType() {
        return GLOBAL_RULE_CONFIGURED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}