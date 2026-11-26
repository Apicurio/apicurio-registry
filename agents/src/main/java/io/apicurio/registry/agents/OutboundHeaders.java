package io.apicurio.registry.agents;

import io.apicurio.registry.rest.v3.beans.Headers;
import jakarta.ws.rs.BadRequestException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks the HTTP headers that a request asks the registry to send to an outbound target, such as
 * an MCP server or an inventory source. Header values are credentials, so error messages repeat a
 * sanitized header name at most, never a value.
 */
public final class OutboundHeaders {

    private OutboundHeaders() {
    }

    /**
     * @param requested the headers in the request, or {@code null}
     * @param reserved  lower-case names the caller may not set, because the transport sets them
     * @param target    how the target is named in error messages, for example "the MCP server"
     * @return the headers, unchanged
     * @throws BadRequestException when a name is not an HTTP token or is reserved, or a value
     *                             contains a control character
     */
    public static Map<String, String> validate(Headers requested, Set<String> reserved, String target) {
        if (requested == null || requested.getAdditionalProperties().isEmpty()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (var header : requested.getAdditionalProperties().entrySet()) {
            String name = header.getKey();
            if (name == null || name.isEmpty() || !name.chars().allMatch(OutboundHeaders::isTokenChar)
                    || reserved.contains(name.toLowerCase(Locale.ROOT))) {
                // The name is safe to repeat; the value is never repeated.
                throw new BadRequestException("Header '" + sanitize(name) + "' cannot be sent to " + target + ".");
            }
            if (!(header.getValue() instanceof String value) || value.chars().anyMatch(OutboundHeaders::isControl)) {
                throw new BadRequestException("Header '" + sanitize(name) + "' has an invalid value.");
            }
            headers.put(name, value);
        }
        return Map.copyOf(headers);
    }

    /**
     * A control character, which RFC 9110 does not allow in a field value (horizontal tab aside).
     * The HTTP client would refuse it with an exception that repeats the value.
     */
    private static boolean isControl(int c) {
        return c < 0x20 && c != '\t' || c == 0x7f;
    }

    /** A character allowed in an HTTP field name: RFC 9110 {@code tchar}. */
    private static boolean isTokenChar(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.replaceAll("[^A-Za-z0-9-]", "?");
    }
}
