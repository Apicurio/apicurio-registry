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

}
