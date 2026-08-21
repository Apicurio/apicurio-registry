package io.apicurio.registry.cli.interactive;

import io.apicurio.registry.cli.common.CliException;
import org.jboss.logging.Logger;
import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;

@RegisterForReflection
public class InteractiveTable<T> {

    private static final Logger log = Logger.getLogger(InteractiveTable.class);

    public enum Action {
        VIEW,
        DELETE,
        QUIT
    }

    public record Selection<T>(T row, Action action) {
    }

    final InteractiveTableState<T> state;
    private final Function<T, String> rowRenderer;
    private final IntFunction<PageResult<T>> pageFetcher;
    private final Consumer<T> deleteHandler;
    private int currentPage = 1;
    private boolean hasNextPage = true;
    private int windowStart = 0;
    private String errorMessage = null;
    private String statusMessage = null;

    /** Result of fetching a page: the rows, and whether more pages exist. */
    public record PageResult<T>(List<T> rows, boolean hasNextPage) {
    }

    public InteractiveTable(List<T> rows, Function<T, String> rowRenderer, Function<T, String> rowSearcher, IntFunction<PageResult<T>> pageFetcher, boolean hasNextPage, Consumer<T> deleteHandler) {
        this.state = new InteractiveTableState<>(rows, rowRenderer, rowSearcher);
        this.rowRenderer = rowRenderer;
        this.pageFetcher = pageFetcher;
        this.hasNextPage = hasNextPage;
        this.deleteHandler = deleteHandler;
    }

    /**
     * Runs the interactive TUI loop.
     *
     * @return the user's selection, or {@code null} if the user exited
     *         (via 'q'/Esc) or if stdin reached EOF.
     * @throws CliException if an interactive terminal (TTY) cannot be opened.
     */
    public Selection<T> run() {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {
            validateTerminal(terminal);
            terminal.enterRawMode();
            terminal.puts(Capability.enter_ca_mode);
            try {
                return runEventLoop(terminal);
            } finally {
                terminal.puts(Capability.exit_ca_mode);
                terminal.flush();
            }
        } catch (IOException e) {
            log.warn("Interactive mode failed to initialize.", e);
            throw new CliException("Interactive mode failed to initialize: " + e.getMessage());
        }
    }

    private void validateTerminal(Terminal terminal) {
        if (terminal.getType() == null || terminal.getType().equals(Terminal.TYPE_DUMB)
                || terminal.getType().equals(Terminal.TYPE_DUMB_COLOR)) {
            throw new CliException("Interactive mode requires an interactive terminal (TTY).");
        }
    }

    private Selection<T> runEventLoop(Terminal terminal) {
        KeyMap<String> normalKeyMap = buildNormalKeyMap(terminal);
        KeyMap<String> filterKeyMap = buildFilterKeyMap();
        KeyMap<String> confirmKeyMap = buildConfirmKeyMap();
        BindingReader bindingReader = new BindingReader(terminal.reader());

        render(terminal);
        while (true) {
            var mode = state.getMode();
            KeyMap<String> activeKeyMap = switch (mode) {
                case FILTER_INPUT -> filterKeyMap;
                case CONFIRM_DELETE -> confirmKeyMap;
                case NORMAL -> normalKeyMap;
            };
            String op = bindingReader.readBinding(activeKeyMap);
            if (op == null) {
                // EOF on stdin — exit cleanly
                return null;
            }
            if ("NOP".equals(op)) {
                continue;
            }
            var result = handleBinding(op, mode);
            if (result != null) {
                return result.action() == Action.QUIT ? null : result;
            }
            render(terminal);
        }
    }


    private static final String KEY_ENTER = "ENTER";
    private static final String KEY_BACKSPACE = "BACKSPACE";
    private static final String KEY_CONFIRM_YES = "CONFIRM_YES";
    private static final String KEY_CONFIRM_NO = "CONFIRM_NO";

