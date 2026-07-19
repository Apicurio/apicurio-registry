package io.apicurio.registry.cli.interactive;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.util.List;
import java.util.function.Function;

public class InteractiveTable<T> {

    public enum Action {
        VIEW,
        DELETE
    }

    public record Selection<T>(T row, Action action) {
    }

    private final List<T> rows;
    private final Function<T, String> rowRenderer;
    private int selected = 0;

    public InteractiveTable(List<T> rows, Function<T, String> rowRenderer) {
        this.rows = rows;
        this.rowRenderer = rowRenderer;
    }

    /** Runs the loop. Returns the selected row/action, or null if the user exited. */
    public Selection<T> run() throws Exception {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {
            terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            render(terminal);
            while (true) {
                int c = reader.read();
                if (c == 'q') {
                    return null;
                } else if (c == 13) { // Enter
                    return rows.isEmpty() ? null : new Selection<>(rows.get(selected), Action.VIEW);
                } else if (c == 'd') {
                    return rows.isEmpty() ? null : new Selection<>(rows.get(selected), Action.DELETE);
                } else if (c == 27) { // possible escape sequence
                    int next = reader.read(50L);
                    if (next == '[') {
                        int arrow = reader.read(50L);
                        if (arrow == 'A') { // up
                            selected = Math.max(0, selected - 1);
                            render(terminal);
                        } else if (arrow == 'B') { // down
                            selected = Math.min(rows.size() - 1, selected + 1);
                            render(terminal);
                        }
                        // other escape sequences: ignore
                    } else {
                        // lone Esc key, no '[' followed
                        return null;
                    }
                }
                // any other key: ignore
            }
        }
    }

    private void render(Terminal terminal) {
        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
        for (int i = 0; i < rows.size(); i++) {
            String prefix = (i == selected) ? "> " : "  ";
            terminal.writer().println(prefix + rowRenderer.apply(rows.get(i)));
        }
        terminal.writer().println("\n[Enter: view, d: delete, q/Esc: exit]");
        terminal.flush();
    }
}
