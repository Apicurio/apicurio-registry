package io.apicurio.registry.cli.utils;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.stream;

/**
 * A fluent interface builder for creating formatted ASCII tables.
 * <p>
 * Tables adapt to the terminal width: columns wider than their fair share of the available
 * width are shrunk, and cell content is wrapped across multiple lines to fit its column, so
 * no content is lost. Headers that do not fit their column are truncated with an ellipsis.
 * <p>
 * Known limitation: widths are measured in UTF-16 code units ({@link String#length()}), not
 * display columns, so content with CJK characters (2 columns wide) or emoji (surrogate
 * pairs) may misalign. Acceptable for the ASCII-heavy identifiers the registry works with.
 */
public class TableBuilder {

    private static final int MIN_COLUMN_WIDTH = 3;
    private static final String COLUMN_SEPARATOR = "   ";
    private static final String ELLIPSIS = "...";

    // Invariant: Number of cells in every column must be the same.
    private final List<Column> columns = new ArrayList<>();

    private Pagination pagination;

    private int maxWidth = TerminalUtils.getTerminalWidth();

    public TableBuilder addColumn(String header) {
        columns.add(new Column(header != null ? header : ""));
        return this;
    }

    public TableBuilder addColumns(String... headers) {
        stream(headers).forEach(this::addColumn);
        return this;
    }

