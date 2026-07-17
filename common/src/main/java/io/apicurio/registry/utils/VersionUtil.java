package io.apicurio.registry.utils;

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

    public static String generateVersionSortKey(String version) {
        if (version == null || version.trim().isEmpty()) {
            return null;
        }
        String originalVersion = version;
        String withoutBuild = version.split("\\+")[0];
        String[] parts = withoutBuild.split("-", 2);
        String core = parts[0];
        String prerelease = parts.length > 1 ? parts[1] : "";
        if (core.toLowerCase().startsWith("v")) {
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
        
        return String.format("%010d.%010d.%010d%s", major, minor, patch, preBuilder);
    }

    private static String formatPrerelease(String prerelease) {
        if (prerelease.isEmpty()) {
            return "~";
        }
        
        StringBuilder preBuilder = new StringBuilder("-");
        String[] preParts = prerelease.split("\\.");
        
        for (int i = 0; i < preParts.length; i++) {
            if (i > 0) {
                preBuilder.append(".");
            }
            String p = preParts[i];
            if (p.matches("\\d+")) {
                try {
                    preBuilder.append(String.format("%010d", Long.parseLong(p)));
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

