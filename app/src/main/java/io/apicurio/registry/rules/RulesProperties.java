package io.apicurio.registry.rules;

import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;

import java.util.Set;

/**
 * A service used to retrieve the default global rules that have been set via registry.rules.global
 * configuration properties. E.g.: <code>
 * %prod.registry.rules.global.compatibility=BACKWARDS
 * %prod.registry.rules.global.validity=FULL
 * </code>
 */
public interface RulesProperties {

    /**
     * Get the list of configured default global RuleType enums.
     *
     * @return The list of configured default global RuleTypes.
     */
    Set<RuleType> getDefaultGlobalRules();

    /**
     * Whether the supplied RuleType has been configured as a global rule.
     *
     * @return true if the a default global rule has been configured for the supplied RuleType, false
     *         otherwise.
     */
    boolean isDefaultGlobalRuleConfigured(RuleType ruleType);

    /**
     * Get the default global RuleConfigurationDto for the supplied RuleType.
     *
     * @return The default global RuleConfigurationDto for the supplied RuleType or null if the RuleType has
     *         not been configured.
     */
    RuleConfigurationDto getDefaultGlobalRuleConfiguration(RuleType ruleType);

}
