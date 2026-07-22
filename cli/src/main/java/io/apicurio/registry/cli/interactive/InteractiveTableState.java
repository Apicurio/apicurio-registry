package io.apicurio.registry.cli.interactive;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pure state/logic for the interactive table — no terminal dependency,
 * so it can be unit tested directly.
 */
public class InteractiveTableState<T> {

    public enum Mode {
        NORMAL,
        FILTER_INPUT
    }

    private final Function<T, String> rowRenderer;
    private List<T> allRows;
    private List<T> filteredRows;
    private int selected = 0;
    private Mode mode = Mode.NORMAL;
    private StringBuilder filterText = new StringBuilder();

    public InteractiveTableState(List<T> rows, Function<T, String> rowRenderer) {
        this.rowRenderer = rowRenderer;
        this.allRows = rows;
        this.filteredRows = rows;
    }

    public List<T> getVisibleRows() {
        return filteredRows;
    }

    public int getSelectedIndex() {
        return selected;
    }

    public T getSelectedRow() {
        return filteredRows.isEmpty() ? null : filteredRows.get(selected);
    }

    public Mode getMode() {
        return mode;
    }

    public String getFilterText() {
        return filterText.toString();
    }

    /** Replaces the full row set (e.g. after fetching a new page). Resets filter and selection. */
    public void setRows(List<T> rows) {
        this.allRows = rows;
        this.filteredRows = rows;
        this.selected = 0;
        clearFilter();
    }

    public void moveUp() {
        if (!filteredRows.isEmpty()) {
            selected = Math.max(0, selected - 1);
        }
    }

    public void moveDown() {
        if (!filteredRows.isEmpty()) {
            selected = Math.min(filteredRows.size() - 1, selected + 1);
        }
    }

    public void startFilterInput() {
        mode = Mode.FILTER_INPUT;
    }

    public void typeFilterChar(char c) {
        filterText.append(c);
        applyFilter();
    }

    public void backspaceFilterChar() {
        if (filterText.length() > 0) {
            filterText.deleteCharAt(filterText.length() - 1);
            applyFilter();
        }
    }

    public void commitFilter() {
        mode = Mode.NORMAL;
    }

    public void clearFilter() {
        filterText = new StringBuilder();
        filteredRows = allRows;
        selected = 0;
        mode = Mode.NORMAL;
    }

    private void applyFilter() {
        var query = filterText.toString().toLowerCase();
        if (query.isEmpty()) {
            filteredRows = allRows;
        } else {
            var result = new ArrayList<T>();
            for (T row : allRows) {
                if (rowRenderer.apply(row).toLowerCase().contains(query)) {
                    result.add(row);
                }
            }
            filteredRows = result;
        }
        selected = 0;
    }
}