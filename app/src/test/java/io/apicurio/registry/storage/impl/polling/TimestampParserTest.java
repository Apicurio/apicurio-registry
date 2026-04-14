package io.apicurio.registry.storage.impl.polling;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimestampParserTest {

    private static final Instant FALLBACK = Instant.ofEpochMilli(999L);

    @Test
    void nullReturnsDefault() {
        assertEquals(999L, TimestampParser.parse(null, FALLBACK));
    }

    @Test
    void blankReturnsDefault() {
        assertEquals(999L, TimestampParser.parse("  ", FALLBACK));
    }

    @Test
    void unixMilliseconds() {
        assertEquals(1709510400000L, TimestampParser.parse("1709510400000", FALLBACK));
    }

    @Test
    void isoWithTimezone() {
        // 2024-03-04T00:00:00Z = 1709510400000 ms
        assertEquals(1709510400000L, TimestampParser.parse("2024-03-04T00:00:00Z", FALLBACK));
    }

    @Test
    void isoWithOffset() {
        // 2024-03-04T01:00:00+01:00 = 2024-03-04T00:00:00Z
        assertEquals(1709510400000L, TimestampParser.parse("2024-03-04T01:00:00+01:00", FALLBACK));
    }

    @Test
    void isoWithoutTimezone() {
        // Assumed UTC
        assertEquals(1709510400000L, TimestampParser.parse("2024-03-04T00:00:00", FALLBACK));
    }

    @Test
    void dateOnly() {
        // Midnight UTC
        assertEquals(1709510400000L, TimestampParser.parse("2024-03-04", FALLBACK));
    }

    @Test
    void whitespaceIsTrimmed() {
        assertEquals(1709510400000L, TimestampParser.parse("  2024-03-04  ", FALLBACK));
    }

    @Test
    void invalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> TimestampParser.parse("not-a-date", FALLBACK));
    }
}
