package io.apicurio.registry.cli.utils;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

public final class ColorUtil {

    private ColorUtil() {
    }

    private static volatile boolean enabled = true;
    private static volatile boolean installed = false;

    public static synchronized void init(boolean on) {
        enabled = on;
        if (on && !installed) {
            AnsiConsole.systemInstall();
            installed = true;
        }
    }

    public static synchronized void shutdown() {
        if (installed) {
            AnsiConsole.systemUninstall();
            installed = false;
        }
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String colorizeSuccess(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        return Ansi.ansi().fg(Ansi.Color.GREEN).a(text).reset().toString();
    }

    public static String colorizeError(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        return Ansi.ansi().fg(Ansi.Color.RED).a(text).reset().toString();
    }

    public static String colorizeWarning(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return text;
        }
        return Ansi.ansi().fg(Ansi.Color.YELLOW).a(text).reset().toString();
    }
}