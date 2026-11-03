package io.apicurio.registry.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RestMetricsResponseFilterSanitizeTest {

    @Test
    void replacesLineBreaksToPreventLogForging() {
        assertEquals("/apis/registry/v3/groups__FAKE LOG LINE",
                RestMetricsResponseFilter.sanitizeForLog("/apis/registry/v3/groups\r\nFAKE LOG LINE"));
    }

    @Test
    void replacesOtherControlCharacters() {
        assertEquals("/a_b_c", RestMetricsResponseFilter.sanitizeForLog("/a\tb\u0000c"));
    }

    @Test
    void leavesRegularPathUntouched() {
        assertEquals("/apis/registry/v3/groups/my-group/artifacts",
                RestMetricsResponseFilter.sanitizeForLog("/apis/registry/v3/groups/my-group/artifacts"));
    }

    @Test
    void nullStaysNull() {
        assertNull(RestMetricsResponseFilter.sanitizeForLog(null));
    }
}
