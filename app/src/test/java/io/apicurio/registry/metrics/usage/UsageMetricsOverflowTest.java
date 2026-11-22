package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.metrics.UsageMetricsConfig;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The configured series cap reaches the SDK through the CDI customizer and is enforced on the exported data.
 * One test method owns the whole class, because the series of every method would share the same cap.
 */
@QuarkusTest
@TestProfile(UsageMetricsOverflowTest.Profile.class)
class UsageMetricsOverflowTest extends AbstractUsageMetricsTest {

    private static final int CAP = 4;

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of("apicurio.usage.metrics.enabled", "true",
                    "apicurio.usage.metrics.max-series", String.valueOf(CAP));
        }
    }

    @Inject
    UsageMetricsConfig metricsConfig;

    @Test
    void fetchesPastTheCapAreStillCountedButLoseTheirLabels() throws Exception {
        assertEquals(CAP, metricsConfig.getMaxSeries());
        Schema schema = createSchema();
        // The fetches are sequential, which is when the SDK keeps CAP - 1 detailed series and the overflow series:
        // concurrent first-seen clients can exceed the threshold, which this test does not claim otherwise.
        List<String> clients = List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7");
        for (String client : clients) {
            assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        }
        // Series that already exist keep counting, a client that is new goes to the overflow series.
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), "c1", "SERIALIZE"));
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), "c8", "SERIALIZE"));

        await().atMost(30, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            List<OtlpMetricsTestReceiver.Point> points = allSchemaFetchPoints();
            assertEquals(9L, points.stream().mapToLong(OtlpMetricsTestReceiver.Point::value).sum(),
                    "the total over all series, overflow included, is the number of fetches");
        });

        List<OtlpMetricsTestReceiver.Point> points = allSchemaFetchPoints();
        assertEquals(CAP, points.size(), "sequential fetches export exactly the configured number of series, overflow included");

        Map<String, Long> detailed = new TreeMap<>();
        points.stream().filter(p -> p.attributes().get("apicurio.registry.client_id") != null)
                .forEach(p -> detailed.put(p.attributes().get("apicurio.registry.client_id"), p.value()));
        assertEquals(Map.of("c1", 2L, "c2", 1L, "c3", 1L), detailed, "the first series seen keep their detail");

        List<OtlpMetricsTestReceiver.Point> overflow = points.stream()
                .filter(p -> p.attributes().get("apicurio.registry.client_id") == null).collect(Collectors.toList());
        assertEquals(1, overflow.size());
        assertEquals(Map.of("otel.metric.overflow", "true"), overflow.get(0).attributes(),
                "the overflow series has no client or identifier labels");
        assertEquals(5L, overflow.get(0).value(), "c4 to c8 were folded into the overflow series");
    }
}
