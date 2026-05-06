package io.apicurio.registry.cli.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.utils.Columns.CONFIG;
import static io.apicurio.registry.cli.utils.Columns.FIELD;
import static io.apicurio.registry.cli.utils.Columns.RULE_TYPE;
import static io.apicurio.registry.cli.utils.Columns.VALUE;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

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

    private static final String DEFAULT_GROUP = "default";

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

    public static void rejectDefaultGroup(final String groupId) {
        if (DEFAULT_GROUP.equals(groupId)) {
            throw new CliException(
                    "Group rules are not available for the 'default' group. Use global rules or specify a custom group with -g.",
                    VALIDATION_ERROR_RETURN_CODE
            );
        }
    }

    public static void printRuleTypes(final List<RuleType> ruleTypes, final OutputBuffer output,
                                        final OutputTypeMixin outputType) throws JsonProcessingException {
        final List<String> ruleTypeNames = ruleTypes != null
                ? ruleTypes.stream().map(RuleType::getValue).toList()
                : List.of();
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(ruleTypeNames));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(RULE_TYPE);
                    ruleTypeNames.forEach(table::addRow);
                    table.print(out);
                }
            }
        });
    }

    public static void printRule(final OutputBuffer output, final Rule rule, final OutputTypeMixin outputType) throws JsonProcessingException {
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(rule));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(FIELD, VALUE);
                    table.addRow(RULE_TYPE, rule.getRuleType() != null ? rule.getRuleType().value() : "");
                    table.addRow(CONFIG, rule.getConfig());
                    table.print(out);
                }
            }
        });
    }
}
