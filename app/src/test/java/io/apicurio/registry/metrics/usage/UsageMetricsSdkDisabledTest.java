package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.rest.UsageTelemetryBuffer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The usage metric is enabled but the OpenTelemetry SDK is disabled, which is the shipped default of
 * {@code quarkus.otel.sdk.disabled}. Fetches keep working and nothing is exported or stored.
 */
@QuarkusTest
@TestProfile(UsageMetricsSdkDisabledTest.Profile.class)
class UsageMetricsSdkDisabledTest extends AbstractUsageMetricsTest {

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of("quarkus.otel.sdk.disabled", "true",
                    "apicurio.usage.metrics.enabled", "true");
        }
    }

    @InjectSpy
    UsageTelemetryBuffer buffer;

    @Test
    void fetchesSucceedAndNothingIsExportedOrBuffered() throws Exception {
        Schema schema = createSchema();

        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), newClient("sdk-off"), "SERIALIZE"));
        assertEquals(200, get(CONTENT_ROUTE + schema.contentId(), newClient("sdk-off"), "SERIALIZE"));

        // The export interval is one second, so four quiet seconds would have shown any export.
        await().during(4, TimeUnit.SECONDS).atMost(8, TimeUnit.SECONDS)
                .until(() -> receiver.requestCount() == 0);
        verify(buffer, never()).addEvent(any());
    }
}
