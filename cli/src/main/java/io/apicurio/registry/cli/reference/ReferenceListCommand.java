package io.apicurio.registry.cli.reference;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.ReferenceType;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.REFERENCE_NAME;
import static io.apicurio.registry.cli.utils.Columns.VERSION;
import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@Command(
        name = "list",
        aliases = {"ls"},
        description = "List references for an artifact version"
)
public class ReferenceListCommand extends AbstractCommand {

    @Parameters(
            index = "0",
            description = "The version expression (e.g. '1.0.0', 'branch=latest')."
    )
    private String versionExpression;

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

    @Option(
            names = {"--inbound"},
            description = "List inbound references instead of outbound."
    )
    private boolean inbound;

    @Mixin
    private OutputTypeMixin outputType;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var resolvedArtifactId = IdUtil.resolveArtifactId(artifactId, config);
        final var registryClient = client.getRegistryClient();
        IdUtil.validateGroup(registryClient, resolvedGroupId);
        IdUtil.validateArtifact(registryClient, resolvedGroupId, resolvedArtifactId);

        final var refType = inbound ? ReferenceType.INBOUND : ReferenceType.OUTBOUND;

        final var references = Optional.ofNullable(
                registryClient.groups().byGroupId(resolvedGroupId)
                        .artifacts().byArtifactId(resolvedArtifactId)
                        .versions().byVersionExpression(versionExpression)
                        .references().get(r -> r.queryParameters.refType = refType)
        ).orElse(List.of());

        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(MAPPER.writeValueAsString(references));
                    out.append('\n');
                }
                case table -> {
                    if (references.isEmpty()) {
                        out.append("No references found.\n");
                    } else {
                        final var table = new TableBuilder();
                        table.addColumns(GROUP_ID, ARTIFACT_ID, VERSION, REFERENCE_NAME);
                        for (final ArtifactReference ref : references) {
                            table.addRow(ref.getGroupId(), ref.getArtifactId(),
                                    ref.getVersion(), ref.getName());
                        }
                        table.print(out);
                    }
                }
            }
        });
    }
}
