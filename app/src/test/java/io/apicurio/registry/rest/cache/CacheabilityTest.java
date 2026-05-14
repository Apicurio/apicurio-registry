package io.apicurio.registry.rest.cache;

import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rest.cache.Cacheability.HIGH;
import static io.apicurio.registry.rest.cache.Cacheability.LOW;
import static io.apicurio.registry.rest.cache.Cacheability.MODERATE;
import static io.apicurio.registry.rest.cache.Cacheability.NONE;
import static io.apicurio.registry.rest.cache.Cacheability.min;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CacheabilityTest {

    @Test
    void minReturnsSingleValue() {
        assertEquals(HIGH, min(HIGH));
        assertEquals(NONE, min(NONE));
    }

    @Test
    void minReturnsLowest() {
        assertEquals(LOW, min(HIGH, MODERATE, LOW));
        assertEquals(NONE, min(HIGH, NONE));
        assertEquals(MODERATE, min(HIGH, MODERATE));
    }

    @Test
    void minIgnoresNulls() {
        assertEquals(HIGH, min(HIGH, null));
        assertEquals(MODERATE, min(null, MODERATE, null));
    }

    @Test
    void minWithAllNullsThrows() {
        assertThrows(IllegalArgumentException.class, () -> min((Cacheability) null));
    }

    @Test
    void minWithNullArrayThrows() {
        assertThrows(IllegalArgumentException.class, () -> min((Cacheability[]) null));
    }

    @Test
    void minIsIdempotent() {
        assertEquals(LOW, min(HIGH, min(MODERATE, LOW)));
    }
}
