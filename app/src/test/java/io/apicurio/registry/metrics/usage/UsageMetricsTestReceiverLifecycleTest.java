package io.apicurio.registry.metrics.usage;

import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.resource.v1.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The test receiver is a resource that the usage metrics integration tests depend on, so its own lifecycle is
 * tested: nothing of it may outlive {@code stop()}, a mode chosen at start is in effect for the first request,
 * and a stopped receiver really refuses connections and comes back on its port.
 */
class UsageMetricsTestReceiverLifecycleTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void closeClient() {
        http.close();
    }

    private HttpRequest post(OtlpMetricsTestReceiver receiver, byte[] body) {
        return HttpRequest.newBuilder(URI.create(receiver.endpoint() + "/v1/metrics")).timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }

    private int status(OtlpMetricsTestReceiver receiver, byte[] body) throws Exception {
        return http.send(post(receiver, body), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static long liveThreads(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(t -> t.isAlive() && t.getName().startsWith(prefix)).count();
    }

    @Test
    void stopReleasesAStalledRequestAndLeavesNoReceiverThread() {
        OtlpMetricsTestReceiver receiver = OtlpMetricsTestReceiver.startNew(OtlpMetricsTestReceiver.Mode.STALL);
        try {
            CompletableFuture<HttpResponse<Void>> stalled = http.sendAsync(post(receiver, new byte[0]),
                    HttpResponse.BodyHandlers.discarding());
            await().atMost(5, TimeUnit.SECONDS).until(() -> receiver.stalledRequests() == 1);

            receiver.stop();

            assertFalse(receiver.isRunning());
            await().atMost(5, TimeUnit.SECONDS).until(stalled::isDone);
            assertEquals(0, receiver.stalledRequests());
            await().atMost(5, TimeUnit.SECONDS).until(() -> liveThreads(receiver.threadPrefix()) == 0);
        } finally {
            receiver.stop();
        }
    }

    @Test
    void theModeChosenAtStartAnswersTheFirstRequest() throws Exception {
        OtlpMetricsTestReceiver receiver = OtlpMetricsTestReceiver.startNew(OtlpMetricsTestReceiver.Mode.UNAVAILABLE);
        try {
            assertEquals(503, status(receiver, exportOf("m", 1).toByteArray()));
            assertEquals(0, receiver.acceptedExports());
            assertEquals(1, receiver.requestCount());
        } finally {
            receiver.stop();
        }
    }

    @Test
    void aStoppedReceiverRefusesConnectionsAndRestartsOnTheSamePortKeepingWhatItReceived() throws Exception {
        OtlpMetricsTestReceiver receiver = OtlpMetricsTestReceiver.startNew(OtlpMetricsTestReceiver.Mode.OK);
        try {
            int port = receiver.port();
            assertEquals(200, status(receiver, exportOf("m", 7).toByteArray()));

            receiver.stop();
            assertThrows(ConnectException.class, () -> status(receiver, exportOf("m", 8).toByteArray()));

            receiver.start(OtlpMetricsTestReceiver.Mode.OK);
            assertEquals(port, receiver.port());
            assertEquals(200, status(receiver, exportOf("m", 9).toByteArray()));
            assertEquals(2, receiver.acceptedExports());
            assertEquals(9L, receiver.latestPoints("m").get(0).value());
        } finally {
            receiver.stop();
        }
    }

    @Test
    void anExportIsDecodedWithTheOfficialMessages() throws Exception {
        OtlpMetricsTestReceiver receiver = OtlpMetricsTestReceiver.startNew(OtlpMetricsTestReceiver.Mode.OK);
        try {
            assertEquals(200, status(receiver, exportOf("m", 7).toByteArray()));

            assertEquals(List.of(new OtlpMetricsTestReceiver.Point("m", Map.of("service.instance.id", "abc"),
                    Map.of("k", "v"), 7L)), receiver.latestPoints("m"));
            assertEquals(List.of("m"), receiver.metricNames());
        } finally {
            receiver.stop();
        }
    }

    private static ExportMetricsServiceRequest exportOf(String metric, long value) {
        KeyValue instance = KeyValue.newBuilder().setKey("service.instance.id")
                .setValue(AnyValue.newBuilder().setStringValue("abc")).build();
        KeyValue attribute = KeyValue.newBuilder().setKey("k").setValue(AnyValue.newBuilder().setStringValue("v"))
                .build();
        NumberDataPoint point = NumberDataPoint.newBuilder().setAsInt(value).addAttributes(attribute).build();
        return ExportMetricsServiceRequest.newBuilder()
                .addResourceMetrics(ResourceMetrics.newBuilder()
                        .setResource(Resource.newBuilder().addAttributes(instance))
                        .addScopeMetrics(ScopeMetrics.newBuilder().addMetrics(Metric.newBuilder().setName(metric)
                                .setSum(Sum.newBuilder().addDataPoints(point)))))
                .build();
    }
}
