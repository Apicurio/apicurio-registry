package io.apicurio.registry.rules;

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class RulesPropertiesImpl implements RulesProperties {
    @SuppressWarnings("unused")
    private final Properties properties;
    private final Map<RuleType, String> defaultGlobalRules;

    public RulesPropertiesImpl(Properties properties) {
        this.properties = properties;
        this.defaultGlobalRules = properties.stringPropertyNames().stream()
                .collect(Collectors.toMap(
                        rulePropertyName -> RuleType.fromValue(rulePropertyName.toUpperCase()),
                        properties::getProperty));
    }

    @Override
    public Set<RuleType> getDefaultGlobalRules() {
        return defaultGlobalRules.keySet();
    }

    @Override
    public boolean isDefaultGlobalRuleConfigured(RuleType ruleType) {
        return defaultGlobalRules.containsKey(ruleType);
    }

    @Override
    public RuleConfigurationDto getDefaultGlobalRuleConfiguration(RuleType ruleType) {
        RuleConfigurationDto ruleConfigurationDto = null;
        if (defaultGlobalRules.containsKey(ruleType)) {
            ruleConfigurationDto = new RuleConfigurationDto();
            ruleConfigurationDto.setConfiguration(defaultGlobalRules.get(ruleType));
        }
        return ruleConfigurationDto;
    }
}
