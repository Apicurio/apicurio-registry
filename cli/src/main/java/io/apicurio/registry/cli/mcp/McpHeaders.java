package io.apicurio.registry.cli.mcp;

import io.apicurio.registry.cli.common.CliException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

/**
 * Reads the HTTP headers to forward to an MCP server during an import.
 * <p>
 * Header values often hold credentials, so they are never taken from a plain command-line option,
 * which the shell history and the process list would expose. They come from a file, with one
 * {@code Name: value} per line, or from environment variables. Error messages name the header or
 * the variable, never a value.
 */
final class McpHeaders {

    private static final String HEADER = "Header '";

    private McpHeaders() {
    }

    /**
     * @param fileContent the content of the header file, or null when there is none
     * @param envMappings {@code Name=VARIABLE} pairs, or null
     * @param env         looks up an environment variable
     * @return the headers by name, in the order given
     * @throws CliException with the validation exit code if a header is malformed or repeated
     */
    static Map<String, String> read(final String fileContent, final List<String> envMappings,
                                    final UnaryOperator<String> env) {
        final Map<String, String> headers = new LinkedHashMap<>();
        if (fileContent != null) {
            readFile(headers, fileContent);
        }
        if (envMappings != null) {
            envMappings.forEach(mapping -> readEnv(headers, mapping, env));
        }
        return headers;
    }

    private static void readFile(final Map<String, String> headers, final String fileContent) {
        final String[] lines = fileContent.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            final int colon = line.indexOf(':');
            if (colon <= 0) {
                throw invalid("Line " + (i + 1) + " of the header file is not 'Name: value'.");
            }
            put(headers, line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }
    }

    private static void readEnv(final Map<String, String> headers, final String mapping,
                                final UnaryOperator<String> env) {
        final int equals = mapping.indexOf('=');
        if (equals <= 0 || equals == mapping.length() - 1) {
            throw invalid("--header-env must be Name=VARIABLE.");
        }
        final String name = mapping.substring(0, equals).strip();
        final String variable = mapping.substring(equals + 1).strip();
        final String value = env.apply(variable);
        if (value == null) {
            throw invalid("Environment variable '" + variable + "' for header '" + safeName(name)
                    + "' is not set.");
        }
        put(headers, name, value);
    }

    private static void put(final Map<String, String> headers, final String name, final String value) {
        if (name.isEmpty() || !name.chars().allMatch(McpHeaders::isTokenChar)) {
            throw invalid("Header name '" + safeName(name) + "' is not a valid HTTP header name.");
        }
        if (value.isEmpty()) {
            throw invalid(HEADER + name + "' has an empty value.");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw invalid(HEADER + name + "' has an invalid value.");
        }
        final boolean repeated = headers.keySet().stream()
                .anyMatch(existing -> existing.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)));
        if (repeated) {
            throw invalid(HEADER + name + "' is given more than once.");
        }
        headers.put(name, value);
    }

    /** A character allowed in an HTTP field name: RFC 9110 {@code tchar}. */
    private static boolean isTokenChar(final int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || "!#$%&'*+-.^_`|~".indexOf(c) >= 0;
    }

    /** The name with anything outside the token set replaced, so it is safe to print. */
    private static String safeName(final String name) {
        final StringBuilder safe = new StringBuilder();
        name.chars().forEach(c -> safe.append(isTokenChar(c) ? (char) c : '?'));
        return safe.toString();
    }

    private static CliException invalid(final String message) {
        return new CliException(message, VALIDATION_ERROR_RETURN_CODE);
    }
}
