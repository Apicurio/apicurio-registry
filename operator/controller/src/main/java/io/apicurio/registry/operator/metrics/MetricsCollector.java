package io.apicurio.registry.operator.metrics;

import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.TLSSpec;
import io.apicurio.registry.operator.metrics.RegistryMetricsSnapshot.RequestCounters;
import io.apicurio.registry.operator.resource.Labels;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.metrics.RegistryMetricNames.KAFKA_RECORDS_LAG_MAX;
import static io.apicurio.registry.operator.metrics.RegistryMetricNames.POOL_ACTIVE_COUNT;
import static io.apicurio.registry.operator.metrics.RegistryMetricNames.POOL_AVAILABLE_COUNT;
import static io.apicurio.registry.operator.metrics.RegistryMetricNames.REST_REQUESTS_COUNT;
import static io.apicurio.registry.operator.metrics.RegistryMetricNames.STORAGE_ARTIFACTS;
import static io.apicurio.registry.operator.metrics.RegistryMetricNames.STORAGE_ARTIFACT_VERSIONS;
import static io.apicurio.registry.operator.metrics.RegistryMetricNames.TAG_STATUS_CODE_GROUP;
import static io.apicurio.registry.operator.metrics.RegistryMetricNames.isServerError;
import static io.apicurio.registry.operator.resource.ResourceFactory.COMPONENT_APP;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;

/**
 * Reads the Prometheus endpoint of the Apicurio Registry application Pods.
 * <p>
 * Pods are contacted directly on their Pod IP rather than through the Service, because the Service only
 * publishes the API ports. The management interface, which is where Quarkus serves the Prometheus endpoint,
 * is not part of it.
 * <p>
 * Only plain HTTP is supported. An instance with TLS configured serves its management interface over HTTPS,
 * and is skipped with an explanatory MetricsUnavailable condition rather than being contacted in vain.
 */
