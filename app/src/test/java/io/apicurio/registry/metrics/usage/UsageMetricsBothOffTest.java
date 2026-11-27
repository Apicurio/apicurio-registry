package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.rest.UsageTelemetryBuffer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Neither the SQL usage telemetry nor the usage metric is enabled, which is the default. */
@QuarkusTest
@TestProfile(UsageMetricsBothOffTest.Profile.class)
class UsageMetricsBothOffTest extends AbstractUsageMetricsTest {

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of();
        }
    }

    @InjectSpy
    UsageTelemetryBuffer buffer;

    @Test
    void identifiedFetchesProduceNeitherAMetricNorALegacyEvent() throws Exception {
        Schema schema = createSchema();

        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), newClient("both-off"), "SERIALIZE"));
        assertEquals(200, get(CONTENT_ROUTE + schema.contentId(), newClient("both-off"), "SERIALIZE"));

        // Two full exports after the fetches prove the SDK is exporting while the usage metric stays absent.
        awaitExports(2);
        assertTrue(receiver.metricNames().contains("apicurio.artifacts.created"));
        assertFalse(receiver.metricNames().contains(METRIC));
        verify(buffer, never()).addEvent(any());
    }
}
