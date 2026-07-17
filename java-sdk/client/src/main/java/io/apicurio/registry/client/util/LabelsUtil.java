package io.apicurio.registry.client.util;

import io.apicurio.registry.rest.client.models.Labels;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

/**
 * Utility methods for working with the generated {@link Labels} client model and with the
 * {@code key=value} label syntax used across SDK consumers (e.g. the CLI).
 * <p>
 * {@link Labels} is a Kiota "additional data" model: its entries are not typed fields but
 * are instead stored in a generic {@code Map<String, Object>}. Consumers of the SDK
 * generally want a plain {@code Map<String, String>} or a simple display string instead, so
 * these conversions are centralized here rather than being duplicated by every caller.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/8644">#8644</a>
 */
public final class LabelsUtil {

    private static final char ESCAPE_CHAR = '\\';
    private static final char LABEL_SEPARATOR = '=';

    private LabelsUtil() {
    }

    /**
     * Converts a {@link Labels} instance (as returned by the generated REST client) into a
     * plain {@code Map<String, String>}. Returns an empty map if {@code labels} is
     * {@code null} or has no additional data.
     *
     * @param labels the labels model returned by the SDK, may be {@code null}
     * @return a non-null map of label key/value pairs
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> toMap(Labels labels) {
        return ofNullable(labels)
                .map(Labels::getAdditionalData)
                .map(additionalData -> (Map<String, String>) (Map<String, ?>) additionalData)
                .orElse(Map.of());
    }

    /**
     * Formats a {@link Labels} instance as a comma-separated {@code key=value} string,
     * suitable for simple display purposes (e.g. CLI table output). Returns an empty
     * string if {@code labels} is {@code null} or has no additional data.
     *
     * @param labels the labels model returned by the SDK, may be {@code null}
     * @return a non-null, comma-separated {@code key=value} string
     */
    public static String toDisplayString(Labels labels) {
        return ofNullable(labels)
                .map(Labels::getAdditionalData)
                .map(LabelsUtil::toDisplayString)
                .orElse("");
    }

    /**
     * Formats a generic label map as a comma-separated {@code key=value} string, suitable
     * for simple display purposes (e.g. CLI table output). Returns an empty string if
     * {@code labels} is {@code null}.
     *
     * @param labels the label map to format, may be {@code null}
     * @return a non-null, comma-separated {@code key=value} string
     */
    public static String toDisplayString(Map<String, ?> labels) {
        return ofNullable(labels)
                .map(map -> map.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(",")))
                .orElse("");
    }

    /**
     * Parses a list of {@code key=value} (or bare {@code key}) label strings, as typically
     * supplied via repeated CLI flags, into a label map. Supports {@code \} as an escape
     * character so that keys or values containing literal {@code =} or {@code \} can be
     * expressed as {@code \=} / {@code \\}.
     *
     * @param labels the raw {@code key=value} strings to parse, must not be {@code null}
     * @return a non-null, insertion-ordered map of label key/value pairs
     */
    public static Map<String, String> parseLabels(final List<String> labels) {
        final Map<String, String> result = new LinkedHashMap<>();
        for (final String label : labels) {
            final Map.Entry<String, String> entry = splitLabel(label);
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Splits a single {@code key=value} (or bare {@code key}) label string into its key and
     * value, honoring {@code \} as an escape character in the key portion. If there is no
     * unescaped {@code =}, the whole (unescaped) string is treated as the key with an empty
     * value.
     *
     * @param label the raw label string to split, must not be {@code null}
     * @return a non-null key/value entry
     */
    public static Map.Entry<String, String> splitLabel(final String label) {
        final int separatorIndex = findUnescapedEquals(label);
        if (separatorIndex < 0) {
            return new AbstractMap.SimpleImmutableEntry<>(unescape(label), "");
        }
        final String key = unescape(label.substring(0, separatorIndex));
        final String value = label.substring(separatorIndex + 1);
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static String unescape(final String input) {
        final StringBuilder result = new StringBuilder(input.length());
        boolean escaped = false;
        for (int index = 0; index < input.length(); index++) {
            final char ch = input.charAt(index);
            if (escaped) {
                result.append(ch);
                escaped = false;
            } else if (ch == ESCAPE_CHAR) {
                escaped = true;
            } else {
                result.append(ch);
            }
        }
        if (escaped) {
            result.append(ESCAPE_CHAR);
        }
        return result.toString();
    }

    private static int findUnescapedEquals(final String label) {
        boolean escaped = false;
        for (int index = 0; index < label.length(); index++) {
            final char ch = label.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (ch == ESCAPE_CHAR) {
                escaped = true;
            } else if (ch == LABEL_SEPARATOR) {
                return index;
            }
        }
        return -1;
    }
}
