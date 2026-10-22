package io.apicurio.registry.utils;

import java.util.Locale;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

public class StringUtil {

    /**
     * Characters that must never reach a log line verbatim. CR and LF are the dangerous ones - they let
     * a user-supplied value forge additional log entries - but every ISO control character is replaced
     * so terminal escape sequences cannot be smuggled through either.
     */
    private static final Pattern LOG_UNSAFE_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private static final int LOG_VALUE_LIMIT = 256;

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
        return value.toLowerCase(Locale.ROOT);
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

    /**
     * Makes a user-supplied value safe to write to a log line: control characters (CR/LF above all)
     * are replaced so the value cannot forge extra log entries, and the result is truncated so an
     * oversized value cannot flood the log.
     *
     * @param value the value to sanitize, may be null
     * @return the sanitized value, or null if the input was null
     */
    public static String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return limitStr(LOG_UNSAFE_CHARACTERS.matcher(value).replaceAll("_"), LOG_VALUE_LIMIT, true);
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
