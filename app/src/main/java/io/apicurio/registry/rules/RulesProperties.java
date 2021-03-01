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

/**
 * A service used to retrieve the default global rules that have been set via registry.rules.global configuration
 * properties. E.g.:
 * <code>
 * %prod.registry.rules.global.compatibility=BACKWARDS
 * %prod.registry.rules.global.validity=FULL
 * </code>
 */
public interface RulesProperties {

    /**
     * Get the list of configured default global RuleType enums. A list of RuleType enums can be supplied that will
     * be filtered out of the returned list.
     *
     * @param excludeRulesFilter a list of RuleType enums to filter from the returned list. If null, the entire
     *                     configured list of default global RuleTypes is returned.
     * @return The list of configured default global RuleTypes with any matching the excludeRules list removed.
     */
    List<RuleType> getFilteredDefaultGlobalRules(List<RuleType> excludeRulesFilter);

    /**
     * Whether the supplied RuleType has been configured as a global rule.
     *
     * @return true if the a default global rule has been configured for the supplied RuleType, false otherwise.
     */
    boolean isDefaultGlobalRuleConfigured(RuleType ruleType);

    /**
     * Get the default global RuleConfigurationDto for the supplied RuleType.
     *
     * @return The default global RuleConfigurationDto for the supplied RuleType or null if the RuleType has
     * not been configured.
     */
    RuleConfigurationDto getDefaultGlobalRuleConfiguration(RuleType ruleType);

}
