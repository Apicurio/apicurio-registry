/*
 * Copyright 2020 IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.rules;

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class RulesPropertiesImpl implements RulesProperties {
    @SuppressWarnings("unused")
    private final Properties properties;
    private final Map<RuleType, String> defaultGlobalRules;

    public RulesPropertiesImpl(Properties properties) {
        this.properties = properties;
        this.defaultGlobalRules = properties.stringPropertyNames().stream()
            .collect(Collectors.toMap(rulePropertyName -> RuleType.fromValue(rulePropertyName.toUpperCase()), properties::getProperty));
    }

    @Override
    public List<RuleType> getFilteredDefaultGlobalRules(List<RuleType> excludeRulesFilter) {
        return defaultGlobalRules.keySet().stream()
            .filter(ruleType -> excludeRulesFilter == null || !excludeRulesFilter.contains(ruleType))
            .collect(Collectors.toList());
    }

    @Override
    public boolean isDefaultGlobalRuleConfigured(RuleType ruleType) {
        return defaultGlobalRules.containsKey(ruleType);
    }

    @Override
    public RuleConfigurationDto getDefaultGlobalRuleConfiguration(RuleType ruleType) {
        RuleConfigurationDto ruleConfigurationDto = null;
        if(defaultGlobalRules.containsKey(ruleType)) {
            ruleConfigurationDto = new RuleConfigurationDto();
            ruleConfigurationDto.setConfiguration(defaultGlobalRules.get(ruleType));
        }
        return ruleConfigurationDto;
    }
}
