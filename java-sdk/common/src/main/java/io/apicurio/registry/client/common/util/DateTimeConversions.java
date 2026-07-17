package io.apicurio.registry.client.common.util;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Generic date/time conversion and formatting helpers shared by SDK consumers (CLI, tests,
 * integrations). These are intentionally simple, dependency-free conversions between
 * {@link OffsetDateTime}, {@link Date}, and {@link String}.
 * <p>
 * For parsing dates coming out of the generated REST client (including legacy date format
 * fallback), see {@link DateTimeUtil} instead.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/8644">#8644</a>
 */
public final class DateTimeConversions {

    private DateTimeConversions() {
    }

    /**
     * Converts an {@link OffsetDateTime} to a {@link Date}.
     *
     * @param timestamp the timestamp to convert, must not be {@code null}
     * @return the equivalent {@link Date}
     */
    public static Date toDate(OffsetDateTime timestamp) {
        return Date.from(timestamp.toInstant());
    }

    /**
     * Formats an {@link OffsetDateTime} as an ISO local date-time string (using the JVM's
     * default time zone), e.g. {@code 2026-07-16T10:15:30}.
     *
     * @param timestamp the timestamp to format, must not be {@code null}
     * @return the formatted date-time string
     */
    public static String toIsoLocalDateTimeString(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * Formats a {@link Date} as an ISO local date-time string (using the JVM's default time
     * zone), e.g. {@code 2026-07-16T10:15:30}.
     *
     * @param timestamp the timestamp to format, must not be {@code null}
     * @return the formatted date-time string
     */
    public static String toIsoLocalDateTimeString(Date timestamp) {
        return timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
