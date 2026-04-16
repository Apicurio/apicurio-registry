package io.apicurio.registry.storage.impl.polling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * Parses timestamp strings from GitOps/polling data files into epoch milliseconds.
 *
 * <p>Supported formats (tried in order):
 * <ul>
 *   <li>Unix milliseconds: {@code 1709510400000}</li>
 *   <li>ISO 8601 with timezone: {@code 2024-03-04T10:30:00Z}, {@code 2024-03-04T10:30:00+01:00}</li>
 *   <li>ISO 8601 without timezone (assumed UTC): {@code 2024-03-04T10:30:00}</li>
 *   <li>Date only (midnight UTC): {@code 2024-03-04}</li>
 * </ul>
 */
public final class TimestampParser {

    private TimestampParser() {
    }

    /**
     * Parses a timestamp string into epoch milliseconds.
     *
     * @param value the timestamp string
     * @param fallback Instant to use if value is null or blank
     * @return epoch milliseconds
     * @throws IllegalArgumentException if the value cannot be parsed
     */
    public static long parse(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback.toEpochMilli();
        }
        value = value.trim();

        // Try numeric (unix milliseconds)
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
        }

        // Try ISO 8601 with timezone (e.g. 2024-03-04T10:30:00Z, 2024-03-04T10:30:00+01:00)
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        // Try ISO 8601 without timezone (assumed UTC)
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        // Try date only (midnight UTC)
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }

        throw new IllegalArgumentException("Cannot parse timestamp: '" + value
                + "'. Expected ISO 8601 (e.g. 2024-03-04, 2024-03-04T10:30:00Z) or unix milliseconds.");
    }
}