public class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);

    /**
     * Quarkus management interface port, where the Prometheus endpoint is served.
     */
    static final int MANAGEMENT_PORT = 9000;

    /**
     * Path of the Prometheus endpoint, relative to the management interface root.
     */
    static final String METRICS_PATH = "/metrics";

    /**
     * Upper bound on how many Pods are contacted per collection, so that a large deployment cannot turn a
     * single reconciliation into an unbounded number of HTTP requests.
     */
    static final int MAX_SCRAPED_PODS = 10;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Shared across all custom resources. An {@link HttpClient} carries its own selector thread and executor,
     * so one per watched instance would be wasteful.
     */
    private static final HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private final HttpClient httpClient;

    public MetricsCollector() {
        this(SHARED_CLIENT);
    }

    MetricsCollector(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public RegistryMetricsSnapshot collect(KubernetesClient client, ApicurioRegistry3 primary)
            throws MetricsCollectionException {

        if (servesManagementOverTls(primary)) {
            // Attempting the scrape anyway would open a doomed connection to every Pod on every interval and
            // then report a bare connection error, which says nothing about the actual cause.
            throw new MetricsCollectionException(
                    "The management interface is served over TLS because a keystore is configured in "
                            + "spec.app.tls, and metrics collection only supports plain HTTP.");
        }

        var pods = findApplicationPods(client, primary);
        if (pods.isEmpty()) {
            throw new MetricsCollectionException("No running Apicurio Registry application Pod was found.");
        }

        // Keyed by Pod, so that two collections can be compared by which Pods they actually covered.
        var bodies = new LinkedHashMap<String, String>();
        String lastFailure = null;
        for (var pod : pods) {
            var name = pod.getMetadata().getName();
            try {
                bodies.put(name, scrape(pod.getStatus().getPodIP()));
            } catch (Exception ex) {
                lastFailure = name + ": " + describe(ex);
                log.debug("Could not scrape metrics from Pod {}", name, ex);
            }
        }
        if (bodies.isEmpty()) {
            throw new MetricsCollectionException(
                    "Could not read metrics from any of the %d application Pod(s). Last failure was %s"
                            .formatted(pods.size(), lastFailure));
        }
        return aggregate(bodies);
    }

    /**
     * Whether the operand serves its management interface over TLS.
     * <p>
     * Configuring a keystore switches the whole Quarkus TLS registry over, and the management interface
     * follows the main HTTP server. Setting {@code spec.app.tls.insecureRequests} does not hold it back: an
     * instance configured that way was observed listening on {@code http://0.0.0.0:8080},
     * {@code https://0.0.0.0:8443} and {@code https://0.0.0.0:9000} at the same time.
     */
    static boolean servesManagementOverTls(ApicurioRegistry3 primary) {
        return ofNullable(primary)
                .map(ApicurioRegistry3::getSpec)
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getTls)
                .map(TLSSpec::getKeystoreSecretRef)
                .isPresent();
    }

    private List<Pod> findApplicationPods(KubernetesClient client, ApicurioRegistry3 primary) {
        var all = client.pods()
                .inNamespace(primary.getMetadata().getNamespace())
                .withLabels(Labels.getSelectorLabels(primary, COMPONENT_APP))
                .list()
                .getItems();

        var running = all.stream()
                .filter(pod -> pod.getStatus() != null
                        && "Running".equals(pod.getStatus().getPhase())
                        && !isBlank(pod.getStatus().getPodIP()))
                // Sorted so that a deployment larger than the cap is truncated to the same set of Pods on
                // every collection. An unstable set would make consecutive readings incomparable.
                .sorted(comparing(pod -> pod.getMetadata().getName()))
                .limit(MAX_SCRAPED_PODS)
                .toList();

        if (all.size() > running.size()) {
            log.debug("Collecting metrics from {} of {} application Pod(s).", running.size(), all.size());
        }
        return running;
    }

    private String scrape(String podIP) throws Exception {
        // A literal IPv6 address has to be bracketed before it can go into a URI.
        var host = podIP.contains(":") ? "[" + podIP + "]" : podIP;
        var uri = URI.create("http://%s:%d%s".formatted(host, MANAGEMENT_PORT, METRICS_PATH));
        var request = HttpRequest.newBuilder(uri).GET().timeout(REQUEST_TIMEOUT).build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new MetricsCollectionException("unexpected status code " + response.statusCode());
        }
        return response.body();
    }

    /**
     * @param bodiesByPod the Prometheus endpoint response of every Pod that answered, keyed by Pod name
     */
    static RegistryMetricsSnapshot aggregate(Map<String, String> bodiesByPod) {
        var requestCounters = new LinkedHashMap<String, RequestCounters>();
        Double poolUtilization = null;
        Long artifactCount = null;
        Long artifactVersionCount = null;
        Long kafkaConsumerLag = null;

        for (var pod : bodiesByPod.entrySet()) {
            var samples = PrometheusTextParser.parse(pod.getValue());
            var podRequests = 0.0;
            var podServerErrors = 0.0;
            var podRequestSeen = false;
            var podActive = 0.0;
            var podAvailable = 0.0;
            var podPoolSeen = false;

            for (var sample : samples) {
                if (!isFinite(sample.value())) {
                    continue;
                }
                switch (sample.name()) {
                    case REST_REQUESTS_COUNT -> {
                        podRequestSeen = true;
                        podRequests += sample.value();
                        if (isServerError(sample.label(TAG_STATUS_CODE_GROUP))) {
                            podServerErrors += sample.value();
                        }
                    }
                    case POOL_ACTIVE_COUNT -> {
                        podActive += sample.value();
                        podPoolSeen = true;
                    }
                    case POOL_AVAILABLE_COUNT -> {
                        podAvailable += sample.value();
                        podPoolSeen = true;
                    }
                    // Every replica counts the same shared storage, so these are maxed rather than summed.
                    // Summing would multiply the real figure by the replica count.
                    case STORAGE_ARTIFACTS -> artifactCount = highest(artifactCount, sample.value());
                    case STORAGE_ARTIFACT_VERSIONS ->
                            artifactVersionCount = highest(artifactVersionCount, sample.value());
                    // Each replica runs its own consumer over its own assigned partitions, so this reports
                    // the replica that is furthest behind rather than a total.
                    case KAFKA_RECORDS_LAG_MAX ->
                            kafkaConsumerLag = highest(kafkaConsumerLag, sample.value());
                    default -> {
                        // Not a metric the operator reports.
                    }
                }
            }

            if (podRequestSeen) {
                requestCounters.put(pod.getKey(), new RequestCounters(podRequests, podServerErrors));
            }
            if (podPoolSeen) {
                var poolSize = podActive + podAvailable;
                // A pool that has not opened a connection yet is idle, not saturated.
                var utilization = poolSize > 0 ? podActive / poolSize : 0.0;
                // Pool exhaustion is a per-Pod condition: a replica whose pool is full is already queueing
                // or failing requests. Averaging across replicas would let that replica hide behind idle
                // ones, which is the case the threshold exists to catch. This also agrees with the
                // generated PrometheusRule, which evaluates the same ratio per series.
                poolUtilization = poolUtilization == null ? utilization
                        : Math.max(poolUtilization, utilization);
            }
        }

        return new RegistryMetricsSnapshot(
                Instant.now(),
                bodiesByPod.size(),
                requestCounters,
                poolUtilization,
                artifactCount,
                artifactVersionCount,
                kafkaConsumerLag);
    }


    /**
     * Keeps the larger of the two, treating a negative sample as zero.
     */
    private static Long highest(Long current, double value) {
        var candidate = (long) Math.max(0, value);
        return current == null ? candidate : Math.max(current, candidate);
    }
    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String describe(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
