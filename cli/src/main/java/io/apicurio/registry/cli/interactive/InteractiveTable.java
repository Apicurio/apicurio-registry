package io.apicurio.registry.cli.interactive;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

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

            KeyMap<String> keyMap = new KeyMap<>();
            keyMap.bind("UP", KeyMap.key(terminal, Capability.key_up));
            keyMap.bind("DOWN", KeyMap.key(terminal, Capability.key_down));
            keyMap.bind("ENTER", "\r");
            keyMap.bind("QUIT", "q");
            keyMap.bind("DELETE", "d");
            keyMap.bind("ESC", KeyMap.esc());

            BindingReader bindingReader = new BindingReader(terminal.reader());

            render(terminal);
            while (true) {
                String op = bindingReader.readBinding(keyMap);
                if (op == null || op.equals("QUIT") || op.equals("ESC")) {
                    return null;
                } else if (op.equals("ENTER")) {
                    return rows.isEmpty() ? null : new Selection<>(rows.get(selected), Action.VIEW);
                } else if (op.equals("DELETE")) {
                    return rows.isEmpty() ? null : new Selection<>(rows.get(selected), Action.DELETE);
                } else if (op.equals("UP")) {
                    selected = Math.max(0, selected - 1);
                    render(terminal);
                } else if (op.equals("DOWN")) {
                    selected = Math.min(rows.size() - 1, selected + 1);
                    render(terminal);
                }
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