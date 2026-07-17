package io.apicurio.registry.client.common.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DateTimeConversionsTest {

    // Matches an ISO local date-time string, e.g. "2026-07-16T10:15:30". Used to
    // independently verify the *shape* of the output without relying on
    // DateTimeFormatter.ISO_LOCAL_DATE_TIME, the same API the code under test uses to
    // produce it.
    private static final Pattern ISO_LOCAL_DATE_TIME_PATTERN =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})$");

    @Test
    public void testToDate() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 7, 16, 10, 15, 30, 0, ZoneOffset.UTC);

        Date result = DateTimeConversions.toDate(ts);

        assertEquals(ts.toInstant(), result.toInstant());
    }

    @Test
    public void testToIsoLocalDateTimeString_offsetDateTime() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 7, 16, 10, 15, 30, 0, ZoneOffset.UTC);

        String result = DateTimeConversions.toIsoLocalDateTimeString(ts);

        assertMatchesIndependentlyComputedLocalDateTime(ts.toInstant(), result);
    }

    @Test
    public void testToIsoLocalDateTimeString_date() {
        OffsetDateTime ts = OffsetDateTime.of(2026, 7, 16, 10, 15, 30, 0, ZoneOffset.UTC);
        Date date = Date.from(ts.toInstant());

        String result = DateTimeConversions.toIsoLocalDateTimeString(date);

        assertMatchesIndependentlyComputedLocalDateTime(ts.toInstant(), result);
    }

    /**
     * Verifies the formatted string against a value assembled by hand from the
     * individual {@link LocalDateTime} fields (year/month/day/hour/minute/second),
     * rather than by calling the same {@code DateTimeFormatter} the code under test
     * uses. This catches regressions (e.g. wrong zone conversion, truncated seconds,
     * wrong field order) that a "recompute with the identical formatter" assertion
     * would not.
     */
    private static void assertMatchesIndependentlyComputedLocalDateTime(java.time.Instant instant, String actual) {
        LocalDateTime expectedLocal = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        String expected = String.format("%04d-%02d-%02dT%02d:%02d:%02d",
                expectedLocal.getYear(), expectedLocal.getMonthValue(), expectedLocal.getDayOfMonth(),
                expectedLocal.getHour(), expectedLocal.getMinute(), expectedLocal.getSecond());

        assertEquals(expected, actual);

        Matcher matcher = ISO_LOCAL_DATE_TIME_PATTERN.matcher(actual);
        assertTrue(matcher.matches(), "Expected an ISO local date-time string, got: " + actual);
    }
}
