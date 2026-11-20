package io.apicurio.registry.cli.interactive;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Pure state/logic for the interactive table — no terminal dependency,
 * so it can be unit tested directly.
 *
 * <p>Note: The TUI filter ('/') performs instant client-side filtering on the
 * currently loaded page rows (allRows). For global server-side search across
 * all pages, search parameters are passed via CLI command options.
 */
@RegisterForReflection
public class InteractiveTableState<T> {

    public enum Mode {
        NORMAL,
        FILTER_INPUT,
        CONFIRM_DELETE
    }

    private final Function<T, String> rowRenderer;
    private final Function<T, String> rowSearcher;
    private List<T> allRows;
    private List<T> filteredRows;
    private int selected = 0;
    private Mode mode = Mode.NORMAL;
    private StringBuilder filterText = new StringBuilder();

    public InteractiveTableState(List<T> rows, Function<T, String> rowRenderer) {
        this(rows, rowRenderer, null);
    }

    public InteractiveTableState(List<T> rows, Function<T, String> rowRenderer, Function<T, String> rowSearcher) {
        this.rowRenderer = rowRenderer;
        this.rowSearcher = rowSearcher != null ? rowSearcher : rowRenderer;
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

    /** Replaces the full row set (e.g. after fetching a new page). Re-applies active filter and resets selection. */
    public void setRows(List<T> rows) {
        this.allRows = rows;
        this.selected = 0;
        applyFilter();
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
        if (!filterText.isEmpty()) {
            filterText.deleteCharAt(filterText.length() - 1);
            applyFilter();
        }
    }

    public void startConfirmDelete() {
        if (!filteredRows.isEmpty()) {
            mode = Mode.CONFIRM_DELETE;
        }
    }

    public void cancelConfirmDelete() {
        mode = Mode.NORMAL;
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
        var query = filterText.toString().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            filteredRows = allRows;
        } else {
            var result = new ArrayList<T>();
            for (T row : allRows) {
                if (rowSearcher.apply(row).toLowerCase(Locale.ROOT).contains(query)) {
                    result.add(row);
                }
            }
            filteredRows = result;
        }
        selected = 0;
    }
}
