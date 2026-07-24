/*
 * Copyright 2026 Red Hat
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

package io.apicurio.registry.cli.artifact;

import io.apicurio.registry.cli.Acr;
import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.ArtifactOrderMixin;
import io.apicurio.registry.cli.common.ColumnsMixin;
import io.apicurio.registry.cli.common.IdUtil;
import io.apicurio.registry.cli.common.OutputTypeMixin;
import io.apicurio.registry.cli.common.PaginationMixin;
import io.apicurio.registry.cli.interactive.InteractiveTable;
import io.apicurio.registry.cli.interactive.InteractiveTable.PageResult;
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
    
    @Mixin
    private ColumnsMixin columns;

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
                    table.setSelectedColumns(columns.getColumns());
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
        final var initialResults = convert(registryClient
                .groups().byGroupId(resolvedGroupId).artifacts().get(r -> {
                    //noinspection ConstantConditions
                    r.queryParameters.offset = 0;
                    r.queryParameters.limit = pagination.getSize();
                    r.queryParameters.orderby = ordering.getOrderBy();
                    r.queryParameters.order = ordering.getOrder();
                }));
        final var initialRows = Optional.ofNullable(initialResults.getArtifacts()).orElse(List.of());

        var table = new InteractiveTable<>(
                initialRows,
                a -> Optional.ofNullable(a.getName()).orElse(a.getArtifactId()) + "  " + a.getArtifactType() + "  " + a.getCreatedOn(),
                page -> {
                    //noinspection ConstantConditions
                    final var pageResults = convert(registryClient
                            .groups().byGroupId(resolvedGroupId).artifacts().get(r -> {
                                //noinspection ConstantConditions
                                r.queryParameters.offset = (page - 1) * pagination.getSize();
                                r.queryParameters.limit = pagination.getSize();
                                r.queryParameters.orderby = ordering.getOrderBy();
                                r.queryParameters.order = ordering.getOrder();
                            }));
                    final var pageRows = Optional.ofNullable(pageResults.getArtifacts()).orElse(List.of());
                    final var hasNext = (page * pagination.getSize()) < pageResults.getCount();
                    return new PageResult<>(pageRows, hasNext);
                }
        );

        var selected = table.run();
        if (selected == null) {
            return;
        }

        var a = selected.row();
        if (selected.action() == InteractiveTable.Action.VIEW) {
            config.getStdOut().print("Group:        " + displayGroupId(a.getGroupId()) + "\n");
            config.getStdOut().print("Artifact ID:  " + a.getArtifactId() + "\n");
            config.getStdOut().print("Name:         " + Optional.ofNullable(a.getName()).orElse(a.getArtifactId()) + "\n");
            config.getStdOut().print("Type:         " + a.getArtifactType() + "\n");
            config.getStdOut().print("Description:  " + Optional.ofNullable(a.getDescription()).orElse("") + "\n");
            config.getStdOut().print("Created:      " + a.getCreatedOn() + "\n");
        } else if (selected.action() == InteractiveTable.Action.DELETE) {
            var deleteGroupId = Optional.ofNullable(a.getGroupId()).orElse(resolvedGroupId);
            registryClient.groups().byGroupId(deleteGroupId)
                    .artifacts().byArtifactId(a.getArtifactId()).delete();
            config.getStdOut().print("Artifact '" + a.getArtifactId() + "' in group '"
                    + deleteGroupId + "' deleted successfully.\n");
        }
    }
}