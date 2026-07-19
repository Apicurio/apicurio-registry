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

        assertThat(output).isEqualTo("""
                ID    NAME
                ---   -----
                1     alpha
                2     beta
                -----------
                """);
    }

    @Test
    public void testWrapsCellContentWhenExceedingMaxWidth() {
        var output = print(new TableBuilder()
                .setMaxWidth(30)
                .addColumns("ID", "DESCRIPTION")
                .addRow("1", "a very long description here"));

        // Content is wrapped across lines within the column, never truncated.
        assertThat(output).doesNotContain("...");
        assertThat(output.replace(" ", "").replace("\n", "")).contains("averylongdescriptionhere");
        output.lines().forEach(line ->
                assertThat(line.stripTrailing().length()).isLessThanOrEqualTo(30));
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
        // The 100 characters wrap across lines of at most 34, with nothing lost.
        assertThat(output).contains("x".repeat(34));
        assertThat(output).doesNotContain("x".repeat(35));
        assertThat(output.chars().filter(c -> c == 'x').count()).isEqualTo(100);
    }

    @Test
    public void testWrappingPreservesEmbeddedLineBreaks() {
        var output = print(new TableBuilder()
                .setMaxWidth(20)
                .addColumns("ID", "NOTES")
                .addRow("1", "short\n" + "z".repeat(30)));

        assertThat(output).contains("short");
        assertThat(output).doesNotContain("...");
        assertThat(output.chars().filter(c -> c == 'z').count()).isEqualTo(30);
    }

    @Test
    public void testLinesNeverExceedMaxWidthOrEndWithSpaces() {
        var output = print(new TableBuilder()
                .setMaxWidth(40)
                .addColumns("ID", "NAME", "DESCRIPTION")
                .addRow("1", "alpha", "d".repeat(80))
                .addRow("2", "beta", "short"));

        // Lines padded up to the terminal width would overflow it by the trailing
        // separator, making the terminal wrap in a blank line after every row.
        output.lines().forEach(line -> {
            assertThat(line).isEqualTo(line.stripTrailing());
            assertThat(line.length()).isLessThanOrEqualTo(40);
        });
    }

    @Test
    public void testHeadersTruncateWithEllipsisWhenColumnShrinks() {
        var output = print(new TableBuilder()
                .setMaxWidth(30)
                .addColumns("ID", "DESCRIPTION_COLUMN_WITH_LONG_NAME")
                .addRow("1", "y".repeat(50)));

        // The header cannot wrap, so it is truncated; the cell content still wraps fully.
        assertThat(output.lines().findFirst().orElseThrow()).contains("...");
        assertThat(output.chars().filter(c -> c == 'y').count()).isEqualTo(50);
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
    public void testZeroMaxWidthRendersNaturalWidths() {
        var longValue = "y".repeat(200);
        var output = print(new TableBuilder()
                .setMaxWidth(0)
                .addColumns("ID", "VALUE")
                .addRow("1", longValue));

        // No width limit: the value stays on a single unwrapped line.
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
        assertThat(separatorLine).isEqualTo("---   ---");
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
