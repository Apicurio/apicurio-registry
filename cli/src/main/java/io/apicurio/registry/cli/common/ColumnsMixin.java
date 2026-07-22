package io.apicurio.registry.cli.common;

import java.util.List;
import lombok.Getter;
import picocli.CommandLine.Option;

/**
 * Mixin that adds a {@code --columns} option for choosing which columns are shown in table output.
 * The selection only affects the 'table' output type; other output formats are unaffected.
 */
public class ColumnsMixin {

    @Option(
            names = {"--columns"},
            paramLabel = "<column>",
            split = ",",
            description = "Comma-separated list of columns to display in table output, "
                    + "e.g. '--columns groupId,artifactId,name'. Column names are matched "
                    + "case-insensitively. Invalid names produce an error listing the valid columns. "
                    + "Only affects 'table' output."
    )
    @Getter
    private List<String> columns;
}
