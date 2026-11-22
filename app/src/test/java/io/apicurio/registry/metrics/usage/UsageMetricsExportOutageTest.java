package io.apicurio.registry.metrics.usage;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Schema fetches keep succeeding, within a bound, while the collector is unreachable, answers 503 or holds
 * the export request open. Every phase starts the fetches first and only then counts the outage as in effect,
 * when the test has observed an export attempt failing: a logged failure for the unreachable collector, a
 * request that the receiver answered with 503, or a handler that the receiver has not released. The last
 * observation describes the receiver only: it does not tell us whether the exporter already timed out.
 * <p>
 * The cumulative count that arrives once the collector is back is the exact number of fetches made, which
 * this test shows for one outage of each kind; it is not a guarantee that telemetry is never lost (an outage
 * that ends with the Registry process loses the counts of that process).
 */
@QuarkusTest
@TestProfile(UsageMetricsExportOutageTest.Profile.class)
class UsageMetricsExportOutageTest extends AbstractUsageMetricsTest {

    private static final Duration FETCH_BOUND = Duration.ofSeconds(3);
    private static final Duration PHASE_DEADLINE = Duration.ofSeconds(30);
    private static final int FETCHES_ONCE_THE_OUTAGE_IS_OBSERVED = 5;

    public static class Profile extends UsageMetricsTestProfile {
        @Override
        protected Map<String, String> scenario() {
            return Map.of("apicurio.usage.metrics.enabled", "true",
                    "quarkus.otel.exporter.otlp.timeout", "1s",
                    "quarkus.otel.metric.export.interval", "500ms");
        }
    }

    /**
     * Counts the log records in which the SDK reports that a metrics export failed. It is attached to the root
     * logger and matches on the message: the SDK's throttling logger forwards its records without the logger
     * name that the console shows, so a handler on that name never sees them.
     */
    private static final class ExportFailureCounter extends Handler {
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public void publish(LogRecord record) {
            String message = record.getMessage();
            if (message != null && message.contains("Failed to export MetricsRequestMarshaler")) {
                failures.incrementAndGet();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private Schema schema;
    private String client;
    private int fetches;

    private void boundedFetch() throws Exception {
        long start = System.nanoTime();
        assertEquals(200, get(GLOBAL_ROUTE + schema.globalId(), client, "SERIALIZE"));
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        assertTrue(took.compareTo(FETCH_BOUND) < 0,
                "a schema fetch took " + took + " during a collector outage");
        fetches++;
    }

    /**
     * Fetches, paced, until the outage has been observed through {@code event}, and then keeps fetching for
     * a few more requests. The wait for the event has a deadline, so a regression fails instead of hanging.
     */
    private void fetchThroughTheOutage(String outage, BooleanSupplier event) throws Exception {
        long deadline = System.nanoTime() + PHASE_DEADLINE.toNanos();
        while (!event.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Did not observe " + outage + " within " + PHASE_DEADLINE);
            }
            boundedFetch();
            Thread.sleep(100);
        }
        for (int i = 0; i < FETCHES_ONCE_THE_OUTAGE_IS_OBSERVED; i++) {
            boundedFetch();
            Thread.sleep(100);
        }
    }

    @Test
    void fetchesSucceedDuringEveryKindOfCollectorOutageAndTheTotalArrivesAfterwards() throws Exception {
        schema = createSchema();
        client = newClient("outage");

        // Phase 1: nothing listens on the endpoint any more, so every export attempt is refused.
        ExportFailureCounter failureCounter = new ExportFailureCounter();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(failureCounter);
        try {
            receiver.stop();
            fetchThroughTheOutage("a refused export", () -> failureCounter.failures.get() > 0);
        } finally {
            rootLogger.removeHandler(failureCounter);
        }

        // Phase 2: a collector that answers 503 from the first request it accepts.
        int responsesBefore503 = receiver.unavailableResponses();
        int acceptedBefore503 = receiver.acceptedExports();
        receiver.start(OtlpMetricsTestReceiver.Mode.UNAVAILABLE);
        fetchThroughTheOutage("two exports answered with 503",
                () -> receiver.unavailableResponses() >= responsesBefore503 + 2);
        assertEquals(acceptedBefore503, receiver.acceptedExports(), "a 503 is not an accepted export");

        // Phase 3: a collector that holds the export request without answering.
        receiver.mode(OtlpMetricsTestReceiver.Mode.STALL);
        fetchThroughTheOutage("an export held by the receiver", () -> receiver.stalledRequests() > 0);
        assertTrue(receiver.stalledRequests() > 0, "the export request was still held while the last fetches ran");
        assertEquals(acceptedBefore503, receiver.acceptedExports(), "a held export is not an accepted export");

        // Phase 4: the collector recovers and receives the cumulative total of the whole outage.
        receiver.mode(OtlpMetricsTestReceiver.Mode.OK);
        long expected = fetches;
        await().atMost(30, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(Map.of("global_id:" + schema.globalId() + ":SERIALIZE", expected),
                        seriesOf(client)));
    }
}
