package io.apicurio.registry.cli.utils;

import org.fusesource.jansi.AnsiConsole;

import java.io.Console;

/**
 * Detects the width of the terminal the CLI is running in.
 * <p>
 * The width is detected once and cached, since the cost of the native call is not worth paying
 * for every table and a resize mid-command is not a supported scenario.
 */
public final class TerminalUtils {

    /**
     * Width used when running in a terminal whose width cannot be detected.
     * Wide enough for most tables without being unwieldy.
     */
    public static final int DEFAULT_WIDTH = 120;

    private static volatile Integer cachedWidth;

    private TerminalUtils() {
    }

    /**
     * Returns the detected terminal width in characters, or 0 (no width limit) when the output
     * is not a terminal, falling back to {@link #DEFAULT_WIDTH} when running in a terminal
     * whose width cannot be detected.
     */
    public static int getTerminalWidth() {
        var width = cachedWidth;
        if (width == null) {
            width = detectWidth();
            cachedWidth = width;
        }
        return width;
    }

    private static int detectWidth() {
        if (!isTerminal()) {
            // Piped or redirected output renders at natural width, so scripts and
            // other consumers never see wrapped or truncated values.
            return 0;
        }
        try {
            var width = AnsiConsole.getTerminalWidth();
            if (width > 0) {
                return width;
            }
        } catch (Throwable ex) {
            // Jansi could not load its native library on this platform. Fall through.
        }
        var width = parseColumns(System.getenv("COLUMNS"));
        return width > 0 ? width : DEFAULT_WIDTH;
    }

    private static boolean isTerminal() {
        var console = System.console();
        if (console == null) {
            return false;
        }
        // Since JDK 22, System.console() is non-null even for redirected streams,
        // and Console::isTerminal must be consulted instead. Invoked reflectively
        // because the CLI compiles with an older source level.
        try {
            return (Boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (NoSuchMethodException ex) {
            // Pre-JDK 22: Console::isTerminal does not exist, but System.console()
            // already returns null for redirected streams, so a non-null console
            // means a real terminal.
            return true;
        } catch (ReflectiveOperationException ex) {
            // Unexpected reflective failure on JDK 22+, where a non-null console no
            // longer implies a terminal. Fail toward the safe default width.
            return false;
        }
    }

    static int parseColumns(String columns) {
        if (columns == null || columns.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(columns.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
