package io.apicurio.registry.cli.utils;

import java.util.Locale;

public final class PlatformUtils {

    private PlatformUtils() {
    }

    public static String detectOsClassifier() {
        var osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) {
            return "linux";
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return "osx";
        }
        throw new UnsupportedOperationException("Unsupported OS: " + System.getProperty("os.name"));
    }

    public static String detectArchClassifier() {
        var arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch_64";
            default -> throw new UnsupportedOperationException("Unsupported architecture: " + arch);
        };
    }

    public static String detectPlatformClassifier() {
        return detectOsClassifier() + "-" + detectArchClassifier();
    }
}
