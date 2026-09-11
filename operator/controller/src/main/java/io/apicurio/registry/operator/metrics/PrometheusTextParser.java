package io.apicurio.registry.operator.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal reader for the Prometheus text exposition format.
 * <p>
 * The operator only needs a handful of samples, so this deliberately does not build a full metric family
 * model. Malformed lines are skipped rather than failing the whole scrape, because a single unexpected line
 * should not cost us every other metric on the endpoint.
 */
public final class PrometheusTextParser {

    private PrometheusTextParser() {
    }

    public static List<MetricSample> parse(String body) {
        var samples = new ArrayList<MetricSample>();
        if (body == null) {
            return samples;
        }
        for (var rawLine : body.split("\n")) {
            var line = rawLine.strip();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            var sample = parseLine(line);
            if (sample != null) {
                samples.add(sample);
            }
        }
        return samples;
    }

    private static MetricSample parseLine(String line) {
        String name;
        Map<String, String> labels;
        String remainder;

        var openBrace = line.indexOf('{');
        if (openBrace < 0) {
            var space = line.indexOf(' ');
            if (space <= 0) {
                return null;
            }
            name = line.substring(0, space);
            labels = Map.of();
            remainder = line.substring(space + 1).strip();
        } else {
            var closeBrace = findClosingBrace(line, openBrace);
            if (closeBrace < 0) {
                return null;
            }
            name = line.substring(0, openBrace).strip();
            labels = parseLabels(line.substring(openBrace + 1, closeBrace));
            remainder = line.substring(closeBrace + 1).strip();
        }

        if (name.isEmpty() || remainder.isEmpty()) {
            return null;
        }
        // The remainder is the value, optionally followed by a timestamp we do not use.
        var space = remainder.indexOf(' ');
        var valueToken = space < 0 ? remainder : remainder.substring(0, space);
        var value = parseValue(valueToken);
        return value == null ? null : new MetricSample(name, labels, value);
    }

    /**
     * Label values are quoted and may contain braces, so the closing brace has to be found outside of quotes.
     */
    private static int findClosingBrace(String line, int openBrace) {
        var inQuotes = false;
        var escaped = false;
        for (var i = openBrace + 1; i < line.length(); i++) {
            var c = line.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '}' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, String> parseLabels(String raw) {
        var labels = new LinkedHashMap<String, String>();
        for (var pair : splitOutsideQuotes(raw)) {
            var equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            var key = pair.substring(0, equals).strip();
            var value = pair.substring(equals + 1).strip();
            if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
                value = unescape(value.substring(1, value.length() - 1));
            }
            if (!key.isEmpty()) {
                labels.put(key, value);
            }
        }
        return labels;
    }

    private static List<String> splitOutsideQuotes(String raw) {
        var parts = new ArrayList<String>();
        var current = new StringBuilder();
        var inQuotes = false;
        var escaped = false;
        for (var i = 0; i < raw.length(); i++) {
            var c = raw.charAt(i);
            if (escaped) {
                escaped = false;
                current.append(c);
            } else if (c == '\\') {
                escaped = true;
                current.append(c);
            } else if (c == '"') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }

    private static String unescape(String value) {
        var out = new StringBuilder(value.length());
        var escaped = false;
        for (var i = 0; i < value.length(); i++) {
            var c = value.charAt(i);
            if (escaped) {
                out.append(switch (c) {
                    case 'n' -> '\n';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> c;
                });
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static Double parseValue(String token) {
        return switch (token) {
            case "NaN" -> Double.NaN;
            case "+Inf", "Inf" -> Double.POSITIVE_INFINITY;
            case "-Inf" -> Double.NEGATIVE_INFINITY;
            default -> {
                try {
                    yield Double.parseDouble(token);
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
        };
    }
}
