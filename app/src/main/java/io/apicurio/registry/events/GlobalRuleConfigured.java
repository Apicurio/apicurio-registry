package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.GLOBAL_RULE_CONFIGURED;

public class GlobalRuleConfigured extends OutboxEvent {
    private final Map<String, Object> data;

    private GlobalRuleConfigured(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static GlobalRuleConfigured of(RuleType ruleType, RuleConfigurationDto rule) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ruleType", ruleType.value());
        data.put("configuration", rule.getConfiguration());
        return new GlobalRuleConfigured(id, ruleType.value(), data);
    }

    @Override
    public String getType() {
        return GLOBAL_RULE_CONFIGURED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
