package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ArtifactOrderMixin;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.interactive.InteractiveTable;
import io.apicurio.registry.cli.utils.Mapper;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.apicurio.registry.cli.utils.TableBuilder;
import io.apicurio.registry.cli.version.VersionCommand;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import static io.apicurio.registry.cli.common.IdUtil.displayGroupId;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_ID;
import static io.apicurio.registry.cli.utils.Columns.ARTIFACT_TYPE;
import static io.apicurio.registry.cli.utils.Columns.CREATED_ON;
import static io.apicurio.registry.cli.utils.Columns.DESCRIPTION;
import static io.apicurio.registry.cli.utils.Columns.GROUP_ID;
import static io.apicurio.registry.cli.utils.Columns.LABELS;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_BY;
import static io.apicurio.registry.cli.utils.Columns.MODIFIED_ON;
import static io.apicurio.registry.cli.utils.Columns.NAME;
import static io.apicurio.registry.cli.utils.Columns.OWNER;
import static io.apicurio.registry.cli.utils.Conversions.convert;
import static io.apicurio.registry.cli.utils.Conversions.convertToString;

/**
 * Lists artifacts in a group with pagination support.
 * When no group is specified, falls back to the context groupId or "default".
 */
@Command(
        name = "artifact",
        aliases = {"artifacts"},
        description = "Work with artifacts",
        subcommands = {
                ArtifactCreateCommand.class,
                ArtifactDeleteCommand.class,
                ArtifactGetCommand.class,
                ArtifactRuleCommand.class,
                ArtifactUpdateCommand.class,
                VersionCommand.class
        }
)
public class ArtifactCommand extends AbstractCommand {

    @Option(
            names = {"-g", "--group"},
            description = "Group ID. If not provided, uses the groupId from the current context, or 'default'."
    )
    private String groupId;

    @Mixin
    private ArtifactOrderMixin ordering;

    @Mixin
    private PaginationMixin pagination;

    @Mixin
    private OutputTypeMixin outputType;

    @Option(names = {"--interactive"}, description = "Launch interactive TUI mode.")
    private boolean interactive;

    @ParentCommand
    @Getter
    private Acr parent;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var registryClient = client.getRegistryClient();
        IdUtil.validateGroup(registryClient, resolvedGroupId);
        //noinspection ConstantConditions
        final var artifacts = convert(registryClient
                .groups().byGroupId(resolvedGroupId).artifacts().get(r -> {
                    //noinspection ConstantConditions
                    r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
                    r.queryParameters.limit = pagination.getSize();
                    r.queryParameters.orderby = ordering.getOrderBy();
                    r.queryParameters.order = ordering.getOrder();
                }));
        output.writeStdOutChunkWithException(out -> {
            switch (outputType.getOutputType()) {
                case json -> {
                    out.append(Mapper.MAPPER.writeValueAsString(artifacts));
                    out.append('\n');
                }
                case table -> {
                    final var table = new TableBuilder();
                    table.addColumns(
                            GROUP_ID,
                            ARTIFACT_ID,
                            NAME,
                            ARTIFACT_TYPE,
                            DESCRIPTION,
                            CREATED_ON,
                            OWNER,
                            MODIFIED_ON,
                            MODIFIED_BY,
                            LABELS
                    );
                    artifacts.getArtifacts().forEach(a -> {
                        table.addRow(
                                displayGroupId(a.getGroupId()),
                                a.getArtifactId(),
                                a.getName(),
                                a.getArtifactType(),
                                a.getDescription(),
                                convertToString(a.getCreatedOn()),
                                a.getOwner(),
                                convertToString(a.getModifiedOn()),
                                a.getModifiedBy(),
                                convertToString(a.getLabels())
                        );
                    });
                    table.setPagination(pagination.getPage(), pagination.getSize(), artifacts.getCount());
                    table.print(out);
                }
            }
        });
    }

    @Override
    public boolean supportsInteractive() {
        return true;
    }

    @Override
    public void runInteractive() throws Exception {
        final var resolvedGroupId = IdUtil.resolveGroupId(groupId, config);
        final var registryClient = client.getRegistryClient();
        IdUtil.validateGroup(registryClient, resolvedGroupId);
        //noinspection ConstantConditions
        final var results = convert(registryClient
                .groups().byGroupId(resolvedGroupId).artifacts().get(r -> {
                    //noinspection ConstantConditions
                    r.queryParameters.offset = (pagination.getPage() - 1) * pagination.getSize();
                    r.queryParameters.limit = pagination.getSize();
                    r.queryParameters.orderby = ordering.getOrderBy();
                    r.queryParameters.order = ordering.getOrder();
                }));
        final var artifacts = Optional.ofNullable(results.getArtifacts()).orElse(List.of());
        if (artifacts.isEmpty()) {
            System.out.println("No artifacts found.");
            return;
        }

        var table = new InteractiveTable<>(artifacts,
                a -> Optional.ofNullable(a.getName()).orElse(a.getArtifactId()) + "  " + a.getArtifactType() + "  " + a.getCreatedOn());
        var selected = table.run();
        if (selected == null) {
            return;
        }

        var a = selected.row();
        switch (selected.action()) {
            case VIEW -> {
                System.out.println("Group:        " + displayGroupId(a.getGroupId()));
                System.out.println("Artifact ID:  " + a.getArtifactId());
                System.out.println("Name:         " + Optional.ofNullable(a.getName()).orElse(a.getArtifactId()));
                System.out.println("Type:         " + a.getArtifactType());
                System.out.println("Description:  " + Optional.ofNullable(a.getDescription()).orElse(""));
                System.out.println("Created:      " + a.getCreatedOn());
            }
            case DELETE -> {
                var deleteGroupId = Optional.ofNullable(a.getGroupId()).orElse(resolvedGroupId);
                registryClient.groups().byGroupId(deleteGroupId)
                        .artifacts().byArtifactId(a.getArtifactId()).delete();
                System.out.println("Artifact '" + a.getArtifactId() + "' in group '"
                        + deleteGroupId + "' deleted successfully.");
            }
        }
    }
}