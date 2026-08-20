package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.common.OutputType;
import io.apicurio.registry.cli.interactive.InteractiveTable;
import io.apicurio.registry.cli.interactive.InteractiveTable.PageResult;
import io.apicurio.registry.rest.v3.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v3.beans.SearchedArtifact;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

import static io.apicurio.registry.cli.utils.Mapper.MAPPER;

@RegisterForReflection
public final class InteractiveUtil {

    private InteractiveUtil() {
    }

    public static final Function<SearchedArtifact, String> ARTIFACT_ROW_RENDERER =
            a -> Optional.ofNullable(a.getName()).orElse(a.getArtifactId()) + "  " + a.getArtifactType() + "  " + Conversions.convertToString(a.getCreatedOn());

    public static final Function<SearchedArtifact, String> ARTIFACT_ROW_SEARCHER =
            a -> a.getArtifactId() + " " + Optional.ofNullable(a.getName()).orElse("") + " "
                    + Optional.ofNullable(a.getGroupId()).orElse("") + " " + Optional.ofNullable(a.getArtifactType()).orElse("") + " "
                    + Optional.ofNullable(a.getDescription()).orElse("");

    /**
     * Shared runner for artifact-oriented interactive table commands.
     */
    public static void runInteractive(
            IntFunction<ArtifactSearchResults> pageFetcher,
            Consumer<SearchedArtifact> deleter,
            Function<SearchedArtifact, String> groupIdDisplay,
            int pageSize,
            OutputType outputType,
            OutputBuffer output
    ) {
        final var initialResults = pageFetcher.apply(1);
        final var initialRows = Optional.ofNullable(initialResults.getArtifacts()).orElse(List.of());
        final var initialHasNext = pageSize < initialResults.getCount();

        var table = new InteractiveTable<SearchedArtifact>(
                initialRows,
                ARTIFACT_ROW_RENDERER,
                ARTIFACT_ROW_SEARCHER,
                page -> {
                    ArtifactSearchResults pageResults = pageFetcher.apply(page);
                    final var pageRows = Optional.ofNullable(pageResults.getArtifacts()).orElse(List.of());
                    final var hasNext = ((long) page * pageSize) < pageResults.getCount();
                    return new PageResult<>(pageRows, hasNext);
                },
                initialHasNext,
                deleter
        );

        var selected = table.run();
        if (selected == null) {
            return;
        }

        var a = selected.row();
        if (selected.action() == InteractiveTable.Action.VIEW) {
            if (outputType == OutputType.json) {
                output.writeStdOutChunk(out -> {
                    try {
                        out.append(MAPPER.writeValueAsString(a)).append('\n');
                    } catch (Exception e) {
                        throw new CliException("Failed to serialize artifact to JSON", e, CliException.APPLICATION_ERROR_RETURN_CODE);
                    }
                });
            } else {
                output.writeStdOutChunk(sb -> printArtifactDetails(a, sb, groupIdDisplay.apply(a)));
            }
        }
    }

    public static void printArtifactDetails(SearchedArtifact a, StringBuilder stdout, String groupIdDisplay) {
        stdout.append("Group:        ").append(groupIdDisplay).append("\n");
        stdout.append("Artifact ID:  ").append(a.getArtifactId()).append("\n");
        stdout.append("Name:         ").append(Optional.ofNullable(a.getName()).orElse(a.getArtifactId())).append("\n");
        stdout.append("Type:         ").append(a.getArtifactType()).append("\n");
        stdout.append("Description:  ").append(Optional.ofNullable(a.getDescription()).orElse("")).append("\n");
        stdout.append("Created:      ").append(Conversions.convertToString(a.getCreatedOn())).append("\n");
    }
}
