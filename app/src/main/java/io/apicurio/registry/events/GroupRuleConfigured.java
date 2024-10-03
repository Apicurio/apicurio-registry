package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_RULE_CONFIGURED;

public class GroupRuleConfigured extends OutboxEvent {
    private final JSONObject eventPayload;

    private GroupRuleConfigured(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static GroupRuleConfigured of(String groupId, RuleType ruleType, RuleConfigurationDto rule) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id).put("groupId", groupId).put("ruleType", ruleType.value())
                .put("rule", rule.getConfiguration()).put("eventType", GROUP_RULE_CONFIGURED.name());

        return new GroupRuleConfigured(id, groupId, jsonObject);
    }

    @Override
    public String getType() {
        return GROUP_RULE_CONFIGURED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}