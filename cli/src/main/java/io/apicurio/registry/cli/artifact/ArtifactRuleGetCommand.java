package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.common.IdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.common.CliException.exitQuietServerError;
import static io.apicurio.registry.cli.common.RuleUtil.printRule;
import static io.apicurio.registry.cli.common.RuleUtil.validateRuleType;
import static io.apicurio.registry.cli.utils.Conversions.convert;

@Command(
        name = "get",
        description = "Get the configuration of an artifact rule"
)
public class ArtifactRuleGetCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Option(
            names = {"-a", "--artifact"},
            description = "Artifact ID. If not provided, uses the artifactId from the current context."
    )
    private String artifactId;

    @Parameters(
            index = "0",
            description = "The rule type (VALIDITY, COMPATIBILITY, or INTEGRITY)"
    )
    private String ruleType;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws JsonProcessingException {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        validateRuleType(ruleType);
        try {
            final var registryClient = client.getRegistryClient();
            IdUtil.validateGroup(registryClient, resolvedGroupId);
            //noinspection ConstantConditions
            final var rule = convert(registryClient.groups().byGroupId(resolvedGroupId)
                    .artifacts().byArtifactId(resolvedArtifactId).rules().byRuleType(ruleType).get());
            printRule(output, rule, outputType);
        } catch (final ProblemDetails ex) {
            output.writeStdErrChunk(err -> {
                err.append("Error retrieving rule '")
                        .append(ruleType)
                        .append("' for artifact '")
                        .append(resolvedArtifactId)
                        .append("' in group '")
                        .append(resolvedGroupId)
                        .append("': ")
                        .append(ex.getDetail())
                        .append('\n');
            });
            exitQuietServerError();
        }
    }
}
