package io.apicurio.registry.cli.utils;

import java.util.Locale;

public final class PlatformUtils {

    public static final String OS_MAC = "osx";
    public static final String OS_MAC_IDENTIFIER = "mac";
    public static final String OS_DARWIN_IDENTIFIER = "darwin";
    public static final String OS_LINUX = "linux";

    private PlatformUtils() {
    }

    public static String detectOsClassifier() {
        var osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains(OS_LINUX)) {
            return OS_LINUX;
        }
        if (osName.contains(OS_MAC_IDENTIFIER) || osName.contains(OS_DARWIN_IDENTIFIER)) {
            return OS_MAC;
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

    public static boolean isMacOS() {
        return OS_MAC.equals(detectOsClassifier());
    }

    public static String detectPlatformClassifier() {
        return detectOsClassifier() + "-" + detectArchClassifier();
    }
}
