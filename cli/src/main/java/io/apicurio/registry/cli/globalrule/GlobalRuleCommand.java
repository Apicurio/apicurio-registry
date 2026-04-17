package io.apicurio.registry.cli.globalrule;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleType;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParentCommand;

import java.util.List;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.utils.Columns.RULE_TYPE;

@Command(
        name = "rule",
        aliases = {"rules"},
        description = "Work with global rules",
        subcommands = {
                GlobalRuleCreateCommand.class,
                GlobalRuleGetCommand.class,
                GlobalRuleUpdateCommand.class,
                GlobalRuleDeleteCommand.class
        }
)
public class GlobalRuleCommand extends AbstractCommand {

    @Mixin
    private OutputTypeMixin outputType;

    @ParentCommand
    @Getter
    private Acr parent;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        try {
            final var ruleTypes = client.getRegistryClient().admin().rules().get();
            final List<String> ruleTypeNames = ruleTypes != null
                    ? ruleTypes.stream().map(RuleType::getValue).toList()
                    : List.of();
            output.writeStdOutChunkWithException(out -> {
                switch (outputType.getOutputType()) {
                    case json -> {
                        out.append(Mapper.MAPPER.writeValueAsString(ruleTypeNames));
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
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error listing global rules: ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
