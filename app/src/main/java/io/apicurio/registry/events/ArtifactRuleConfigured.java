package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_RULE_CONFIGURED;

public class ArtifactRuleConfigured extends OutboxEvent {
    private final Map<String, Object> data;

    private ArtifactRuleConfigured(String id, String aggregateId, Map<String, Object> data) {
        super(id, aggregateId);
        this.data = data;
    }

    public static ArtifactRuleConfigured of(String groupId, String artifactId, RuleType ruleType,
            RuleConfigurationDto rule) {
        String id = UUID.randomUUID().toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        data.put("ruleType", ruleType.value());
        data.put("configuration", rule.getConfiguration());
        return new ArtifactRuleConfigured(id, groupId + "-" + artifactId, data);
    }

    @Override
    public String getType() {
        return ARTIFACT_RULE_CONFIGURED.name();
    }

    @Override
    public Object getPayload() {
        return data;
    }
}
