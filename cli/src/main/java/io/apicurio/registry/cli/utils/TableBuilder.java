package io.apicurio.registry.cli.utils;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static java.lang.Math.max;
import static java.util.Arrays.stream;

/**
 * A fluent interface builder for creating formatted ASCII tables.
 * <p>
 * Tables adapt to the terminal width: columns wider than their fair share of the available
 * width are shrunk, and cell content that does not fit is truncated with an ellipsis.
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
        out.append("\n");

        // Print header separator
        for (int i = 0; i < columns.size(); i++) {
            out.append("-".repeat(widths[i]))
                    .append(COLUMN_SEPARATOR);
        }
        out.append("\n");

        // Print rows
        int rowCount = columns.get(0).getCells().size();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            // Print lines
            int finalRowIndex = rowIndex;
            var maxLineHeight = columns.stream().mapToInt(c -> c.getCells().get(finalRowIndex).getHeight()).max().getAsInt();
            for (int lineIndex = 0; lineIndex < maxLineHeight; lineIndex++) {
                for (int i = 0; i < columns.size(); i++) {
                    var lines = columns.get(i).getCells().get(rowIndex).getLines();
                    var line = "";
                    if (lineIndex < lines.size()) {
                        line = lines.get(lineIndex);
                    }
                    out.append(fit(line, widths[i]))
                            .append(COLUMN_SEPARATOR);
                }
                out.append("\n");
            }
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

        // Fix columns that fit within the current fair share, then recompute the share
        // from the remaining space until the allocation stabilizes.
        var fixed = new boolean[columns.size()];
        var remaining = available;
        var flexible = columns.size();
        var changed = true;
        while (changed && flexible > 0) {
            changed = false;
            var share = remaining / flexible;
            for (int i = 0; i < columns.size(); i++) {
                if (!fixed[i] && widths[i] <= share) {
                    fixed[i] = true;
                    remaining -= widths[i];
                    flexible--;
                    changed = true;
                }
            }
        }

        // Split the remaining space evenly among the columns that must shrink,
        // distributing any leftover characters one by one from the left.
        if (flexible > 0) {
            var share = remaining / flexible;
            var leftover = remaining % flexible;
            for (int i = 0; i < columns.size(); i++) {
                if (!fixed[i]) {
                    widths[i] = max(share + (leftover > 0 ? 1 : 0), MIN_COLUMN_WIDTH);
                    leftover--;
                }
            }
        }
        return widths;
    }

    /**
     * Pads the value to the given width, truncating with an ellipsis if it does not fit.
     */
    private String fit(String value, int width) {
        if (value.length() <= width) {
            return value + " ".repeat(width - value.length());
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