    private KeyMap<String> buildNormalKeyMap(Terminal terminal) {
        KeyMap<String> keyMap = new KeyMap<>();
        keyMap.bind("UP", KeyMap.key(terminal, Capability.key_up));
        keyMap.bind("DOWN", KeyMap.key(terminal, Capability.key_down));
        keyMap.bind(KEY_ENTER, "\r");
        keyMap.bind(KEY_ENTER, "\n");
        keyMap.bind("QUIT", "q");
        keyMap.bind("QUIT", "\u0003");
        keyMap.bind("DELETE", "d");
        keyMap.bind("FILTER", "/");
        keyMap.bind("ESC", KeyMap.esc());
        if (pageFetcher != null) {
            keyMap.bind("NEXT_PAGE", KeyMap.key(terminal, Capability.key_npage));
            keyMap.bind("PREV_PAGE", KeyMap.key(terminal, Capability.key_ppage));
        }
        keyMap.setNomatch("NOP");
        return keyMap;
    }

    private KeyMap<String> buildFilterKeyMap() {
        KeyMap<String> keyMap = new KeyMap<>();
        keyMap.bind(KEY_ENTER, "\r");
        keyMap.bind(KEY_ENTER, "\n");
        keyMap.bind("ESC", KeyMap.esc());
        keyMap.bind(KEY_BACKSPACE, KeyMap.del());
        keyMap.bind(KEY_BACKSPACE, "\b");
        for (char c = 32; c < 127; c++) {
            keyMap.bind(String.valueOf(c), String.valueOf(c));
        }
        keyMap.setNomatch("NOP");
        return keyMap;
    }

    private KeyMap<String> buildConfirmKeyMap() {
        KeyMap<String> keyMap = new KeyMap<>();
        keyMap.bind(KEY_CONFIRM_YES, "y");
        keyMap.bind(KEY_CONFIRM_YES, "Y");
        keyMap.bind(KEY_CONFIRM_NO, "n");
        keyMap.bind(KEY_CONFIRM_NO, "N");
        keyMap.bind(KEY_CONFIRM_NO, "\r");
        keyMap.bind(KEY_CONFIRM_NO, "\n");
        keyMap.bind("ESC", KeyMap.esc());
        keyMap.setNomatch("NOP");
        return keyMap;
    }

    /** Returns a Selection if the loop should end with a result, or null to keep looping. */
    Selection<T> handleBinding(String op, InteractiveTableState.Mode mode) {
        return switch (mode) {
            case FILTER_INPUT -> {
                handleFilterBinding(op);
                yield null;
            }
            case CONFIRM_DELETE -> handleConfirmDeleteBinding(op);
            case NORMAL -> handleNormalBinding(op);
        };
    }

    Selection<T> handleConfirmDeleteBinding(String op) {
        state.cancelConfirmDelete();
        if (!KEY_CONFIRM_YES.equals(op)) {
            return null;
        }

        var row = state.getSelectedRow();
        if (row == null) {
            return null;
        }

        if (deleteHandler != null) {
            executeDeleteAndRefresh(row);
            return null;
        }

        return new Selection<>(row, Action.DELETE);
    }

    private void executeDeleteAndRefresh(T row) {
        try {
            deleteHandler.accept(row);
            statusMessage = "Deleted " + rowRenderer.apply(row);
            errorMessage = null;
        } catch (Exception e) {
            log.warn("Failed to delete item", e);
            errorMessage = "Failed to delete: " + extractErrorMessage(e);
            statusMessage = null;
            return;
        }

        try {
            refreshCurrentPage();
        } catch (Exception e) {
            log.warn("Error refreshing page after deletion", e);
            errorMessage = "Deleted item, but failed to refresh page: " + e.getMessage();
        }
    }

    private String extractErrorMessage(Exception e) {
        var msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (e.getCause() != null && e.getCause().getMessage() != null && !msg.contains(e.getCause().getMessage())) {
            return msg + " (" + e.getCause().getMessage() + ")";
        }
        return msg;
    }

    void handleFilterBinding(String op) {
        statusMessage = null;
        if (op.equals("ESC")) {
            state.clearFilter();
        } else if (op.equals(KEY_ENTER)) {
            state.commitFilter();
        } else if (op.equals(KEY_BACKSPACE)) {
            state.backspaceFilterChar();
        } else if (op.length() == 1) {
            state.typeFilterChar(op.charAt(0));
        }
    }

