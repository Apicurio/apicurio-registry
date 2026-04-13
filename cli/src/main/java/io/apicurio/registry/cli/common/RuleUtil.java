package io.apicurio.registry.cli.common;

import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

public final class RuleUtil {

    private static final List<String> VALID_RULE_TYPES = Stream.of(RuleType.values())
            .map(RuleType::getValue)
            .toList();

    private static final Map<String, List<String>> VALID_CONFIGS = Map.of(
            RuleType.VALIDITY.getValue(), Stream.of(ValidityLevel.values())
                    .map(Enum::name).toList(),
            RuleType.COMPATIBILITY.getValue(), Stream.of(CompatibilityLevel.values())
                    .map(Enum::name).toList(),
            RuleType.INTEGRITY.getValue(), Stream.of(IntegrityLevel.values())
                    .map(Enum::name).toList()
    );

    private RuleUtil() {
    }

    public static void validateRuleType(final String ruleType) {
        if (!VALID_RULE_TYPES.contains(ruleType)) {
            throw new CliException(
                    "Invalid rule type '" + ruleType + "'. Valid values are: " + String.join(", ", VALID_RULE_TYPES) + ".",
                    VALIDATION_ERROR_RETURN_CODE
            );
        }
    }

    public static void validateRuleConfig(final String ruleType, final String config) {
        final var validConfigs = VALID_CONFIGS.get(ruleType);
        if (validConfigs != null && !validConfigs.contains(config)) {
            throw new CliException(
                    "Invalid config '" + config + "' for rule type '" + ruleType + "'. Valid values are: "
                            + String.join(", ", validConfigs) + ".",
                    VALIDATION_ERROR_RETURN_CODE
            );
        }
    }
}
