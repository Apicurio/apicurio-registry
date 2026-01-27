package io.apicurio.registry.cli.utils;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.stream;

/**
 * A fluent interface builder for creating formatted ASCII tables.
 */
public class TableBuilder {

    private static final int MIN_COLUMN_WIDTH = 3;
    private static final int MAX_COLUMN_WIDTH = 25; // TODO: Dynamically based on terminal width.
    private static final String COLUMN_SEPARATOR = "   ";

    // Invariant: Number of cells in every column must be the same.
    private final List<Column> columns = new ArrayList<>();

    private Pagination pagination;

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
     * Builds and prints the formatted table to the provided StringBuilder.
     */
    public void print(StringBuilder out) {
        if (columns.isEmpty()) {
            return;
        }

        // Print headers
        for (Column column : columns) {
            out.append(padRight(column.getHeader(), column.getWidth()))
                    .append(COLUMN_SEPARATOR);
        }
        out.append("\n");

        // Print header separator
        for (Column column : columns) {
            out.append("-".repeat(column.getWidth()))
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
                for (Column column : columns) {
                    var lines = column.getCells().get(rowIndex).getLines();
                    var line = "";
                    if (lineIndex < lines.size()) {
                        line = lines.get(lineIndex);
                    }
                    out.append(padRight(line, column.getWidth()))
                            .append(COLUMN_SEPARATOR);
                }
                out.append("\n");
            }
        }

        // Print bottom separator
        for (Column column : columns) {
            out.append("-".repeat(column.getWidth() + COLUMN_SEPARATOR.length()));
        }
        out.setLength(out.length() - COLUMN_SEPARATOR.length()); // Remove last separator
        out.append("\n");

        // Print pagination info if available
        if (pagination != null) {
            pagination.print(out);
        }
    }

    private String padRight(String str, int length) {
        if (str.length() >= length) {
            return str;
        }
        return str + " ".repeat(length - str.length());
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
            List.of(value.split("\n")).forEach(line -> {
                if (line.length() > MAX_COLUMN_WIDTH) {
                    // Split the line into chunks of MAX_COLUMN_WIDTH
                    for (int i = 0; i < line.length(); i += MAX_COLUMN_WIDTH) {
                        int end = min(i + MAX_COLUMN_WIDTH, line.length());
                        lines.add(line.substring(i, end));
                    }
                } else {
                    lines.add(line);
                }
            });
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
