package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableBuilderTest {

    private static String render(TableBuilder table) {
        var out = new StringBuilder();
        table.print(out);
        return out.toString();
    }

    /** Returns the cell values of the given (0-based) output line, split on the column padding. */
    private static String[] row(String output, int lineIndex) {
        return output.split("\n")[lineIndex].trim().split("\\s{2,}");
    }

    @Test
    void selectColumnsKeepsOnlyRequestedColumnsInRequestedOrder() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Artifact ID", "Name")
                .addRow("g1", "a1", "n1")
                .selectColumns(List.of("name", "groupId")));
        assertThat(row(output, 0)).containsExactly("Name", "Group ID");
        // Row data follows the selected columns.
        assertThat(row(output, 2)).containsExactly("n1", "g1");
    }

    @Test
    void selectColumnsMatchesHeadersCaseInsensitively() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Created On")
                .addRow("g1", "2024-01-01")
                .selectColumns(List.of("CREATEDON", "group id")));
        assertThat(row(output, 0)).containsExactly("Created On", "Group ID");
    }

    @Test
    void selectColumnsIgnoresDuplicateRequests() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Name")
                .addRow("g1", "n1")
                .selectColumns(List.of("name", "name")));
        assertThat(row(output, 0)).containsExactly("Name");
    }

    @Test
    void selectColumnsWithUnknownNameThrowsValidationError() {
        var table = new TableBuilder().addColumns("Group ID", "Name").addRow("g1", "n1");
        var ex = assertThrows(CliException.class, () -> table.selectColumns(List.of("bogus")));
        assertEquals(VALIDATION_ERROR_RETURN_CODE, ex.getCode());
        assertThat(ex.getMessage()).contains("bogus").contains("Group ID").contains("Name");
    }

    @Test
    void selectColumnsWithNullOrEmptyLeavesTableUnchanged() {
        var nullSelection = render(new TableBuilder().addColumns("Group ID", "Name")
                .addRow("g1", "n1").selectColumns(null));
        assertThat(row(nullSelection, 0)).containsExactly("Group ID", "Name");

        var emptySelection = render(new TableBuilder().addColumns("Group ID", "Name")
                .addRow("g1", "n1").selectColumns(List.of()));
        assertThat(row(emptySelection, 0)).containsExactly("Group ID", "Name");
    }
}
