package io.apicurio.registry.utils;

import static java.util.Objects.requireNonNull;

public class StringUtil {

    public static boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static String limitStr(String value, int limit) {
        return limitStr(value, limit, false);
    }

    public static String asLowerCase(String value) {
        if (value == null) {
            return null;
        }
        return value.toLowerCase();
    }

    public static String limitStr(String value, int limit, boolean withEllipsis) {
        if (StringUtil.isEmpty(value)) {
            return value;
        }

        if (value.length() > limit) {
            if (withEllipsis) {
                return value.substring(0, limit - 3).concat("...");
            } else {
                return value.substring(0, limit);
            }
        } else {
            return value;
        }
    }

    /**
     * Converts a byte array to a readable string representation, showing printable ASCII characters
     * where possible and hex codes for non-printable bytes.
     */
    public static String toReadableString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (byte b : bytes) {
            // Check if byte is a printable ASCII character (space to tilde: 32-126)
            if (b >= 32 && b <= 126) {
                sb.append((char) b);
            } else {
                // Show as hex for non-printable bytes
                sb.append(String.format("\\%02X", b & 0xFF));
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public static boolean contains(String value, String chars) {
        requireNonNull(value, "value");
        requireNonNull(chars, "chars");
        for (char c : chars.toCharArray()) {
            if (value.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}
