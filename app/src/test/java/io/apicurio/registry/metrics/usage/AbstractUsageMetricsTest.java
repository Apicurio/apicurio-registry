package io.apicurio.registry.metrics.usage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.AfterAll;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base of the usage metrics integration tests. Every test class runs Quarkus with its own profile and
 * exports to a receiver the test owns, so what is asserted is what a real collector would be sent.
 */
abstract class AbstractUsageMetricsTest extends AbstractResourceTestBase {

    static final String METRIC = "apicurio.usage.schema.fetches";
    static final String GLOBAL_ROUTE = "/apis/registry/v3/ids/globalIds/";
    static final String CONTENT_ROUTE = "/apis/registry/v3/ids/contentIds/";
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** The identifiers of one registered schema version. */
    record Schema(long globalId, long contentId) {
    }

    @InjectOtlpReceiver
    protected OtlpMetricsTestReceiver receiver;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
    private final AtomicInteger counter = new AtomicInteger();

    @AfterAll
    void closeHttpClient() {
        http.close();
    }

    /** A client identifier that no other test of the class has used. */
    protected String newClient(String prefix) {
        return prefix + "-" + counter.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected Schema createSchema() throws Exception {
        return createSchema("usage-metrics");
    }

    protected Schema createSchema(String group) throws Exception {
        String content = "{\"type\":\"object\",\"title\":\"" + UUID.randomUUID() + "\"}";
        CreateArtifactResponse response = createArtifact(group, generateArtifactId(), ArtifactType.JSON,
                content, ContentTypes.APPLICATION_JSON);
        return new Schema(response.getVersion().getGlobalId(), response.getVersion().getContentId());
    }

    /** Sends a GET with the usage headers (a null header is not sent) and returns the HTTP status. */
    protected int get(String path, String clientId, String operation) throws IOException, InterruptedException {
        return send("GET", path, clientId, operation);
    }

    protected int send(String method, String path, String clientId, String operation)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + testPort + path))
                .timeout(REQUEST_TIMEOUT)
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (clientId != null) {
            request.header("X-Registry-Client-Id", clientId);
        }
        if (operation != null) {
            request.header("X-Registry-Operation", operation);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /** The exported series of one client as {@code identifierType:identifierValue:operation -> count}. */
    protected Map<String, Long> seriesOf(String client) {
        return receiver.latestPoints(METRIC).stream()
                .filter(p -> client.equals(p.attributes().get("apicurio.registry.client_id")))
                .collect(Collectors.toMap(AbstractUsageMetricsTest::key, OtlpMetricsTestReceiver.Point::value,
                        Long::sum, TreeMap::new));
    }

    protected static String key(OtlpMetricsTestReceiver.Point point) {
        return point.attributes().get("apicurio.registry.identifier_type") + ":"
                + point.attributes().get("apicurio.registry.identifier_value") + ":"
                + point.attributes().get("apicurio.registry.operation");
    }

    protected void awaitSeries(String client, Map<String, Long> expected) {
        await().atMost(30, TimeUnit.SECONDS).pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertEquals(expected, seriesOf(client)));
    }

    /** Waits until the receiver has accepted at least {@code exports} more exports than it had before. */
    protected void awaitExports(int exports) {
        int before = receiver.acceptedExports();
        await().atMost(30, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> receiver.acceptedExports() >= before + exports);
    }

    protected List<OtlpMetricsTestReceiver.Point> allSchemaFetchPoints() {
        return receiver.latestPoints(METRIC);
    }
}
