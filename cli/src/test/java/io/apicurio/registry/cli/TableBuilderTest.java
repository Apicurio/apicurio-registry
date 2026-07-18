package io.apicurio.registry.cli;

import io.apicurio.registry.cli.utils.TableBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TableBuilderTest {

    private static String print(TableBuilder table) {
        var out = new StringBuilder();
        table.print(out);
        return out.toString();
    }

    @Test
    public void testNaturalWidthsWhenTableFits() {
        var output = print(new TableBuilder()
                .setMaxWidth(80)
                .addColumns("ID", "NAME")
                .addRow("1", "alpha")
                .addRow("2", "beta"));

        assertThat(output).isEqualTo(
                "ID    NAME    \n"
                        + "---   -----   \n"
                        + "1     alpha   \n"
                        + "2     beta    \n"
                        + "-----------\n");
    }

    @Test
    public void testTruncatesWithEllipsisWhenExceedingMaxWidth() {
        var output = print(new TableBuilder()
                .setMaxWidth(30)
                .addColumns("ID", "DESCRIPTION")
                .addRow("1", "a very long description here"));

        assertThat(output).contains("a very long descripti...");
        assertThat(output).doesNotContain("a very long description here");
    }

    @Test
    public void testNarrowColumnsKeepNaturalWidthWhenOthersShrink() {
        var output = print(new TableBuilder()
                .setMaxWidth(40)
                .addColumns("ID", "VALUE")
                .addRow("42", "x".repeat(100)));

        var separatorLine = output.lines().skip(1).findFirst().orElseThrow();
        var dashes = separatorLine.split(" {3}");
        // The narrow column keeps its natural width (header "ID" padded to the minimum of 3).
        assertThat(dashes[0]).isEqualTo("---");
        // The wide column gets the remaining space: 40 - 3 (separator) - 3 (ID column) = 34.
        assertThat(dashes[1]).isEqualTo("-".repeat(34));
        assertThat(output).contains("x".repeat(31) + "...");
    }

    @Test
    public void testMultiLineCellsArePreserved() {
        var output = print(new TableBuilder()
                .setMaxWidth(80)
                .addColumns("ID", "NOTES")
                .addRow("1", "first line\nsecond line"));

        assertThat(output).contains("first line");
        assertThat(output).contains("second line");
        // Both lines belong to the same row, so the ID column is blank on the second line.
        assertThat(output.lines().filter(l -> l.contains("second line")).findFirst().orElseThrow())
                .startsWith("      second line");
    }

    @Test
    public void testZeroMaxWidthDisablesTruncation() {
        var longValue = "y".repeat(200);
        var output = print(new TableBuilder()
                .setMaxWidth(0)
                .addColumns("ID", "VALUE")
                .addRow("1", longValue));

        assertThat(output).contains(longValue);
        assertThat(output).doesNotContain("...");
    }

    @Test
    public void testVeryNarrowMaxWidthClampsToMinimumColumnWidth() {
        var output = print(new TableBuilder()
                .setMaxWidth(5)
                .addColumns("FIRST", "SECOND")
                .addRow("aaaaaaaaaa", "bbbbbbbbbb"));

        // Every column is clamped to the minimum width of 3, never less.
        var separatorLine = output.lines().skip(1).findFirst().orElseThrow();
        assertThat(separatorLine).isEqualTo("---   ---   ");
    }

    @Test
    public void testPaginationFooter() {
        var output = print(new TableBuilder()
                .setMaxWidth(80)
                .addColumns("ID")
                .addRow("1")
                .setPagination(1, 2, 3));

        assertThat(output).endsWith("Page 1/2, total 3 rows.\n");
    }

    @Test
    public void testEmptyTablePrintsNothing() {
        assertThat(print(new TableBuilder().setMaxWidth(80))).isEmpty();
    }
}