    Selection<T> handleNormalBinding(String op) {
        statusMessage = null;
        switch (op) {
            case "QUIT", "ESC" -> {
                return terminalExit();
            }
            case KEY_ENTER -> {
                var row = state.getSelectedRow();
                return row == null ? null : new Selection<>(row, Action.VIEW);
            }
            case "DELETE" -> {
                state.startConfirmDelete();
                return null;
            }
            case "UP" -> state.moveUp();
            case "DOWN" -> state.moveDown();
            case "FILTER" -> state.startFilterInput();
            case "NEXT_PAGE" -> {
                if (hasNextPage) {
                    goToPage(currentPage + 1);
                }
            }
            case "PREV_PAGE" -> {
                if (currentPage > 1) {
                    goToPage(currentPage - 1);
                }
            }
            default -> { /* ignore unrecognized bindings */ }
        }
        return null;
    }

    private Selection<T> terminalExit() {
        return new Selection<>(null, Action.QUIT);
    }

    void goToPage(int page) {
        if (pageFetcher == null || page < 1) {
            return;
        }
        try {
            var result = pageFetcher.apply(page);
            if (result == null) {
                return;
            }
            errorMessage = null;
            currentPage = page;
            hasNextPage = result.hasNextPage();
            state.setRows(result.rows());
        } catch (Exception e) {
            log.warn("Error fetching page " + page, e);
            errorMessage = "Failed to load page " + page + ": " + e.getMessage();
        }
    }

    void refreshCurrentPage() {
        if (pageFetcher == null) {
            return;
        }
        try {
            var result = pageFetcher.apply(currentPage);
            if (result == null || result.rows().isEmpty()) {
                if (currentPage > 1) {
                    goToPage(currentPage - 1);
                } else {
                    currentPage = 1;
                    hasNextPage = false;
                    state.setRows(List.of());
                }
                return;
            }
            errorMessage = null;
            hasNextPage = result.hasNextPage();
            state.setRows(result.rows());
        } catch (Exception e) {
            log.warn("Error refreshing page " + currentPage, e);
            errorMessage = "Failed to refresh page " + currentPage + ": " + e.getMessage();
        }
    }

    private void render(Terminal terminal) {
        terminal.puts(Capability.clear_screen);
        var rows = state.getVisibleRows();
        int selected = state.getSelectedIndex();

        int availableRows = calculateAvailableRows(terminal);
        updateWindowStart(selected, availableRows, rows.size());
        
        int windowEnd = Math.min(rows.size(), windowStart + availableRows);

        for (int i = windowStart; i < windowEnd; i++) {
            String prefix = (i == selected) ? "> " : "  ";
            terminal.writer().println(prefix + rowRenderer.apply(rows.get(i)));
        }
        if (rows.isEmpty()) {
            if (state.getFilterText() != null && !state.getFilterText().isEmpty()) {
                terminal.writer().println("(no matching rows)");
            } else {
                terminal.writer().println("(page empty)");
            }
        }
        renderFooter(terminal);
    }

    private int calculateAvailableRows(Terminal terminal) {
        int terminalRows = terminal.getSize().getRows();
        if (terminalRows <= 0) {
            terminalRows = 24;
        }
        // Reserve 3 lines for the footer (blank line + help/filter + prompt)
        return Math.max(5, terminalRows - 3);
    }

    private void updateWindowStart(int selected, int availableRows, int totalRows) {
        if (selected < windowStart) {
            windowStart = selected;
        } else if (selected >= windowStart + availableRows) {
            windowStart = selected - availableRows + 1;
        }

        if (windowStart > totalRows - availableRows) {
            windowStart = Math.max(0, totalRows - availableRows);
        }
    }

    private void renderFooter(Terminal terminal) {
        terminal.writer().println();
        if (errorMessage != null) {
            terminal.writer().println("Error: " + errorMessage);
        } else if (state.getMode() == InteractiveTableState.Mode.FILTER_INPUT) {
            terminal.writer().println("Filter (loaded rows): " + state.getFilterText() + "_  [Enter: apply, Esc: clear]");
        } else if (state.getMode() == InteractiveTableState.Mode.CONFIRM_DELETE) {
            var selectedRow = state.getSelectedRow();
            String label = selectedRow != null ? rowRenderer.apply(selectedRow) : "selected item";
            terminal.writer().println("Delete " + label + "? [y/N] ");
        } else {
            var pageInfo = pageFetcher != null ? "  [PgUp/PgDn: page " + currentPage + "]" : "";
            var statusInfo = statusMessage != null ? "  (" + statusMessage + ")" : "";
            terminal.writer().println("[Enter: view, d: delete, /: filter loaded rows, q/Esc: exit]" + pageInfo + statusInfo);
        }
        terminal.flush();
    }
}
