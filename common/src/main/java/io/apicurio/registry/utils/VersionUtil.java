package io.apicurio.registry.utils;

import java.util.Locale;

public class VersionUtil {

    public static final String toString(Number version) {
        return version != null ? String.valueOf(version) : null;
    }

    public static final Integer toInteger(String version) {
        if (version == null) {
            return null;
        }
        try {
            return Integer.valueOf(version);
        } catch (NumberFormatException e) {
            // TODO what to do here?
            return null;
        }
    }

    public static final Long toLong(String version) {
        if (version == null) {
            return null;
        }
        try {
            return Long.valueOf(version);
        } catch (NumberFormatException e) {
            // TODO what to do here?
            return null;
        }
    }

    /**
     * Generates a collation-agnostic sort key for semantic versions.
     * The key ensures correct database ordering regardless of SQL dialect.
     * 
     * Format: {major}.{minor}.{patch}-{flag}-{prerelease}
     * Flags: "-0-" for prerelease, "-1" for final release.
     * 
     * Note: This is a lexical approximation of SemVer, not a strict implementation.
     * It handles standard cases perfectly but intentionally approximates edge cases 
     * for SQL collation (e.g., 1.0 and 1.0.0 will collide, a 4th segment like 1.2.3.4 
     * is truncated). Additionally, invalid SemVer numeric segments with >= 10 digits 
     * or negative segments will break the fixed-width alignment and sort incorrectly.
     * 
     * If the version string is not at all parseable, it falls back to
     * a "NON_SEMVER_" prefix appended with the original version.
     */
    public static String generateVersionSortKey(String version) {
        if (version == null || version.trim().isEmpty()) {
            return null;
        }
        String originalVersion = version;
        String withoutBuild = version.split("\\+")[0];
        String[] parts = withoutBuild.split("-", 2);
        String core = parts[0];
        String prerelease = parts.length > 1 ? parts[1] : "";
        if (core.toLowerCase(Locale.ROOT).startsWith("v")) {
            core = core.substring(1);
        }
        String[] coreParts = core.split("\\.");
        long major = 0;
        long minor = 0;
        long patch = 0;
        
        try {
            major = coreParts.length > 0 && !coreParts[0].isEmpty() ? Long.parseLong(coreParts[0]) : 0;
            minor = coreParts.length > 1 && !coreParts[1].isEmpty() ? Long.parseLong(coreParts[1]) : 0;
            patch = coreParts.length > 2 && !coreParts[2].isEmpty() ? Long.parseLong(coreParts[2]) : 0;
        } catch (NumberFormatException e) {
            return "NON_SEMVER_" + originalVersion;
        }
        String preBuilder = formatPrerelease(prerelease);
        
        return String.format(Locale.ROOT, "%010d.%010d.%010d%s", major, minor, patch, preBuilder);
    }

    private static String formatPrerelease(String prerelease) {
        if (prerelease.isEmpty()) {
            return "-1";
        }
        
        StringBuilder preBuilder = new StringBuilder("-0-");
        String[] preParts = prerelease.split("\\.");
        
        for (int i = 0; i < preParts.length; i++) {
            if (i > 0) {
                preBuilder.append(".");
            }
            String p = preParts[i];
            if (p.matches("\\d+")) {
                try {
                    preBuilder.append(String.format(Locale.ROOT, "%010d", Long.parseLong(p)));
                } catch (NumberFormatException e) {
                    preBuilder.append(p);
                }
            } else {
                preBuilder.append(p); 
            }
        }
        return preBuilder.toString();
    }
}