    public TableBuilder addRow(String... values) {
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).addCell(i < values.length && values[i] != null ? values[i] : "");
        }
        return this;
    }

    public TableBuilder setPagination(int page, int size, int total) {
        this.pagination = new Pagination(page, size, total);
        return this;
    }

    /**
     * Overrides the detected terminal width. A value of zero or less disables the
     * width limit, so every column is rendered at its natural width.
     */
    public TableBuilder setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
        return this;
    }

    /**
     * Builds and prints the formatted table to the provided StringBuilder.
     */
    public void print(StringBuilder out) {
        if (columns.isEmpty()) {
            return;
        }

        var widths = computeColumnWidths();

        // Print headers
        for (int i = 0; i < columns.size(); i++) {
            out.append(fit(columns.get(i).getHeader(), widths[i]))
                    .append(COLUMN_SEPARATOR);
        }
        endLine(out);

        // Print header separator
        for (int i = 0; i < columns.size(); i++) {
            out.append("-".repeat(widths[i]))
                    .append(COLUMN_SEPARATOR);
        }
        endLine(out);

        // Print rows
        int rowCount = columns.get(0).getCells().size();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            printRow(out, rowIndex, widths);
        }

        // Print bottom separator
        for (int i = 0; i < columns.size(); i++) {
            out.append("-".repeat(widths[i] + COLUMN_SEPARATOR.length()));
        }
        out.setLength(out.length() - COLUMN_SEPARATOR.length()); // Remove last separator
        out.append("\n");

        // Print pagination info if available
        if (pagination != null) {
            pagination.print(out);
        }
    }

    /**
     * Prints a single row: every cell is wrapped to its column's allocated width and the
     * resulting visual lines are printed side by side.
     */
    private void printRow(StringBuilder out, int rowIndex, int[] widths) {
        var wrapped = new ArrayList<List<String>>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            wrapped.add(wrap(columns.get(i).getCells().get(rowIndex).getLines(), widths[i]));
        }
        var maxLineHeight = wrapped.stream().mapToInt(List::size).max().getAsInt();
        for (int lineIndex = 0; lineIndex < maxLineHeight; lineIndex++) {
            for (int i = 0; i < columns.size(); i++) {
                var lines = wrapped.get(i);
                var line = lineIndex < lines.size() ? lines.get(lineIndex) : "";
                out.append(pad(line, widths[i]))
                        .append(COLUMN_SEPARATOR);
            }
            endLine(out);
        }
    }

    /**
     * Allocates a width to every column. Columns start at their natural width (widest cell
     * or header). If the table exceeds the available width, columns narrower than their fair
     * share keep their natural width and the rest of the space is split evenly among the
     * wider ones, never going below {@link #MIN_COLUMN_WIDTH}.
     */
    private int[] computeColumnWidths() {
        var widths = new int[columns.size()];
        var total = 0;
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).getWidth();
            total += widths[i];
        }
        if (maxWidth <= 0) {
            return widths;
        }

        var available = max(
                maxWidth - COLUMN_SEPARATOR.length() * (columns.size() - 1),
                MIN_COLUMN_WIDTH * columns.size());
        if (total <= available) {
            return widths;
        }

        var fixed = new boolean[columns.size()];
        var remaining = fixColumnsWithinFairShare(widths, fixed, available);
        shrinkFlexibleColumns(widths, fixed, remaining);
        return widths;
    }

    /**
     * Fixes columns that fit within the current fair share, recomputing the share from the
     * remaining space until the allocation stabilizes. Returns the space left over for the
     * columns that must shrink.
     */
    private static int fixColumnsWithinFairShare(int[] widths, boolean[] fixed, int available) {
        var remaining = available;
        var flexible = widths.length;
        var changed = true;
        while (changed && flexible > 0) {
            changed = false;
            var share = remaining / flexible;
            for (int i = 0; i < widths.length; i++) {
                if (!fixed[i] && widths[i] <= share) {
                    fixed[i] = true;
                    remaining -= widths[i];
                    flexible--;
                    changed = true;
                }
            }
        }
        return remaining;
    }

    /**
     * Splits the remaining space evenly among the columns that must shrink, distributing
     * any leftover characters one by one from the left.
     */
    private static void shrinkFlexibleColumns(int[] widths, boolean[] fixed, int remaining) {
        var flexible = 0;
        for (boolean isFixed : fixed) {
            if (!isFixed) {
                flexible++;
            }
        }
        if (flexible == 0) {
            return;
        }
        var share = remaining / flexible;
        var leftover = remaining % flexible;
        for (int i = 0; i < widths.length; i++) {
            if (!fixed[i]) {
                widths[i] = max(share + (leftover > 0 ? 1 : 0), MIN_COLUMN_WIDTH);
                leftover--;
            }
        }
    }

    /**
     * Splits logical lines into chunks no wider than the given width, so cell content wraps
     * across multiple visual lines instead of being lost. Measured in UTF-16 code units,
     * see the class javadoc for the display-width limitation.
     */
    private static List<String> wrap(List<String> lines, int width) {
        var wrapped = new ArrayList<String>();
        for (String line : lines) {
            if (line.length() <= width) {
                wrapped.add(line);
            } else {
                for (int i = 0; i < line.length(); i += width) {
                    wrapped.add(line.substring(i, min(i + width, line.length())));
                }
            }
        }
        return wrapped;
    }

    /**
     * Pads the value to the given width. Only used for content already known to fit.
     */
    private static String pad(String value, int width) {
        return value + " ".repeat(width - value.length());
    }

    /**
     * Ends the current visual line, stripping trailing spaces first. Lines padded to the
     * full terminal width would otherwise overflow it by the trailing column separator,
     * making the terminal insert a blank line after every rendered line.
     */
    private static void endLine(StringBuilder out) {
        while (!out.isEmpty() && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }
        out.append("\n");
    }

    /**
     * Pads the header to the given width, truncating with an ellipsis if it does not fit.
     * Headers are fixed known strings, so unlike cell content they are not wrapped.
     */
    private String fit(String value, int width) {
        if (value.length() <= width) {
            return pad(value, width);
        }
        if (width <= ELLIPSIS.length()) {
            return value.substring(0, width);
        }
        return value.substring(0, width - ELLIPSIS.length()) + ELLIPSIS;
    }

    @Getter
    private static class Column {

        private final String header;
        private final List<Cell> cells = new ArrayList<>();
        private int width;

        public Column(String header) {
            this.header = header;
            width = max(header.length(), MIN_COLUMN_WIDTH);
        }

        public void addCell(String value) {
            var cell = new Cell(value);
            cells.add(cell);
            width = max(cell.getWidth(), width);
        }
    }

    @Getter
    private static class Cell {

        private final List<String> lines = new ArrayList<>();
        private final int width;

        public Cell(String value) {
            lines.addAll(List.of(value.split("\n")));
            width = lines.stream().mapToInt(String::length).max().orElse(0);
        }

        public int getHeight() {
            return lines.size();
        }
    }

    @AllArgsConstructor
    @Getter
    private static class Pagination {

        private final int page;
        private final int size;
        private final int total;

        public void print(StringBuilder out) {
            var pages = (int) Math.ceil((double) total / (double) size);
            out.append("Page %s/%s, total %s %s.".formatted(
                    page,
                    pages,
                    total,
                    total != 1 ? "rows" : "row"
            ));
            out.append('\n');
        }
    }
}
