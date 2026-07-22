package io.apicurio.registry.cli.interactive;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

public class InteractiveTable<T> {

    public enum Action {
        VIEW,
        DELETE
    }

    public record Selection<T>(T row, Action action) {
    }

    private final InteractiveTableState<T> state;
    private final Function<T, String> rowRenderer;
    private final IntFunction<PageResult<T>> pageFetcher;
    private int currentPage = 1;
    private boolean hasNextPage = false;

    /** Result of fetching a page: the rows, and whether more pages exist. */
    public record PageResult<T>(List<T> rows, boolean hasNextPage) {
    }

    public InteractiveTable(List<T> rows, Function<T, String> rowRenderer) {
        this(rows, rowRenderer, null);
    }

    /**
     * @param pageFetcher optional callback invoked with a 1-based page number,
     *                     used for server-backed pagination (PgUp/PgDn). Pass
     *                     null to disable pagination and only show the given rows.
     */
    public InteractiveTable(List<T> rows, Function<T, String> rowRenderer, IntFunction<PageResult<T>> pageFetcher) {
        this.state = new InteractiveTableState<>(rows, rowRenderer);
        this.rowRenderer = rowRenderer;
        this.pageFetcher = pageFetcher;
    }

    /**
     * Runs the interactive TUI loop.
     *
     * @return the user's selection, or {@code null} if the user exited
     *         (via 'q'/Esc) or if a terminal I/O error occurred. Callers
     *         cannot distinguish these two cases from the return value alone.
     */
    public Selection<T> run() {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {
            terminal.enterRawMode();
            terminal.puts(Capability.enter_ca_mode);
            try {
                KeyMap<String> normalKeyMap = buildNormalKeyMap(terminal);
                KeyMap<String> filterKeyMap = buildFilterKeyMap();
                BindingReader bindingReader = new BindingReader(terminal.reader());

                render(terminal);
                while (true) {
                    boolean filtering = state.getMode() == InteractiveTableState.Mode.FILTER_INPUT;
                    String op = bindingReader.readBinding(filtering ? filterKeyMap : normalKeyMap);
                    if (op == null) {
                        return null;
                    }
                    var result = handleBinding(op, filtering);
                    if (result != null) {
                        return result;
                    }
                    render(terminal);
                }
            } finally {
                terminal.puts(Capability.exit_ca_mode);
                terminal.flush();
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static final String KEY_ENTER = "ENTER";
    private static final String KEY_BACKSPACE = "BACKSPACE";

    private KeyMap<String> buildNormalKeyMap(Terminal terminal) {
        KeyMap<String> keyMap = new KeyMap<>();
        keyMap.bind("UP", KeyMap.key(terminal, Capability.key_up));
        keyMap.bind("DOWN", KeyMap.key(terminal, Capability.key_down));
        keyMap.bind(KEY_ENTER, "\r");
        keyMap.bind("QUIT", "q");
        keyMap.bind("DELETE", "d");
        keyMap.bind("FILTER", "/");
        keyMap.bind("ESC", KeyMap.esc());
        if (pageFetcher != null) {
            keyMap.bind("NEXT_PAGE", KeyMap.key(terminal, Capability.key_npage));
            keyMap.bind("PREV_PAGE", KeyMap.key(terminal, Capability.key_ppage));
        }
        return keyMap;
    }

    private KeyMap<String> buildFilterKeyMap() {
        KeyMap<String> keyMap = new KeyMap<>();
        keyMap.bind(KEY_ENTER, "\r");
        keyMap.bind("ESC", KeyMap.esc());
        keyMap.bind(KEY_BACKSPACE, KeyMap.del());
        keyMap.bind(KEY_BACKSPACE, "\b");
        for (char c = 32; c < 127; c++) {
            keyMap.bind(String.valueOf(c), String.valueOf(c));
        }
        return keyMap;
    }

    /** Returns a Selection if the loop should end with a result, or null to keep looping. */
    private Selection<T> handleBinding(String op, boolean filtering) {
        if (filtering) {
            handleFilterBinding(op);
            return null;
        }
        return handleNormalBinding(op);
    }

   private void handleFilterBinding(String op) {
        if (op.equals("ESC")) {
            state.clearFilter();
        } else if (op.equals("ENTER")) {
            state.commitFilter();
        } else if (op.equals("BACKSPACE")) {
            state.backspaceFilterChar();
        } else if (op.length() == 1) {
            state.typeFilterChar(op.charAt(0));
        }
}
    private Selection<T> handleNormalBinding(String op) {
        switch (op) {
            case "QUIT", "ESC" -> {
                return terminalExit();
            }
            case "ENTER" -> {
                var row = state.getSelectedRow();
                return row == null ? null : new Selection<>(row, Action.VIEW);
            }
            case "DELETE" -> {
                var row = state.getSelectedRow();
                return row == null ? null : new Selection<>(row, Action.DELETE);
            }
            case "UP" -> state.moveUp();
            case "DOWN" -> state.moveDown();
            case "FILTER" -> state.startFilterInput();
            case "NEXT_PAGE" -> goToPage(currentPage + 1);
            case "PREV_PAGE" -> goToPage(currentPage - 1);
            default -> { /* ignore unrecognized bindings */ }
        }
        return null;
    }

    private Selection<T> terminalExit() {
        return null;
    }

    private void goToPage(int page) {
    if (pageFetcher == null || page < 1) {
        return;
    }
    var result = pageFetcher.apply(page);
    if (result == null || result.rows().isEmpty()) {
        return;
    }
    currentPage = page;
    hasNextPage = result.hasNextPage();
    state.setRows(result.rows());
}

    private void render(Terminal terminal) {
        terminal.puts(Capability.clear_screen);
        var rows = state.getVisibleRows();
        for (int i = 0; i < rows.size(); i++) {
            String prefix = (i == state.getSelectedIndex()) ? "> " : "  ";
            terminal.writer().println(prefix + rowRenderer.apply(rows.get(i)));
        }
        if (rows.isEmpty()) {
            terminal.writer().println("(no matching rows)");
        }
        terminal.writer().println();
        if (state.getMode() == InteractiveTableState.Mode.FILTER_INPUT) {
            terminal.writer().println("Filter: " + state.getFilterText() + "_  [Enter: apply, Esc: clear]");
        } else {
            var pageInfo = pageFetcher != null ? "  [PgUp/PgDn: page " + currentPage + "]" : "";
            terminal.writer().println("[Enter: view, d: delete, /: search, q/Esc: exit]" + pageInfo);
        }
        terminal.flush();
    }
}
