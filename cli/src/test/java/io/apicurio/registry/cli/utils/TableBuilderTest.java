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
    void setSelectedColumnsKeepsOnlyRequestedColumnsInRequestedOrder() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Artifact ID", "Name")
                .addRow("g1", "a1", "n1")
                .setSelectedColumns(List.of("name", "groupId")));
        assertThat(row(output, 0)).containsExactly("Name", "Group ID");
        // Row data follows the selected columns.
        assertThat(row(output, 2)).containsExactly("n1", "g1");
    }

    @Test
    void setSelectedColumnsMatchesHeadersCaseInsensitively() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Created On")
                .addRow("g1", "2024-01-01")
                .setSelectedColumns(List.of("CREATEDON", "group id")));
        assertThat(row(output, 0)).containsExactly("Created On", "Group ID");
    }

    @Test
    void setSelectedColumnsIgnoresDuplicateRequests() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Name")
                .addRow("g1", "n1")
                .setSelectedColumns(List.of("name", "name")));
        assertThat(row(output, 0)).containsExactly("Name");
    }

    @Test
    void setSelectedColumnsIgnoresBlankEntries() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Name")
                .addRow("g1", "n1")
                .setSelectedColumns(List.of("", "name", "  ")));
        assertThat(row(output, 0)).containsExactly("Name");
    }

    @Test
    void setSelectedColumnsWithOnlyBlankEntriesLeavesTableUnchanged() {
        var output = render(new TableBuilder()
                .addColumns("Group ID", "Name")
                .addRow("g1", "n1")
                .setSelectedColumns(List.of("", "  ")));
        assertThat(row(output, 0)).containsExactly("Group ID", "Name");
    }

    @Test
    void setSelectedColumnsCalledTwiceLetsTheLastCallWin() {
        var table = new TableBuilder()
                .addColumns("Group ID", "Artifact ID", "Name")
                .addRow("g1", "a1", "n1")
                .setSelectedColumns(List.of("name"));
        // A column dropped by the first call is still selectable by a later one.
        var output = render(table.setSelectedColumns(List.of("groupId", "artifactId")));
        assertThat(row(output, 0)).containsExactly("Group ID", "Artifact ID");
        assertThat(row(output, 2)).containsExactly("g1", "a1");
    }

    @Test
    void setSelectedColumnsAfterAnEarlierSelectionStillValidatesAgainstEveryColumn() {
        var table = new TableBuilder().addColumns("Group ID", "Name").addRow("g1", "n1")
                .setSelectedColumns(List.of("name"));
        var ex = assertThrows(CliException.class, () -> table.setSelectedColumns(List.of("bogus")));
        assertThat(ex.getMessage()).contains("Group ID").contains("Name");
    }

    @Test
    void addRowAfterSetSelectedColumnsStillFillsEveryColumn() {
        var table = new TableBuilder().addColumns("Group ID", "Name")
                .setSelectedColumns(List.of("name"))
                .addRow("g1", "n1");
        var output = render(table.setSelectedColumns(List.of("groupId", "name")));
        assertThat(row(output, 2)).containsExactly("g1", "n1");
    }

    @Test
    void setSelectedColumnsMatchesHeadersContainingSpacesAndSymbols() {
        var output = render(new TableBuilder()
                .addColumns("Modified-By (UTC)", "Name")
                .addRow("alice", "n1")
                .setSelectedColumns(List.of("modifiedbyutc")));
        assertThat(row(output, 0)).containsExactly("Modified-By (UTC)");
        assertThat(row(output, 2)).containsExactly("alice");
    }

    @Test
    void setSelectedColumnsMatchesHeadersContainingUnicode() {
        var output = render(new TableBuilder()
                .addColumns("Café Länge", "Name")
                .addRow("42", "n1")
                .setSelectedColumns(List.of("CAFÉ LÄNGE")));
        assertThat(row(output, 0)).containsExactly("Café Länge");
        assertThat(row(output, 2)).containsExactly("42");
    }

    @Test
    void setSelectedColumnsRepeatedWithTheSameSelectionIsStable() {
        var table = new TableBuilder()
                .addColumns("Group ID", "Artifact ID", "Name")
                .addRow("g1", "a1", "n1");
        var first = render(table.setSelectedColumns(List.of("name", "groupId")));
        var second = render(table.setSelectedColumns(List.of("name", "groupId")));
        assertEquals(first, second);
    }

    @Test
    void setSelectedColumnsWithUnknownNameThrowsValidationError() {
        var table = new TableBuilder().addColumns("Group ID", "Name").addRow("g1", "n1");
        var ex = assertThrows(CliException.class, () -> table.setSelectedColumns(List.of("bogus")));
        assertEquals(VALIDATION_ERROR_RETURN_CODE, ex.getCode());
        assertThat(ex.getMessage()).contains("bogus").contains("Group ID").contains("Name");
    }

    @Test
    void setSelectedColumnsWithNullOrEmptyLeavesTableUnchanged() {
        var nullSelection = render(new TableBuilder().addColumns("Group ID", "Name")
                .addRow("g1", "n1").setSelectedColumns(null));
        assertThat(row(nullSelection, 0)).containsExactly("Group ID", "Name");

        var emptySelection = render(new TableBuilder().addColumns("Group ID", "Name")
                .addRow("g1", "n1").setSelectedColumns(List.of()));
        assertThat(row(emptySelection, 0)).containsExactly("Group ID", "Name");
    }
}
