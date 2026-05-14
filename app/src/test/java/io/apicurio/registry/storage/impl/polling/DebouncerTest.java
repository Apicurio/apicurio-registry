package io.apicurio.registry.storage.impl.polling;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebouncerTest {

    @Test
    void disabledDebouncing() {
        // Zero quiet period = process immediately
        var debouncer = new Debouncer<String>(Duration.ZERO, Duration.ZERO);

        assertFalse(debouncer.isReady(), "Should not be ready without pending change");

        debouncer.onChange("change1");
        assertTrue(debouncer.isReady(), "Should be ready immediately when debouncing is disabled");
        assertEquals("change1", debouncer.pending());

        debouncer.reset();
        assertNull(debouncer.pending());
        assertFalse(debouncer.isReady());
    }

    @Test
    void quietPeriodPreventsImmediateProcessing() {
        var debouncer = new Debouncer<String>(Duration.ofSeconds(60), Duration.ZERO);

        debouncer.onChange("change1");
        assertFalse(debouncer.isReady(), "Should not be ready during quiet period");
        assertEquals("change1", debouncer.pending());
    }

    @Test
    void latestChangeReplacesPrevious() {
        var debouncer = new Debouncer<String>(Duration.ZERO, Duration.ZERO);

        debouncer.onChange("first");
        debouncer.onChange("second");
        debouncer.onChange("third");

        assertEquals("third", debouncer.pending(), "Should hold the latest change");
    }
}
