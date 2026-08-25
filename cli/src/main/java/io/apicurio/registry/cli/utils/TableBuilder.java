package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.common.CliException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;
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
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]");

    // Invariant: Number of cells in every column must be the same.
    private final List<Column> columns = new ArrayList<>();

    // The columns to print, in print order. Null means every column is printed.
    // Note: the builder has no way to modify or delete columns or rows once they have been added.
    // If that is ever needed, whether this builder should be mutable or immutable is an open design
    // decision that has to be made first.
    private List<Column> selectedColumns;

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
            columns.get(i).addCell(i < values.length ? values[i] : "");
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
     * Sets the columns to print, restricting the table to the requested columns and printing them in
     * the order requested. Requested names are matched against the column headers case-insensitively,
     * ignoring any non-alphanumeric characters, so "groupId" matches a "Group ID" header. Blank
     * entries are ignored, so "--columns name,,state" behaves like "--columns name,state". A null,
     * empty, or entirely blank selection leaves the table unchanged.
     * <p>
     * Calling this overwrites any previous selection. The selection is always resolved against the
     * full set of columns rather than against an earlier selection, so calling it more than once is
     * safe and the last call wins. Only the printed output is affected - the columns that
     * {@link #addRow(String...)} populates are unchanged.
     *
     * @throws CliException if any requested name does not match a known column
     */
    public TableBuilder setSelectedColumns(List<String> requestedColumns) {
        if (requestedColumns == null || requestedColumns.isEmpty()) {
            return this;
        }
        var columnsByName = new LinkedHashMap<String, Column>();
        for (var column : columns) {
            columnsByName.put(normalizeColumnName(column.getHeader()), column);
        }
        var selected = new ArrayList<Column>();
        var invalid = new ArrayList<String>();
        for (var requested : requestedColumns) {
            var normalized = normalizeColumnName(requested);
            if (normalized.isEmpty()) {
                continue;
            }
            var column = columnsByName.get(normalized);
            if (column == null) {
                invalid.add(requested);
            } else if (!selected.contains(column)) {
                selected.add(column);
            }
        }
        if (!invalid.isEmpty()) {
            var validColumns = columns.stream().map(Column::getHeader).collect(Collectors.joining(", "));
            throw new CliException("Invalid column(s) '" + String.join(", ", invalid)
                    + "'. Valid values: " + validColumns + ".", VALIDATION_ERROR_RETURN_CODE);
        }
        if (selected.isEmpty()) {
            return this;
        }
        selectedColumns = selected;
        return this;
    }

    private List<Column> visibleColumns() {
        return selectedColumns != null ? selectedColumns : columns;
    }

    private static String normalizeColumnName(String name) {
        return NON_ALPHANUMERIC.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /**
     * Builds and prints the formatted table to the provided StringBuilder.
     */
    public void print(StringBuilder out) {
        var visible = visibleColumns();
        if (visible.isEmpty()) {
            return;
        }

        var widths = computeColumnWidths(visible);

        // Print headers
        for (int i = 0; i < visible.size(); i++) {
            out.append(fit(visible.get(i).getHeader(), widths[i]))
                    .append(COLUMN_SEPARATOR);
        }
        endLine(out);

        // Print header separator
        for (int i = 0; i < visible.size(); i++) {
            out.append("-".repeat(widths[i]))
                    .append(COLUMN_SEPARATOR);
        }
        endLine(out);

        // Print rows
        int rowCount = visible.get(0).getCells().size();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            printRow(out, visible, rowIndex, widths);
        }

        // Print bottom separator
        for (int i = 0; i < visible.size(); i++) {
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
    private static void printRow(StringBuilder out, List<Column> visible, int rowIndex, int[] widths) {
        var wrapped = new ArrayList<List<String>>(visible.size());
        for (int i = 0; i < visible.size(); i++) {
            wrapped.add(wrap(visible.get(i).getCells().get(rowIndex).getLines(), widths[i]));
        }
        var maxLineHeight = wrapped.stream().mapToInt(List::size).max().getAsInt();
        for (int lineIndex = 0; lineIndex < maxLineHeight; lineIndex++) {
            for (int i = 0; i < wrapped.size(); i++) {
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
    private int[] computeColumnWidths(List<Column> visible) {
        var widths = new int[visible.size()];
        var total = 0;
        for (int i = 0; i < visible.size(); i++) {
            widths[i] = visible.get(i).getWidth();
            total += widths[i];
        }
        if (maxWidth <= 0) {
            return widths;
        }

        var available = max(
                maxWidth - COLUMN_SEPARATOR.length() * (visible.size() - 1),
                MIN_COLUMN_WIDTH * visible.size());
        if (total <= available) {
            return widths;
        }

        var fixed = new boolean[visible.size()];
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
     * <p>
     * At widths up to the ellipsis length the ellipsis is omitted: it would consume the
     * whole column and convey nothing, while a plain cut keeps identifying characters.
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

        // A null value renders as an empty cell, so callers do not have to guard.
        public Cell(String value) {
            lines.addAll(List.of((value != null ? value : "").split("\n")));
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
