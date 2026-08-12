package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GROUP_RULE_CONFIGURED;

public class GroupRuleConfigured extends OutboxEvent {
    private final Map<String, Object> data;

    private GroupRuleConfigured(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static GroupRuleConfigured of(String groupId, RuleType ruleType, RuleConfigurationDto rule) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("ruleType", ruleType.value());
        data.put("configuration", rule.getConfiguration());
        return new GroupRuleConfigured(id, groupId, data);
    }

    @Override
    public String getType() {
        return GROUP_RULE_CONFIGURED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
