package io.apicurio.registry.metrics.usage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * A local OTLP/HTTP metrics receiver owned by the test. It decodes every request with the official OTLP
 * message classes and can be told to fail or stall, to simulate a collector outage. It listens on the
 * loopback interface only.
 * <p>
 * The receiver owns its HTTP server and its executor. {@link #stop()} releases stalled handlers, shuts the
 * executor down and waits for it, so no receiver thread outlives it. {@link #start(Mode)} brings it back on
 * the same port with the given mode already in effect before the first connection is accepted.
 */
final class OtlpMetricsTestReceiver implements AutoCloseable {

    enum Mode {
        /** Accept the export. */
        OK,
        /** Answer HTTP 503, the way an overloaded collector does. */
        UNAVAILABLE,
        /** Accept the request and do not answer until the mode changes or the receiver stops. */
        STALL
    }

    /** One data point of a sum metric, with the resource attributes of the export it arrived in. */
    record Point(String metric, Map<String, String> resource, Map<String, String> attributes, long value) {
    }

    private static final String HOST = "127.0.0.1";
    private static final AtomicInteger INSTANCES = new AtomicInteger();
    private static final long STALL_LIMIT_SECONDS = 30;
    private static final long TERMINATION_SECONDS = 5;
    private static final long REBIND_SECONDS = 5;

    private final String threadPrefix = "otlp-test-receiver-" + INSTANCES.incrementAndGet();
    private final List<List<Point>> requests = new CopyOnWriteArrayList<>();
    private final List<String> metricNames = new CopyOnWriteArrayList<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private final AtomicInteger unavailableResponses = new AtomicInteger();
    private final AtomicInteger stalledRequests = new AtomicInteger();
    private final int port;
    private HttpServer server;
    private ExecutorService executor;
    private volatile ResponseState responseState;

    private record ResponseState(Mode mode, CountDownLatch release) {
        private ResponseState(Mode mode) {
            this(mode, new CountDownLatch(1));
        }
    }

    /** Starts a receiver on a port the operating system picks, with {@code initial} already in effect. */
    static OtlpMetricsTestReceiver startNew(Mode initial) {
        return new OtlpMetricsTestReceiver(initial);
    }

    private OtlpMetricsTestReceiver(Mode initial) {
        startServer(0, initial);
        port = server.getAddress().getPort();
    }

    int port() {
        return port;
    }

    String endpoint() {
        return "http://" + HOST + ":" + port;
    }

    /** Whether the receiver is listening. */
    synchronized boolean isRunning() {
        return server != null;
    }

    /**
     * Listens again on the port this receiver had, after {@link #stop()}. The mode is set before the socket
     * accepts anything. The port is free again only once the old socket is gone, so binding is retried for a
     * bounded time.
     */
    synchronized void start(Mode initial) {
        if (server != null) {
            throw new IllegalStateException("The receiver is already running");
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(REBIND_SECONDS);
        while (true) {
            try {
                startServer(port, initial);
                return;
            } catch (IllegalStateException e) {
                if (!(e.getCause() instanceof BindException) || System.nanoTime() > deadline) {
                    throw e;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private synchronized void startServer(int requestedPort, Mode initial) {
        responseState = new ResponseState(initial);
        AtomicInteger threads = new AtomicInteger();
        ExecutorService newExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, threadPrefix + "-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        HttpServer newServer;
        try {
            newServer = HttpServer.create(new InetSocketAddress(HOST, requestedPort), 0);
        } catch (IOException e) {
            newExecutor.shutdownNow();
            throw new IllegalStateException("Cannot start the test OTLP receiver", e);
        }
        newServer.createContext("/", this::handle);
        newServer.setExecutor(newExecutor);
        executor = newExecutor;
        server = newServer;
        newServer.start();
    }

    /** Changes how requests are answered from now on. Leaving {@link Mode#STALL} releases stalled requests. */
    synchronized void mode(Mode newMode) {
        ResponseState previous = responseState;
        if (previous.mode() != newMode) {
            responseState = new ResponseState(newMode);
            previous.release().countDown();
        }
    }

    /** Number of HTTP requests that reached the receiver, whatever the mode answered. */
    int requestCount() {
        return requestCount.get();
    }

    /** Number of HTTP 503 responses the receiver sent. */
    int unavailableResponses() {
        return unavailableResponses.get();
    }

    /** Number of requests that are being held right now by {@link Mode#STALL}. */
    int stalledRequests() {
        return stalledRequests.get();
    }

    /** Number of exports that were accepted and decoded. */
    int acceptedExports() {
        return requests.size();
    }

    /** The metric names of every export that was accepted. */
    List<String> metricNames() {
        return new ArrayList<>(metricNames);
    }

    /** The data points of the most recent accepted export that carried the named metric. */
    List<Point> latestPoints(String metricName) {
        for (int i = requests.size() - 1; i >= 0; i--) {
            List<Point> points = pointsOf(requests.get(i), metricName);
            if (!points.isEmpty()) {
                return points;
            }
        }
        return List.of();
    }

    private static List<Point> pointsOf(List<Point> request, String metricName) {
        List<Point> result = new ArrayList<>();
        for (Point point : request) {
            if (metricName.equals(point.metric())) {
                result.add(point);
            }
        }
        return result;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())
                    || !exchange.getRequestURI().getPath().endsWith("/v1/metrics")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            requestCount.incrementAndGet();
            byte[] body = readBody(exchange);
            ResponseState current = responseState;
            if (current.mode() == Mode.STALL) {
                stalledRequests.incrementAndGet();
                try {
                    current.release().await(STALL_LIMIT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    stalledRequests.decrementAndGet();
                }
                return;
            }
            if (current.mode() == Mode.UNAVAILABLE) {
                exchange.sendResponseHeaders(503, -1);
                unavailableResponses.incrementAndGet();
                return;
            }
            record(ExportMetricsServiceRequest.parseFrom(body));
            exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().close();
        }
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        if ("gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
            in = new GZIPInputStream(in);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    private void record(ExportMetricsServiceRequest request) {
        List<Point> points = new ArrayList<>();
        for (ResourceMetrics resourceMetrics : request.getResourceMetricsList()) {
            Map<String, String> resource = attributes(resourceMetrics.getResource().getAttributesList());
            for (ScopeMetrics scope : resourceMetrics.getScopeMetricsList()) {
                for (Metric metric : scope.getMetricsList()) {
                    metricNames.add(metric.getName());
                    if (!metric.hasSum()) {
                        continue;
                    }
                    for (NumberDataPoint point : metric.getSum().getDataPointsList()) {
                        points.add(new Point(metric.getName(), resource, attributes(point.getAttributesList()),
                                point.getAsInt()));
                    }
                }
            }
        }
        requests.add(points);
    }

    private static Map<String, String> attributes(List<KeyValue> keyValues) {
        Map<String, String> result = new TreeMap<>();
        for (KeyValue keyValue : keyValues) {
            AnyValue value = keyValue.getValue();
            String text = switch (value.getValueCase()) {
                case STRING_VALUE -> value.getStringValue();
                case BOOL_VALUE -> String.valueOf(value.getBoolValue());
                case INT_VALUE -> String.valueOf(value.getIntValue());
                default -> value.toString();
            };
            result.put(keyValue.getKey(), text);
        }
        return result;
    }

    /**
     * Stops listening, releases stalled handlers and waits, within a bound, until the executor has finished.
     * The decoded exports stay available.
     */
    synchronized void stop() {
        if (server == null) {
            return;
        }
        responseState.release().countDown();
        server.stop(0);
        executor.shutdownNow();
        boolean terminated;
        try {
            terminated = executor.awaitTermination(TERMINATION_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminated = false;
        } finally {
            server = null;
            executor = null;
        }
        if (!terminated) {
            throw new IllegalStateException("The receiver executor did not terminate within "
                    + TERMINATION_SECONDS + " seconds");
        }
    }

    @Override
    public void close() {
        stop();
    }

    /** Name prefix of the threads this receiver starts, for checks that none of them outlives it. */
    String threadPrefix() {
        return threadPrefix;
    }
}
