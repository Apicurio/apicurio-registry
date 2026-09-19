package io.apicurio.registry.cli.interactive;

import io.apicurio.registry.cli.common.CliException;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.jline.terminal.impl.ExternalTerminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveTableTest {

    @Test
    void testHandleNormalBinding_Quit() {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);

        var selection = table.handleBinding("QUIT", InteractiveTableState.Mode.NORMAL);
        assertEquals(InteractiveTable.Action.QUIT, selection.action());
    }

    @Test
    void testHandleNormalBinding_Enter() {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);

        var selection = table.handleBinding("ENTER", InteractiveTableState.Mode.NORMAL);
        assertEquals("A", selection.row());
        assertEquals(InteractiveTable.Action.VIEW, selection.action());
    }

    @Test
    void testHandleNormalBinding_Delete() {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);

        var selection = table.handleBinding("DELETE", InteractiveTableState.Mode.NORMAL);
        assertNull(selection);
        assertEquals(InteractiveTableState.Mode.CONFIRM_DELETE, table.state.getMode());
    }

    @Test
    void testHandleConfirmDeleteBinding_Yes() {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);
        table.state.startConfirmDelete();

        var selection = table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);
        assertEquals("A", selection.row());
        assertEquals(InteractiveTable.Action.DELETE, selection.action());
        assertEquals(InteractiveTableState.Mode.NORMAL, table.state.getMode());
    }

    @Test
    void testHandleConfirmDeleteBinding_No() {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);
        table.state.startConfirmDelete();

        var selection = table.handleBinding("CONFIRM_NO", InteractiveTableState.Mode.CONFIRM_DELETE);
        assertNull(selection);
        assertEquals(InteractiveTableState.Mode.NORMAL, table.state.getMode());
    }

    @Test
    void testHandleFilterBinding() {
        var table = new InteractiveTable<>(List.of("A", "B"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);
        table.state.startFilterInput();
        table.state.typeFilterChar('A');

        var selection = table.handleBinding("ENTER", InteractiveTableState.Mode.FILTER_INPUT);
        assertNull(selection);
        assertEquals(InteractiveTableState.Mode.NORMAL, table.state.getMode());
        assertEquals("A", table.state.getFilterText());
    }

    @Test
    void testGoToPage() {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> {
            if (p == 2) {
                return new InteractiveTable.PageResult<>(List.of("B"), true);
            }
            return new InteractiveTable.PageResult<>(List.of(), false);
        }, true, null);

        table.goToPage(2);
        assertEquals("B", table.state.getVisibleRows().get(0));
    }

    @Test
    void testGoToPage_EmptyResult_UpdatesCurrentPageAndState() {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> {
            if (p == 2) {
                return new InteractiveTable.PageResult<>(List.of(), false);
            }
            return new InteractiveTable.PageResult<>(List.of("A"), true);
        }, true, null);

        table.goToPage(2);
        assertTrue(table.state.getVisibleRows().isEmpty());
    }

    @Test
    void testHandleConfirmDeleteBinding_WithDeleteHandler_ExecutesAndRefreshes() {
        var deleted = new AtomicReference<String>();
        var pageRows = new ArrayList<>(List.of("A", "B"));
        var table = new InteractiveTable<String>(
                List.copyOf(pageRows),
                s -> s,
                s -> s,
                p -> new InteractiveTable.PageResult<>(pageRows, false),
                false,
                item -> {
                    deleted.set(item);
                    pageRows.remove(item);
                }
        );
        table.state.startConfirmDelete();

        var selection = table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);
        assertNull(selection); // stays in loop
        assertEquals("A", deleted.get());
        assertEquals(List.of("B"), table.state.getVisibleRows());
        assertEquals(InteractiveTableState.Mode.NORMAL, table.state.getMode());
    }

    @Test
    void testHandleConfirmDeleteBinding_WithDeleteHandler_ErrorHandling() {
        var table = new InteractiveTable<String>(
                List.of("A"),
                s -> s,
                s -> s,
                p -> new InteractiveTable.PageResult<>(List.of("A"), false),
                false,
                item -> {
                    throw new RuntimeException("Server error");
                }
        );
        table.state.startConfirmDelete();

        var selection = table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);
        assertNull(selection); // stays in loop
        assertEquals(InteractiveTableState.Mode.NORMAL, table.state.getMode());
        assertEquals("Failed to delete: Server error", table.getErrorMessage());
        // The failure has to outlive the footer, which is gone once the TUI exits.
        assertEquals("Failed to delete: Server error", table.getDeleteFailureMessage());
    }

    @Test
    void testFailIfDeleteFailed_ThrowsWithApplicationErrorCodeAfterAFailedDelete() {
        var table = new InteractiveTable<String>(
                List.of("A"),
                s -> s,
                s -> s,
                p -> new InteractiveTable.PageResult<>(List.of("A"), false),
                false,
                item -> {
                    throw new RuntimeException("Server error");
                }
        );
        table.state.startConfirmDelete();
        table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);

        // The TUI has exited by this point, so the exit code is the only signal left.
        var thrown = assertThrows(CliException.class, table::failIfDeleteFailed);
        assertEquals("Failed to delete: Server error", thrown.getMessage());
        assertEquals(CliException.APPLICATION_ERROR_RETURN_CODE, thrown.getCode());
    }

    @Test
    void testFailIfDeleteFailed_SilentWhenEveryDeleteSucceeds() {
        var pageRows = new ArrayList<>(List.of("A", "B"));
        var table = new InteractiveTable<String>(
                List.copyOf(pageRows),
                s -> s,
                s -> s,
                p -> new InteractiveTable.PageResult<>(pageRows, false),
                false,
                pageRows::remove
        );
        table.state.startConfirmDelete();

        var selection = table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);
        assertNull(selection); // the handler ran in-loop rather than returning a DELETE selection
        assertEquals(List.of("B"), table.state.getVisibleRows());
        assertNull(table.getDeleteFailureMessage());
        assertDoesNotThrow(table::failIfDeleteFailed);
    }

    @Test
    void testHandleNormalBinding_AfterFailedDelete_ErrorDoesNotHideConfirmPrompt() throws IOException {
        var table = new InteractiveTable<String>(
                List.of("A", "B"),
                s -> s,
                s -> s,
                p -> new InteractiveTable.PageResult<>(List.of("A", "B"), false),
                false,
                item -> {
                    throw new RuntimeException("Server error");
                }
        );
        table.state.startConfirmDelete();
        table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);
        assertEquals("Failed to delete: Server error", table.getErrorMessage());

        // Pressing 'd' again must show the confirmation prompt, not the previous error: the footer
        // renders errorMessage at a higher priority than the prompt.
        table.handleBinding("DELETE", InteractiveTableState.Mode.NORMAL);
        assertNull(table.getErrorMessage());
        assertEquals(InteractiveTableState.Mode.CONFIRM_DELETE, table.state.getMode());
        assertEquals("Delete A? [y/N]", renderFooter(table));
        // Clearing the footer must not lose the failure that drives the exit code.
        assertEquals("Failed to delete: Server error", table.getDeleteFailureMessage());
    }

    /**
     * Renders just the footer to an in-memory terminal and returns it without the leading blank
     * line. The carriage returns must be stripped: println emits the platform separator and the
     * terminal applies ONLCR on top, so on Windows the stream contains "\r\r\n".
     */
    private static String renderFooter(InteractiveTable<String> table) throws IOException {
        var out = new ByteArrayOutputStream();
        try (Terminal terminal = new ExternalTerminal("test", "xterm",
                new ByteArrayInputStream(new byte[0]), out, StandardCharsets.UTF_8)) {
            table.renderFooter(terminal);
        }
        return out.toString(StandardCharsets.UTF_8).replace("\r", "").strip();
    }

    @Test
    void testHandleConfirmDeleteBinding_DeleteSuccess_RefreshFailure_ReportsBoth() {
        var deleted = new AtomicBoolean(false);
        var table = new InteractiveTable<String>(
                List.of("A"),
                s -> s,
                s -> s,
                p -> {
                    if (deleted.get()) {
                        throw new RuntimeException("Network timeout on refresh");
                    }
                    return new InteractiveTable.PageResult<>(List.of("A"), false);
                },
                false,
                item -> deleted.set(true)
        );
        table.state.startConfirmDelete();

        var selection = table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);
        assertNull(selection);
        assertTrue(deleted.get());
    }

    @Test
    void testHandleNormalBinding_NavigationAndPaging() {
        var table = new InteractiveTable<>(
                List.of("A", "B"),
                s -> s,
                s -> s,
                p -> new InteractiveTable.PageResult<>(p == 2 ? List.of("C") : List.of("A", "B"), p < 2),
                true,
                null
        );

        table.handleBinding("DOWN", InteractiveTableState.Mode.NORMAL);
        assertEquals(1, table.state.getSelectedIndex());

        table.handleBinding("UP", InteractiveTableState.Mode.NORMAL);
        assertEquals(0, table.state.getSelectedIndex());

        table.handleBinding("FILTER", InteractiveTableState.Mode.NORMAL);
        assertEquals(InteractiveTableState.Mode.FILTER_INPUT, table.state.getMode());
        table.state.commitFilter();

        table.handleBinding("NEXT_PAGE", InteractiveTableState.Mode.NORMAL);
        assertEquals(List.of("C"), table.state.getVisibleRows());

        table.handleBinding("PREV_PAGE", InteractiveTableState.Mode.NORMAL);
        assertEquals(List.of("A", "B"), table.state.getVisibleRows());
    }

    @Test
    void testHandleFilterBinding_EscapeAndBackspace() {
        var table = new InteractiveTable<>(List.of("Alpha", "Beta"), s -> s, s -> s, null, false, null);
        table.state.startFilterInput();

        table.handleBinding("A", InteractiveTableState.Mode.FILTER_INPUT);
        table.handleBinding("l", InteractiveTableState.Mode.FILTER_INPUT);
        assertEquals("Al", table.state.getFilterText());

        table.handleBinding("BACKSPACE", InteractiveTableState.Mode.FILTER_INPUT);
        assertEquals("A", table.state.getFilterText());

        table.handleBinding("ESC", InteractiveTableState.Mode.FILTER_INPUT);
        assertEquals("", table.state.getFilterText());
        assertEquals(InteractiveTableState.Mode.NORMAL, table.state.getMode());
    }

    @Test
    void testRefreshCurrentPage_FallbackToPreviousPage() {
        var rowsPage1 = List.of("A");
        var rowsPage2 = new ArrayList<>(List.of("B"));
        var table = new InteractiveTable<String>(
                rowsPage1,
                s -> s,
                s -> s,
                p -> new InteractiveTable.PageResult<>(p == 1 ? rowsPage1 : rowsPage2, false),
                true,
                item -> rowsPage2.remove(item)
        );

        table.goToPage(2);
        assertEquals(List.of("B"), table.state.getVisibleRows());

        table.state.startConfirmDelete();
        table.handleBinding("CONFIRM_YES", InteractiveTableState.Mode.CONFIRM_DELETE);

        // Page 2 was emptied, so refresh falls back to page 1
        assertEquals(List.of("A"), table.state.getVisibleRows());
    }

    @Test
    void testNormalKeyMap_ArrowKeysBound() throws IOException {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);
        try (Terminal terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream())) {
            var keyMap = table.buildNormalKeyMap(terminal);
            assertEquals("UP", keyMap.getBound("\033[A"));
            assertEquals("DOWN", keyMap.getBound("\033[B"));
            assertEquals("UP", keyMap.getBound("\033OA"));
            assertEquals("DOWN", keyMap.getBound("\033OB"));
            assertEquals("ENTER", keyMap.getBound("\r"));
            assertEquals("ENTER", keyMap.getBound("\n"));
            assertEquals("QUIT", keyMap.getBound("q"));
            assertEquals("QUIT", keyMap.getBound("\u0003"));
            assertEquals("DELETE", keyMap.getBound("d"));
            assertEquals("FILTER", keyMap.getBound("/"));
            // A bare ESC quits; an ESC that opens a sequence must not. That distinction is the bug.
            assertEquals("ESC", keyMap.getBound("\033"));
        }
    }

    // Only types whose caps ship inside the JLine jar, so the test does not depend on the host's
    // terminfo database. xterm/screen/windows advertise \EOA and rxvt-unicode/ansi advertise \E[A,
    // so binding only the terminfo sequence fails one of the two checks on every type.
    // Terminals are constructed directly: TerminalBuilder probes the system streams by spawning
    // processes, which blocks for minutes under Surefire on Windows (Git Bash).
    @ParameterizedTest
    @ValueSource(strings = {"xterm-256color", "xterm", "screen-256color", "rxvt-unicode", "ansi",
            "windows-vtp", "windows", "windows-256color"})
    void testNormalKeyMap_ArrowKeysBoundOnEveryTerminalType(String type) throws IOException {
        var table = new InteractiveTable<>(List.of("A"), s -> s, s -> s, p -> new InteractiveTable.PageResult<>(List.of(), false), false, null);
        try (Terminal terminal = new ExternalTerminal("test", type,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), StandardCharsets.UTF_8)) {
            var keyMap = table.buildNormalKeyMap(terminal);
            // Normal cursor mode
            assertEquals("UP", keyMap.getBound("\033[A"));
            assertEquals("DOWN", keyMap.getBound("\033[B"));
            // Application cursor mode
            assertEquals("UP", keyMap.getBound("\033OA"));
            assertEquals("DOWN", keyMap.getBound("\033OB"));
            // A bare ESC still quits. Before the fix the arrow sequences had no binding, so their
            // leading ESC matched this one and the first arrow key press exited the TUI.
            assertEquals("ESC", keyMap.getBound("\033"));
        }
    }
}
